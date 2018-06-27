package org.apache.ignite.ci.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.analysis.Expirable;

/**
 * Cache reloading from real source stuff
 */
public class CacheUpdateUtil {
    /**
     * @param cache cache to persist results.
     * @param key key for update.
     * @param cachedComputations Map with currently running updates. Used for caching
     * @param realLoadFunction supplier of future for real update
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> CompletableFuture<V> loadAsyncIfAbsent(IgniteCache<K, V> cache,
        K key,
        ConcurrentMap<K, CompletableFuture<V>> cachedComputations,
        Function<K, CompletableFuture<V>> realLoadFunction) {
        @Nullable final V persistedValue = cache.get(key);

        int fields = ObjectInterner.internFields(persistedValue);

       // if(fields>0)
       //     System.out.println("Interned " + fields + " after get()");

        if (persistedValue != null)
            return CompletableFuture.completedFuture(persistedValue);

        AtomicReference<CompletableFuture<V>> submitRef = new AtomicReference<>();

        return cachedComputations.computeIfAbsent(key,
            k -> {
                CompletableFuture<V> future = realLoadFunction.apply(k)
                    .thenApplyAsync(valueLoaded -> {
                        cache.put(k, valueLoaded);

                        return valueLoaded;
                    });

                submitRef.set(future);

                return future;
            }
        ).thenApply(res -> {
            CompletableFuture<V> f = submitRef.get();

            if (f != null)
                cachedComputations.remove(key, f);

            return res;
        });
    }

    public static <K, V> CompletableFuture<V> loadAsyncIfAbsentOrExpired(IgniteCache<K, Expirable<V>> cache,
        K key,
        ConcurrentMap<K, CompletableFuture<V>> cachedComputations,
        Function<K, CompletableFuture<V>> realLoadFunction,
        int maxAgeSecs,
        boolean alwaysProvidePersisted) {
        @Nullable final Expirable<V> persistedValue = cache.get(key);

        int fields = ObjectInterner.internFields(persistedValue);

     //   if (fields > 0)
       //     System.out.println("Interned " + fields + " after get()");


        if (persistedValue != null && persistedValue.isAgeLessThanSecs(maxAgeSecs))
            return CompletableFuture.completedFuture(persistedValue.getData());

        AtomicReference<CompletableFuture<V>> submitRef = new AtomicReference<>();

        CompletableFuture<V> loadFut = cachedComputations.computeIfAbsent(key,
            k -> {
                CompletableFuture<V> future = realLoadFunction.apply(k)
                    .thenApplyAsync(valueLoaded -> {
                        cache.put(k, new Expirable<V>(valueLoaded));

                        return valueLoaded;
                    });

                submitRef.set(future);

                return future;
            }
        ).thenApply(res -> {
            CompletableFuture<V> f = submitRef.get();

            if (f != null)
                cachedComputations.remove(key, f);

            return res;
        });

        if (alwaysProvidePersisted && persistedValue != null)
            return CompletableFuture.completedFuture(persistedValue.getData());

        return loadFut;
    }
}
