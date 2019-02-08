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

package org.apache.ignite.ci.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.ignite.IgniteCache;

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

}
