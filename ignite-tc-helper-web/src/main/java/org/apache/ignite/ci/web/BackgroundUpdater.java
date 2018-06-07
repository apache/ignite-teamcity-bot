package org.apache.ignite.ci.web;

import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.Expirable;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.internal.util.typedef.T2;
import org.apache.ignite.lang.IgniteClosure;
import org.jetbrains.annotations.Nullable;

/**
 * Component for storing catchable results into ignite and get updates
 */
public class BackgroundUpdater {
    /** Expire milliseconds, provide cached result with flag to update */
    private static final long EXPIRE_MS = TimeUnit.MINUTES.toMillis(1);

    /** Outdated milliseconds, don't provide cached result after. */
    private static final long OUTDATED_MS = TimeUnit.HOURS.toMillis(1);

    private Map<T2<String, ?>, Future<?>> scheduledUpdates = new ConcurrentHashMap<>();
    ThreadFactory threadFactory = Executors.defaultThreadFactory();

    private final Cache<T2<String, ?>, Expirable<?>> data
        = CacheBuilder.<T2<String, ?>, Expirable<?>>newBuilder()
        .maximumSize(100)
        .expireAfterAccess(15, TimeUnit.MINUTES)
        .softValues()
        .build();

    private ExecutorService service = Executors.newFixedThreadPool(5, r -> {
        Thread thread = threadFactory.newThread(r);

        thread.setName("bgupd-" + thread.getName());

        return thread;
    });

    public BackgroundUpdater() {
    }

    public <K, V extends IBackgroundUpdatable> V get(String cacheName, K key, IgniteClosure<K, V> load) {
        return get(cacheName, key, load, false);
    }

    @Nullable
    public <K, V extends IBackgroundUpdatable> V get(String cacheName, K key, IgniteClosure<K, V> load,
        boolean triggerSensitive) {

        final T2<String, ?> computationKey = new T2<String, Object>(cacheName, key);

        //Lazy calculation of required value
        final Callable<V> loadAndSaveCall = () -> {
            Stopwatch started = Stopwatch.createStarted();
            System.err.println("Running background upload for [" + cacheName + "] for key [" + key + "]");
            V val = null;  //todo how to handle non first load error
            try {
                val = load.apply(key);
            }
            catch (Exception e) {
                System.err.println("Failed to complete background upload for [" + cacheName + "] " +
                    "for key [" + key + "], required " + started.elapsed(TimeUnit.MILLISECONDS) + " ms");

                e.printStackTrace();
                throw e;
            }

            data.put(computationKey, new Expirable<V>(val));
            System.err.println("Successfully completed background upload for [" + cacheName + "] " +
                "for key [" + key + "], required " + started.elapsed(TimeUnit.MILLISECONDS) + " ms");

            return val;
        };

        //check for computation cleanup required
        final Future<?> oldFut = scheduledUpdates.get(computationKey);
        if (oldFut != null && (oldFut.isCancelled() || oldFut.isDone()))
            scheduledUpdates.remove(computationKey, oldFut);

        Expirable<V> expirable = null;
        try {
            /*
            //todo migrate to guava's get
            expirable = (Expirable<V>)data.get(computationKey,
                ()->{
                    return new Expirable<Object>(loadAndSaveCall.call());
                });
                */

            expirable = (Expirable<V>)data.getIfPresent(computationKey);
        }
        catch (Exception e) {
            e.printStackTrace(); //some persistence problem
        }

        if (expirable == null || isExpired(expirable, triggerSensitive)) {
            Function<T2<String, ?>, Future<?>> startingFunction = (k) -> getService().submit(loadAndSaveCall);
            Future<?> fut = scheduledUpdates.computeIfAbsent(computationKey, startingFunction);

            if (expirable == null || isTooOld(expirable)) {
                try {
                    V o = (V)fut.get();
                    o.setUpdateRequired(false);
                    return o;
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                    return null;
                }
                catch (ExecutionException e) {
                    throw ExceptionUtil.propagateException(e);
                }
                finally {
                    scheduledUpdates.remove(computationKey, fut); // removing registered computation
                }
            }
        }

        final V data = expirable.getData();
        data.setUpdateRequired(isExpired(expirable, triggerSensitive)); //considered actual
        return data;
    }

    public ExecutorService getService() {
        return service;
    }

    private <V extends IBackgroundUpdatable> boolean isExpired(Expirable<V> expirable, boolean triggerSensitive) {

        if (triggerSensitive)
            return !expirable.isAgeLessThanSecs(IgnitePersistentTeamcity.getTriggerRelCacheValidSecs(60));

        return expirable.getAgeMs() > EXPIRE_MS;
    }

    private <V extends IBackgroundUpdatable> boolean isTooOld(Expirable<V> expirable) {
        return expirable.getAgeMs() > OUTDATED_MS;
    }

    public void stop() {
        scheduledUpdates.values().forEach(future -> future.cancel(true));
        service.shutdown();
        try {
            service.awaitTermination(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
