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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.util.GridIntList;
import org.jetbrains.annotations.NotNull;

public class BuildRefDao {
    /** Cache name */
    public static final String TEAMCITY_BUILD_CACHE_NAME = "teamcityBuild";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, BuildRefCompacted> buildsCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    public void init () {
        CacheConfiguration<Long, BuildRefCompacted> cfg = TcHelperDb.getCacheV2Config(TEAMCITY_BUILD_CACHE_NAME);
        buildsCache = igniteProvider.get().getOrCreateCache(cfg);
    }

    @NotNull protected Stream<BuildRefCompacted> compactedBuildsForServer(long srvId) {
        return StreamSupport.stream(buildsCache.spliterator(), false)
            .filter(entry -> entry.getKey() >> 32 == srvId)
            .map(javax.cache.Cache.Entry::getValue);
    }

    /**
     * @param srvId Server id mask high.
     * @param ghData Gh data.
     */
    public int saveChunk(long srvId, List<BuildRef> ghData) {
        Set<Long> ids = ghData.stream().map(BuildRef::getId)
            .filter(Objects::nonNull)
            .map(buildId -> buildIdToCacheKey(srvId, buildId))
            .collect(Collectors.toSet());

        Map<Long, BuildRefCompacted> existingEntries = buildsCache.getAll(ids);
        Map<Long, BuildRefCompacted> entriesToPut = new TreeMap<>();

        List<BuildRefCompacted> collect = ghData.stream()
            .map(ref -> new BuildRefCompacted(compactor, ref))
            .collect(Collectors.toList());

        for (BuildRefCompacted next : collect) {
            long cacheKey = buildIdToCacheKey(srvId, next.id());
            BuildRefCompacted buildPersisted = existingEntries.get(cacheKey);

            if (buildPersisted == null || !buildPersisted.equals(next))
                entriesToPut.put(cacheKey, next);
        }

        int size = entriesToPut.size();
        if (size != 0)
            buildsCache.putAll(entriesToPut);
        return size;
    }

    /**
     * @param srvId Server id mask high.
     * @param buildId Build id.
     */
    private long buildIdToCacheKey(long srvId, int buildId) {
        return (long)buildId | srvId << 32;
    }

    /**
     * @param srvId Server id mask high.
     * @param buildTypeId Build type id.
     * @param bracnhNameQry Bracnh name query.
     */
    @NotNull public List<BuildRef> findBuildsInHistory(long srvId,
        @Nullable String buildTypeId,
        String bracnhNameQry) {

        Integer buildTypeIdId = compactor.getStringIdIfPresent(buildTypeId);
        if (buildTypeIdId == null)
            return Collections.emptyList();

        Integer bracnhNameQryId = compactor.getStringIdIfPresent(bracnhNameQry);
        if (bracnhNameQryId == null)
            return Collections.emptyList();

        return compactedBuildsForServer(srvId)
            .filter(e -> e.buildTypeId() == buildTypeIdId)
            .filter(e -> e.branchName() == bracnhNameQryId)
            .map(compacted -> compacted.toBuildRef(compactor))
            .collect(Collectors.toList());
    }

    /**
     * @param srvId Server id.
     */
    public List<BuildRefCompacted> getQueuedAndRunning(long srvId) {
        GridIntList list = new GridIntList(2);
        Integer stateQueuedId = compactor.getStringIdIfPresent(BuildRef.STATE_QUEUED);
        if (stateQueuedId != null)
            list.add(stateQueuedId);

        Integer stateRunningId = compactor.getStringIdIfPresent(BuildRef.STATE_RUNNING);
        if (stateRunningId != null)
            list.add(stateRunningId);


        return compactedBuildsForServer(srvId)
            .filter(e ->  list.contains(e.state()) )
            .collect(Collectors.toList());
    }
}
