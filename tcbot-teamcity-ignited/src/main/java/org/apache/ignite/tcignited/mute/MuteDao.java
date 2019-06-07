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

package org.apache.ignite.tcignited.mute;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.teamcity.ignited.mute.MuteInfoCompacted;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;

/**
 *
 */
public class MuteDao {
    /** Cache name. */
    private static final String TEAMCITY_MUTE_CACHE_NAME = "teamcityMute";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, MuteInfoCompacted> muteCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     *
     */
    public void init() {
        muteCache = igniteProvider.get().getOrCreateCache(CacheConfigs.getCacheV2Config(TEAMCITY_MUTE_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @return Server mutes.
     */
    @AutoProfiling
    public SortedSet<MuteInfo> getMutes(int srvIdMaskHigh) {
        Preconditions.checkNotNull(muteCache, "init() was not called");
        long srvId = (long) srvIdMaskHigh << 32;

        TreeSet<MuteInfo> res = new TreeSet<>();

        for (Cache.Entry<Long, MuteInfoCompacted> entry : muteCache) {
            if ((entry.getKey() & srvId) == srvId)
                res.add(entry.getValue().toMuteInfo(compactor));
        }

        return res;
    }

    /**
     * Combine server and project into key for storage.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param muteId Mute id.
     * @return Key from server-project pair.
     */
    private static long muteIdToCacheKey(int srvIdMaskHigh, int muteId) {
        return (long) muteId | (long) srvIdMaskHigh << 32;
    }

    /**
     * Save small part of loaded mutes.
     *  @param srvIdMaskHigh Server id mask high.
     * @param chunk Chunk.
     */
    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    protected void saveChunk(int srvIdMaskHigh, Set<MuteInfo> chunk) {
        Preconditions.checkNotNull(muteCache, "init() was not called");

        if (F.isEmpty(chunk))
            return;

        HashMap<Long, MuteInfoCompacted> compactedMutes = new HashMap<>(U.capacity(chunk.size()));

        for (MuteInfo mute : chunk) {
            long key = muteIdToCacheKey(srvIdMaskHigh, mute.id);
            MuteInfoCompacted val = new MuteInfoCompacted(mute, compactor);

            compactedMutes.put(key, val);
        }

        muteCache.putAll(compactedMutes);
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param muteId Mute id.
     */
    public boolean remove(int srvIdMaskHigh, int muteId) {
        return muteCache.remove(muteIdToCacheKey(srvIdMaskHigh, muteId));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param startId Start id.
     */
    public int removeAllAfter(int srvIdMaskHigh, int startId) {
        int rmv = 0;
        long srvId = (long) srvIdMaskHigh << 32;

        for (Cache.Entry<Long, MuteInfoCompacted> entry : muteCache) {
            if ((srvId & entry.getKey()) != srvId)
                continue;

            if (entry.getValue().id() > startId) {
                if (muteCache.remove(entry.getKey()))
                    rmv++;
            }
        }

        return rmv;
    }
}
