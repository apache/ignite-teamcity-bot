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

package org.apache.ignite.ci.tcmodel.mute;

import com.google.common.base.Preconditions;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.internal.util.typedef.F;

/**
 *
 */
public class MuteDao {
    /** Cache name. */
    public static final String TEAMCITY_MUTE_CACHE_NAME = "teamcityMute";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, MutesCompacted> muteCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     *
     */
    public void init() {
        muteCache = igniteProvider.get().getOrCreateCache(TcHelperDb.getCacheV2Config(TEAMCITY_MUTE_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Build id.
     */
    @AutoProfiling
    public Mutes getMutes(int srvIdMaskHigh, String projectId) {
        Preconditions.checkNotNull(muteCache, "init() was not called");

        MutesCompacted compacted = muteCache.get(projectIdToCacheKey(srvIdMaskHigh, projectId));

        return compacted != null ? compacted.toMutes(compactor) : new Mutes();
    }

    /**
     * Combine server and project into key for storage.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Build type id.
     * @return Key from server-project pair.
     */
    public static long projectIdToCacheKey(long srvIdMaskHigh, String projectId) {
        return (long)projectId.hashCode() | srvIdMaskHigh << 32;
    }

    /**
     * Save small part of loaded mutes.
     *
     * @param srvId Server id.
     * @param projectId Project id.
     * @param chunk Chunk.
     */
    public void saveChunk(int srvId, String projectId, Set<MuteInfo> chunk) {
        if (F.isEmpty(chunk))
            return;

        long key = projectIdToCacheKey(srvId, projectId);
        MutesCompacted compacted = muteCache.get(key);
        Mutes mutes;

        if (compacted == null)
            mutes = new Mutes(chunk);
        else {
            mutes = compacted.toMutes(compactor);

            mutes.add(chunk);
        }

        muteCache.put(key, new MutesCompacted(mutes, compactor));
    }

    /**
     * Check that mutes for specified poject are downloaded from specified server.
     *
     * @param srvId Server id.
     * @param projectId Project id.
     */
    public boolean projectExists(int srvId, String projectId) {
        return muteCache.containsKey(projectIdToCacheKey(srvId, projectId));
    }

    /**
     * Replace current chached mutes by fresh data.
     *
     * @param srvId Server id.
     * @param projectId Project id.
     * @param muteList Mute list.
     */
    public void refreshMutes(int srvId, String projectId, Set<MuteInfo> muteList) {
        long key = projectIdToCacheKey(srvId, projectId);

        muteCache.put(key, new MutesCompacted(muteList, compactor));
    }
}
