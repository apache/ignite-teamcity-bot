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

import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicSequence;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.cache.GuavaCached;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.ci.util.ObjectInterner;
import org.apache.ignite.configuration.CacheConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IgniteStringCompactor implements IStringCompactor {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(IgniteStringCompactor.class);

    private final AtomicBoolean initGuard = new AtomicBoolean();
    private final CountDownLatch initLatch = new CountDownLatch(1);

    /** Cache name */
    public static final String STRINGS_CACHE = "stringsCache";

    /** Strings sequence. */
    public static final String STRINGS_SEQ = "stringsSeq";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<String, CompactorEntity> stringsCache;

    /** Sequence. */
    private IgniteAtomicSequence seq;

    @Persisted
    public static class CompactorEntity {
        @QuerySqlField
        String val;
        @QuerySqlField(index = true)
        int id;

        public CompactorEntity(int candidate, String val) {
            this.id = candidate;
            this.val = val;
        }

        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("val", val)
                .add("id", id)
                .toString();
        }

        public int id() {
            return id;
        }
    }

    private void initIfNeeded() {
        if (initGuard.compareAndSet(false, true)) {
            init();

            initLatch.countDown();
        }
        else {
            try {
                initLatch.await();
            }
            catch (InterruptedException e) {
                throw ExceptionUtil.propagateException(e);
            }
        }
    }

    /**
     *
     */
    public void init() {
        Ignite ignite = igniteProvider.get();
        CacheConfiguration<String, CompactorEntity> cfg = getCache8PartsConfig(STRINGS_CACHE);

        cfg.setQueryEntities(Collections.singletonList(new QueryEntity(String.class, CompactorEntity.class)));

        stringsCache = ignite.getOrCreateCache(cfg);

        seq = ignite.atomicSequence(STRINGS_SEQ, 0, true);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @GuavaCached(cacheNegativeNumbersRval = false)
    @Override public int getStringId(String val) {
        if (val == null)
            return -1;

        initIfNeeded();

        CompactorEntity entity = stringsCache.get(val);
        if (entity != null)
            return entity.id;

        int codeCandidate = (int)seq.incrementAndGet();

        boolean valWasSet = stringsCache.putIfAbsent(val, new CompactorEntity(codeCandidate, val));

        return valWasSet ? codeCandidate : stringsCache.get(val).id;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @GuavaCached(cacheNullRval = false)
    @Override public String getStringFromId(int id) {
        if (id < 0)
            return null;

        initIfNeeded();

        QueryCursor<Cache.Entry<String, CompactorEntity>> qryCursor
            = stringsCache.query(new SqlQuery<String, CompactorEntity>(CompactorEntity.class, "id = ?").setArgs(id));

        Iterator<Cache.Entry<String, CompactorEntity>> iter = qryCursor.iterator();

        if (!iter.hasNext()) {
            System.err.println("Not found string by id " + id);

            return null;
        }

        Cache.Entry<String, CompactorEntity> next = iter.next();

        qryCursor.close();

        return ObjectInterner.internString(next.getValue().val);
    }

    /** {@inheritDoc} */
    @GuavaCached(cacheNullRval = false, cacheNegativeNumbersRval = false)
    @Override public Integer getStringIdIfPresent(String val) {
        if (val == null)
            return -1;

        initIfNeeded();

        CompactorEntity entity = stringsCache.get(val);

        if (entity != null)
            return entity.id;

        return null;

    }

    @NotNull
    public static <K, V> CacheConfiguration<K, V> getCache8PartsConfig(String name) {
        CacheConfiguration<K, V> ccfg = new CacheConfiguration<>(name);

        ccfg.setAffinity(new RendezvousAffinityFunction(false, 8));

        return ccfg;
    }
}
