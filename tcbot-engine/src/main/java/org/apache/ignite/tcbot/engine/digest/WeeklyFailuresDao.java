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
package org.apache.ignite.tcbot.engine.digest;

import java.util.Collections;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicSequence;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

public class WeeklyFailuresDao implements IDigestStorage {
    public static final String DIGEST_HIST_CACHE = "botDigestHist";
    public static final String DIGEST_ENUMERATION_SEQ = "digestIdSeq";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /** Digest id sequence. */
    private IgniteAtomicSequence digestIdSeq;

    /**
     * @param trackedBranchName Tracked branch name.
     */
    @Nullable
    public WeeklyFailuresDigest findLatest(String trackedBranchName) {
        Integer trBrId = compactor.getStringIdIfPresent(trackedBranchName);
        if (trBrId == null)
            return null;

        WeeklyFailuresDigest latest = null;
        IgniteCache<Integer, WeeklyFailuresDigest> cache = cache();

        try (QueryCursor<Cache.Entry<Integer, WeeklyFailuresDigest>> qryCursor = cache.query(
            new SqlQuery<Integer, WeeklyFailuresDigest>(WeeklyFailuresDigest.class, "branchNameId = ?")
                .setArgs(trBrId))) {

            for (Cache.Entry<Integer, WeeklyFailuresDigest> next : qryCursor) {
                WeeklyFailuresDigest digest = next.getValue();
                if (latest == null)
                    latest = digest;
                else if (digest.ts > latest.ts)
                    latest = digest;
            }
        }

        return latest;
    }

    public void save(WeeklyFailuresDigest digest) {
        if (digest.branchNameId == null)
            digest.branchNameId = compactor.getStringId(digest.trackedBranchName);

        int id = (int)digestIdSeq.incrementAndGet();

        cache().put(id, digest);

    }

    public void init() {
        Ignite ignite = igniteProvider.get();
        digestIdSeq = ignite.atomicSequence(DIGEST_ENUMERATION_SEQ, 0, true);
    }

    public IgniteCache<Integer, WeeklyFailuresDigest> cache() {
        CacheConfiguration<Integer, WeeklyFailuresDigest> cfg = CacheConfigs.getCache8PartsConfig(DIGEST_HIST_CACHE);

        cfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

        cfg.setQueryEntities(Collections.singletonList(new QueryEntity(String.class, WeeklyFailuresDigest.class)));

        return igniteProvider.get().getOrCreateCache(cfg);
    }
}
