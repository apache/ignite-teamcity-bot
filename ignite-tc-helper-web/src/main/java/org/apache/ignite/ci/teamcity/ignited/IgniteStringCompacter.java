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
package org.apache.ignite.ci.teamcity.ignited;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicSequence;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.configuration.CacheConfiguration;
import org.jetbrains.annotations.NotNull;

public class IgniteStringCompacter implements IStringCompacter {
    AtomicBoolean initGuard = new AtomicBoolean();
    CountDownLatch initLatch = new CountDownLatch(1);

    /** Cache name*/
    public static final String STRINGS_CACHE = "stringsCache";

    public static final String STRINGS_SEQ = "stringsSeq";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<String, Integer> stringsCache;
    private IgniteAtomicSequence sequence;

    private void initIfNeeded() {
        if (initGuard.compareAndSet(false, true)) {
            init();

            initLatch.countDown();
        } else {
            try {
                initLatch.await();
            }
            catch (InterruptedException e) {
                throw ExceptionUtil.propagateException(e);
            }
        }
    }

    public void init () {
        Ignite ignite = igniteProvider.get();
        stringsCache = ignite.getOrCreateCache(getCache8PartsConfig(STRINGS_CACHE));
        sequence = ignite.atomicSequence(STRINGS_SEQ, 0, true);
    }

    @Override public int getStringId(String value) {
        initIfNeeded();
        Integer integer = stringsCache.get(value);
        return integer == null ? -1 : integer;
    }

    @Override public String getStringFromId(int id) {
        return null;
    }


    @NotNull
    public static <K, V> CacheConfiguration<K, V> getCache8PartsConfig(String name) {
        CacheConfiguration<K, V> ccfg = new CacheConfiguration<>(name);

        ccfg.setAffinity(new RendezvousAffinityFunction(false, 8));

        return ccfg;
    }
}
