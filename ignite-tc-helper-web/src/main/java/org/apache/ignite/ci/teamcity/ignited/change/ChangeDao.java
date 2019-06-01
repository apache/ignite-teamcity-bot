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

package org.apache.ignite.ci.teamcity.ignited.change;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.configuration.CacheConfiguration;

public class ChangeDao {
    /** Cache name */
    public static final String TEAMCITY_CHANGE_CACHE_NAME = "teamcityChange";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Change Compacted cache. */
    private IgniteCache<Long, ChangeCompacted> changesCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /** */
    public void init() {
        CacheConfiguration<Long, ChangeCompacted> cfg = TcHelperDb.getCacheV2Config(TEAMCITY_CHANGE_CACHE_NAME);

        changesCache = igniteProvider.get().getOrCreateCache(cfg);
    }

    /**
     * @param key Key.
     * @param srvId Server id.
     */
    private boolean isKeyForServer(Long key, int srvId) {
        return key!=null && key >> 32 == srvId;
    }

    /**
     * @param srvId Server id mask high.
     * @param buildId Build id.
     */
    public long changeIdToCacheKey(long srvId, int buildId) {
        return (long)buildId | srvId << 32;
    }

    public static int cacheKeyToChangeId(Long cacheKey) {
        long l = cacheKey << 32;
        return (int) (l>>32);
    }

    /**
     * @param srvId Server id.
     * @param changeId
     * @param refCompacted Reference compacted.
     */
    @AutoProfiling
    public boolean save(int srvId, int changeId, ChangeCompacted refCompacted) {
        long cacheKey = changeIdToCacheKey(srvId, changeId);
        ChangeCompacted changePersisted = changesCache.get(cacheKey);

        if (changePersisted == null || !changePersisted.equals(refCompacted)) {
            changesCache.put(cacheKey, refCompacted);

            return true;
        }

        return false;
    }

    public ChangeCompacted load(int srvId, int changeId) {
        return changesCache.get(changeIdToCacheKey(srvId, changeId));
    }

    @AutoProfiling
    public Map<Long, ChangeCompacted> getAll(int srvIdMaskHigh, int[] changeIds) {
        final Set<Long> collect = new HashSet<>();

        for (int changeId : changeIds)
            collect.add(changeIdToCacheKey(srvIdMaskHigh, changeId));

        return changesCache.getAll(collect);
    }
}
