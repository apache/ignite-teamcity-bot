package org.apache.ignite.ci.web;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import jersey.repackaged.com.google.common.base.Throwables;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.analysis.Expirable;
import org.apache.ignite.lang.IgniteClosure;

/**
 * Component for storing catchable results into ignite and get updates
 */
public class BackgroundUpdater {

    private Ignite ignite;
    private Map<String, Future<?>> scheduledUpdates = new ConcurrentHashMap<>();
    private ExecutorService service = Executors.newFixedThreadPool(10);

    public BackgroundUpdater(Ignite ignite) {
        this.ignite = ignite;
    }

    public <K, V extends IBackgroundUpdatable> V get(String cacheName, K key, IgniteClosure<K, V> load) {
        final IgniteCache<K, Expirable<V>> currCache = ignite.getOrCreateCache(cacheName);

        Callable<V> loadAndSaveCallable = () -> {
            System.err.println("Running background upload for " + cacheName + " for key " + key);
            V apply = load.apply(key);
            currCache.put(key, new Expirable<V>(apply));

            return apply;
        };
        final Expirable<V> expirable = currCache.get(key);
        if (expirable == null) {
            final FutureTask<V> task = new FutureTask<V>(loadAndSaveCallable);
            final Future<?> future = scheduledUpdates.computeIfAbsent(cacheName, k -> {
                task.run();
                return task;
            });
            try {
                V o = (V)future.get();
                o.setUpdateRequired(false);
                return o;
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            catch (ExecutionException e) {
                throw Throwables.propagate(e);
            } finally {
                scheduledUpdates.remove(cacheName); // removing registered computation
            }
        }

        final long ts = (expirable).getAgeMs();
        if (ts <= TimeUnit.MINUTES.toMillis(1)) {
            final V data = expirable.getData();
            data.setUpdateRequired(false); //considered actual
            return data; // return and do nothing
        }
        else {
            //need update but can return current

            //todo some locking and check if it is already scheduled
            Function<String, Future<?>> startingFunction = (k) -> {
                return service.submit(loadAndSaveCallable);
            };
            Future<?> future = scheduledUpdates.computeIfAbsent(cacheName, startingFunction);
            if(future.isCancelled()) {
                scheduledUpdates.remove(cacheName); //todo bad code, two responsibilities for removal
            }
            if(future.isDone()) {
                scheduledUpdates.remove(cacheName); //todo bad code, two responsibilities for removal
            }

            final V curData = expirable.getData();
            curData.setUpdateRequired(true);
            return curData;
        }
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
