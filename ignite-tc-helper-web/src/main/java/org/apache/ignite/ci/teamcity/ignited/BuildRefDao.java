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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.cache.GuavaCached;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.util.GridIntList;
import org.jetbrains.annotations.NotNull;

public class BuildRefDao {
    /** Cache name */
    public static final String TEAMCITY_BUILD_CACHE_NAME = "teamcityBuildRef";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, BuildRefCompacted> buildRefsCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /** */
    public void init() {
        CacheConfiguration<Long, BuildRefCompacted> cfg = TcHelperDb.getCacheV2Config(TEAMCITY_BUILD_CACHE_NAME);

        cfg.setQueryEntities(Collections.singletonList(new QueryEntity(Long.class, BuildRefCompacted.class)));

        buildRefsCache = igniteProvider.get().getOrCreateCache(cfg);
    }

    /**
     * @param srvId Server id.
     * @return all builds for a server, full scan.
     */
    @NotNull protected Stream<BuildRefCompacted> compactedBuildsForServer(int srvId) {
        return StreamSupport.stream(buildRefsCache.spliterator(), false)
            .filter(entry -> isKeyForServer(entry.getKey(), srvId))
            .map(javax.cache.Cache.Entry::getValue);
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
     * @param ghData Gh data.
     */
    @AutoProfiling
    public Set<Long> saveChunk(long srvId, List<BuildRef> ghData) {
        Set<Long> ids = ghData.stream().map(BuildRef::getId)
            .filter(Objects::nonNull)
            .map(buildId -> buildIdToCacheKey(srvId, buildId))
            .collect(Collectors.toSet());

        Map<Long, BuildRefCompacted> existingEntries = buildRefsCache.getAll(ids);
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
            buildRefsCache.putAll(entriesToPut);

        return entriesToPut.keySet();
    }

    /**
     * @param srvId Server id mask high.
     * @param buildId Build id.
     */
    public static long buildIdToCacheKey(long srvId, int buildId) {
        return (long)buildId | srvId << 32;
    }

    public static int cacheKeyToBuildId(Long cacheKey) {
        long l = cacheKey << 32;
        return (int) (l>>32);
    }

    /**
     * @param srvId Server id mask high.
     * @param buildTypeId Build type id.
     * @param bracnhNameQry Bracnh name query.
     */
    @AutoProfiling
    @NotNull public List<BuildRefCompacted> findBuildsInHistoryCompacted(int srvId,
                                                       @Nullable String buildTypeId,
                                                       String bracnhNameQry) {

        Integer buildTypeIdId = compactor.getStringIdIfPresent(buildTypeId);
        if (buildTypeIdId == null)
            return Collections.emptyList();

        Integer bracnhNameQryId = compactor.getStringIdIfPresent(bracnhNameQry);
        if (bracnhNameQryId == null)
            return Collections.emptyList();

        return getBuildsForBranch(srvId, bracnhNameQry).stream()
                .filter(e -> e.buildTypeId() == buildTypeIdId)
                .collect(Collectors.toList());
    }

    /**
     * @param srvId Server id.
     */
    @AutoProfiling
    public List<BuildRefCompacted> getQueuedAndRunning(int srvId) {
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

    @AutoProfiling
    @GuavaCached(softValues = true, maximumSize = 1000, expireAfterAccessSecs = 30)
    public List<BuildRefCompacted> getBuildsForBranch(int srvId, String branchName) {
        Integer branchNameId = compactor.getStringIdIfPresent(branchName);
        if (branchNameId == null)
            return Collections.emptyList();

        List<BuildRefCompacted> list = new ArrayList<>();
        try (QueryCursor<Cache.Entry<Long, BuildRefCompacted>> qryCursor
                 = buildRefsCache.query(
            new SqlQuery<Long, BuildRefCompacted>(BuildRefCompacted.class, "branchName = ?")
                .setArgs(branchNameId))) {

            for (Cache.Entry<Long, BuildRefCompacted> next : qryCursor) {
                Long key = next.getKey();

                if (!isKeyForServer(key, srvId))
                    continue;

                list.add(next.getValue());
            }
        }

        return list;
    }

    /**
     * @param srvId Server id.
     * @param refCompacted Reference compacted.
     */
    @AutoProfiling
    public boolean save(int srvId, BuildRefCompacted refCompacted) {
        long cacheKey = buildIdToCacheKey(srvId, refCompacted.id());
        BuildRefCompacted buildPersisted = buildRefsCache.get(cacheKey);

        if (buildPersisted == null || !buildPersisted.equals(refCompacted)) {
            buildRefsCache.put(cacheKey, refCompacted);

            return true;
        }

        return false;
    }

    @AutoProfiling
    public int[] getAllIds(int srvId) {
        GridIntList res = new GridIntList(buildRefsCache.size());

        StreamSupport.stream(buildRefsCache.spliterator(), false)
                .map(Cache.Entry::getKey)
                .filter(entry -> isKeyForServer(entry, srvId))
                .map(BuildRefDao::cacheKeyToBuildId)
                .forEach(res::add);

        return res.array();
    }
}
