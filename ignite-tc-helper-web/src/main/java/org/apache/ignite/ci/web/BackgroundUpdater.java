package org.apache.ignite.ci.web;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import jersey.repackaged.com.google.common.base.Throwables;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.analysis.Expirable;
import org.apache.ignite.internal.util.typedef.T2;
import org.apache.ignite.lang.IgniteClosure;

/**
 * Component for storing catchable results into ignite and get updates
 */
public class BackgroundUpdater {

    private Ignite ignite;
    private Map<T2<String, ?>, Future<?>> scheduledUpdates = new ConcurrentHashMap<>();
    private ExecutorService service = Executors.newFixedThreadPool(10, r -> {
        Thread thread =  Executors.defaultThreadFactory().newThread(r);

        thread.setName("bgupd-" + thread.getName());

        return thread;
    });

    public BackgroundUpdater(Ignite ignite) {
        this.ignite = ignite;
    }

    public <K, V extends IBackgroundUpdatable> V get(String cacheName, K key, IgniteClosure<K, V> load) {
        final IgniteCache<K, Expirable<V>> currCache = ignite.getOrCreateCache(cacheName);

        //Lazy calculation of required value
        final Callable<V> loadAndSaveCallable = () -> {
            System.err.println("Running background upload for [" + cacheName + "] for key [" + key + "]");
            V val = null;  //todo how to handle non first load error
            try {
                val = load.apply(key);
            }
            catch (Exception e) {
                System.err.println("Failed to complete background upload for [" + cacheName + "] " +
                    "for key [" + key + "]");

                e.printStackTrace();
                throw e;
            }
            currCache.put(key, new Expirable<V>(val));
            System.err.println("Successfully completed background upload for [" + cacheName + "] for key [" + key + "]");
            return val;
        };

        final T2<String, ?> computationKey = new T2<String, Object>(cacheName, key);

        //check for computation cleanup required
        final Future<?> oldFut = scheduledUpdates.get(computationKey);
        if (oldFut != null && (oldFut.isCancelled() || oldFut.isDone()))
            scheduledUpdates.remove(computationKey, oldFut);

        final Expirable<V> expirable = currCache.get(key);

        if (expirable == null || isExpired(expirable)) {
            Function<T2<String, ?>, Future<?>> startingFunction = (k) -> service.submit(loadAndSaveCallable);
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
                    throw Throwables.propagate(e);
                }
                finally {
                    scheduledUpdates.remove(computationKey, fut); // removing registered computation
                }
            }
        }

        final V data = expirable.getData();
        data.setUpdateRequired(isExpired(expirable)); //considered actual
        return data;
    }

    private <V extends IBackgroundUpdatable> boolean isExpired(Expirable<V> expirable) {
        return expirable.getAgeMs() > TimeUnit.MINUTES.toMillis(1);
    }

    private <V extends IBackgroundUpdatable> boolean isTooOld(Expirable<V> expirable) {
        return expirable.getAgeMs() > TimeUnit.HOURS.toMillis(1);
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
