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
package org.apache.ignite.ci.github.ignited;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.configuration.CacheConfiguration;
import org.jetbrains.annotations.NotNull;

class GitHubConnIgnitedImpl implements IGitHubConnIgnited {
    public static final String GIT_HUB_PR = "gitHubPr";
    private String srvId;
    private IGitHubConnection conn;

    @Inject Provider<Ignite> igniteProvider;
    @Inject IScheduler scheduler;

    @Deprecated
    private final Cache<String, List<PullRequest>> prListCache
        = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .softValues()
        .build();

    /** Server ID mask for cache Entries. */
    private long srvIdMaskHigh;

    private IgniteCache<Long, PullRequest> prCache;

    public void init(String srvId, IGitHubConnection conn) {
        this.srvId = srvId;
        this.conn = conn;

        srvIdMaskHigh = srvId.hashCode();

        prCache = igniteProvider.get().getOrCreateCache(getCache8PartsConfig(GIT_HUB_PR));
    }

    @NotNull
    public static <K, V> CacheConfiguration<K, V> getCache8PartsConfig(String name) {
        CacheConfiguration<K, V> ccfg = new CacheConfiguration<>(name);

        ccfg.setAffinity(new RendezvousAffinityFunction(false, 8));

        return ccfg;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<PullRequest> getPullRequests() {
        scheduler.invokeLater(this::actualizePrs, 10, TimeUnit.SECONDS);

        return StreamSupport.stream(prCache.spliterator(), false)
            .filter(entry -> entry.getKey() >> 32 == srvIdMaskHigh)
            .filter(entry -> PullRequest.OPEN.equals(entry.getValue().getState()))
            .map(javax.cache.Cache.Entry::getValue)
            .collect(Collectors.toList());

        /* try {
            return prListCache.get("", conn::getPullRequests);
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }*/
    }

    private void actualizePrs() {
        runAtualizePrs(srvId);
    }

    /**
     * @param srvId Server id.
     */
    @MonitoredTask(name = "Actualize PRs", nameExtArgIndex = 0)
    @AutoProfiling
    protected String runAtualizePrs(String srvId) {
        List<PullRequest> ghData = conn.getPullRequests();

        Set<Long> ids = ghData.stream().map(PullRequest::getNumber)
            .map(this::prNumberToCacheKey)
            .collect(Collectors.toSet());

        Map<Long, PullRequest> existingEntries = prCache.getAll(ids);
        Map<Long, PullRequest> entriesToPut = new TreeMap<>();

        for (PullRequest next : ghData) {
            long cacheKey = prNumberToCacheKey(next.getNumber());
            PullRequest prPersisted = existingEntries.get(cacheKey);

            if (prPersisted == null || !prPersisted.equals(next))
                entriesToPut.put(cacheKey, next);
        }

        prCache.putAll(entriesToPut);

        return "Entries saved " + entriesToPut.size();
    }

    private long prNumberToCacheKey(int prNumber) {
        return (long)prNumber | srvIdMaskHigh << 32;
    }
}
