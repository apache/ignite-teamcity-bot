/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci.web;

import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.Expirable;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.internal.util.typedef.T2;
import org.apache.ignite.lang.IgniteClosure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for storing catchable results into guava in mem-caches and get updates
 */
public class BackgroundUpdater {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(BackgroundUpdater.class);

    /** Expire milliseconds, provide cached result with flag to update */
    private static final long EXPIRE_MS = TimeUnit.MINUTES.toMillis(1);

    private ThreadFactory threadFactory = Executors.defaultThreadFactory();

    private final Cache<T2<String, ?>, Future<?>> scheduledUpdates
        = CacheBuilder.<T2<String, ?>, Future<?>>newBuilder()
        .maximumSize(100)
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .softValues()
        .build();

    private final Cache<T2<String, ?>, Expirable<?>> dataLoaded
        = CacheBuilder.<T2<String, ?>, Expirable<?>>newBuilder()
        .maximumSize(100)
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .softValues()
        .build();

    private ExecutorService service = Executors.newFixedThreadPool(5, r -> {
        Thread thread = threadFactory.newThread(r);

        thread.setName("bgupd-" + thread.getName());

        return thread;
    });

    private ITcHelper tcHelper;

    public BackgroundUpdater(ITcHelper tcHelper) {
        this.tcHelper = tcHelper;
    }

    public <K, V extends IBackgroundUpdatable> V get(String cacheName, K key, IgniteClosure<K, V> load) {
        return get(cacheName, key, load, false);
    }


    @NotNull private String availServers(ICredentialsProv prov) {
        StringBuffer sb = new StringBuffer();
        sb.append("[");

        tcHelper.getServerIds()
            .stream()
            .filter(prov::hasAccess)
            .forEach(s -> sb.append(s).append(" "));

        sb.append("]");

        return sb.toString();
    }

    @Nullable public <K, V extends IBackgroundUpdatable> V get(String cacheName,
        ICredentialsProv prov,
        K key,
        IgniteClosure<K, V> load,
        boolean triggerSensitive) {
        String postfix = (prov == null) ? "" : "-" + availServers(prov);

        return get(cacheName + postfix, key, load, triggerSensitive);
    }

    @Nullable private <K, V extends IBackgroundUpdatable> V get(String cacheName, K key, IgniteClosure<K, V> load,
        boolean triggerSensitive) {

        final T2<String, ?> computationKey = new T2<String, Object>(cacheName, key);

        //Lazy calculation of required value
        final Callable<V> loadAndSaveCall = () -> {
            Stopwatch started = Stopwatch.createStarted();
            logger.info("Running background upload for [" + cacheName + "] for key [" + key + "]");
            V val = null;  //todo how to handle non first load error
            try {
                val = load.apply(key);
            }
            catch (Exception e) {
                logger.error("Failed to complete background upload for [" + cacheName + "] " +
                    "for key [" + key + "], required " + started.elapsed(TimeUnit.MILLISECONDS) + " ms", e);

                throw e;
            }

            logger.info("Successfully completed background upload for [" + cacheName + "] " +
                "for key [" + key + "], required " + started.elapsed(TimeUnit.MILLISECONDS) + " ms");

            return val;
        };

        //check for computation cleanup required
        final Future<?> oldFut = scheduledUpdates.getIfPresent(computationKey);

        if (oldFut != null && (oldFut.isCancelled() || oldFut.isDone()))
            scheduledUpdates.invalidate(computationKey);

        Expirable<V> expirable;

        try {
            expirable = (Expirable<V>)dataLoaded.get(computationKey,
                () -> new Expirable<V>(loadAndSaveCall.call()));
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }

        if (isRefreshRequired(expirable, triggerSensitive)) {
            Callable<?> loadModified = () -> {
                try {
                    V call = loadAndSaveCall.call();

                    Expirable<Object> expirable1 = new Expirable<>(call);

                    dataLoaded.put(computationKey, expirable1);
                }
                finally {
                    scheduledUpdates.invalidate(computationKey); //todo race here is possible if value was changed
                }

                return computationKey;
            };

            Callable<Future<?>> startingFunction = () -> service.submit(loadModified);

            try {
                scheduledUpdates.get(computationKey, startingFunction);
            }
            catch (ExecutionException e) {
                logger.error("Scheduled update for "
                                + computationKey + " indicated error, dropping this compaction", e);

                scheduledUpdates.invalidate(computationKey);
            }
        }

        final V data = expirable.getData();
        data.setUpdateRequired(isRefreshRequired(expirable, triggerSensitive)); //considered actual
        return data;
    }

    private <V extends IBackgroundUpdatable> boolean isRefreshRequired(Expirable<V> expirable, boolean triggerSensitive) {

        if (triggerSensitive)
            return !expirable.isAgeLessThanSecs(IgnitePersistentTeamcity.getTriggerRelCacheValidSecs(60));

        return expirable.getAgeMs() > EXPIRE_MS;
    }

    public void stop() {
        scheduledUpdates.asMap().values().forEach(future -> future.cancel(true));

        scheduledUpdates.cleanUp();

        dataLoaded.cleanUp();

        service.shutdown();

        try {
            service.awaitTermination(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
