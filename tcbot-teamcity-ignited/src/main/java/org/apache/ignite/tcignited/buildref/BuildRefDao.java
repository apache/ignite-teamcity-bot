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

package org.apache.ignite.tcignited.buildref;

import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistKey;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.util.GridIntList;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.tcbot.common.conf.TcBotSystemProperties;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.build.UpdateCountersStorage;
import org.apache.ignite.tcservice.model.hist.BuildRef;

/**
 *
 */
public class BuildRefDao {
    /** Cache name */
    public static final String TEAMCITY_BUILD_CACHE_NAME = "teamcityBuildRef";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds (Refs) cache: Long(ServerId||BuildId)-> Build reference */
    private IgniteCache<Long, BuildRefCompacted> buildRefsCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /** Update Counters for branch-related changes storage. */
    @Inject private UpdateCountersStorage countersStorage;

    /** Non persistence cache for all BuildRefsCompacted for particular branch.
     * RunHistKey(ServerId||BranchId||suiteId)-> Build reference
     */
    private final com.google.common.cache.Cache<RunHistKey, List<BuildRefCompacted>> buildRefsInMemCache
        = CacheBuilder.newBuilder()
        .maximumSize(Boolean.valueOf(System.getProperty(TcBotSystemProperties.DEV_MODE)) ? 1000 : 8000)
        .expireAfterAccess(16, TimeUnit.MINUTES)
        .expireAfterWrite(45, TimeUnit.MINUTES) //workaround for stale records, enforcing to ask persistence sometimes.
        .softValues()
        .build();


    /** Build refs in mem cache for all branch. Mostly this cache exist for fast building replace with recent builds. */
    private final com.google.common.cache.Cache<Long, List<BuildRefCompacted>> buildRefsInMemCacheForAllBranch
        = CacheBuilder.newBuilder()
        .maximumSize(Boolean.valueOf(System.getProperty(TcBotSystemProperties.DEV_MODE)) ? 200 : 2000)
        .expireAfterAccess(2, TimeUnit.MINUTES)
        .expireAfterWrite(4, TimeUnit.MINUTES) //workaround for stale records
        .softValues()
        .build();

    /** */
    public BuildRefDao init() {
        CacheConfiguration<Long, BuildRefCompacted> cfg = CacheConfigs.getCacheV2Config(TEAMCITY_BUILD_CACHE_NAME);

        cfg.setQueryEntities(Collections.singletonList(new QueryEntity(Long.class, BuildRefCompacted.class)));

        buildRefsCache = igniteProvider.get().getOrCreateCache(cfg);

        return this;
    }

    /**
     * @param srvId Server id.
     * @return all builds for a server, full scan.
     */
    @Nonnull
    public Stream<BuildRefCompacted> compactedBuildsForServer(
        final int srvId,
        @Nullable final IgnitePredicate<BuildRefCompacted> filter) {
        QueryCursor<Cache.Entry<Long, BuildRefCompacted>> qry
            = buildRefsCache.query(
                new ScanQuery<Long, BuildRefCompacted>().setFilter(
                    (k, v) -> {
                        if (!isKeyForServer(k, srvId))
                            return false;

                        return filter == null || filter.apply(v);
                    }));

        return StreamSupport.stream(qry.spliterator(), false)
            .map(javax.cache.Cache.Entry::getValue);
    }

    /**
     * @param key Key.
     * @param srvId Server id.
     */
    public static boolean isKeyForServer(Long key, int srvId) {
        return key!=null && cacheKeyToSrvId(key) == srvId;
    }

    /**
     * @param srvId Server id mask high.
     * @param ghData Gh data.
     */
    @AutoProfiling
    public Set<Long> saveChunk(int srvId, List<BuildRef> ghData) {
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
        if (size != 0) {
            buildRefsCache.putAll(entriesToPut);

            invalidateHistoryInMem(srvId, entriesToPut.values().stream());
        }

        return entriesToPut.keySet();
    }

    public void invalidateHistoryInMem(int srvId, Stream<BuildRefCompacted> stream) {
        Set<RunHistKey> setOfHistToClear = stream
            .map(b -> new RunHistKey(srvId, b.buildTypeId(), b.branchName()))
            .collect(Collectors.toSet());

        Iterable<Long> cacheForAllBranch =
            setOfHistToClear.stream()
                .map(b -> branchNameToHistCacheKey(srvId, b.branch()))
                .collect(Collectors.toSet());

        buildRefsInMemCacheForAllBranch.invalidateAll(cacheForAllBranch);
        buildRefsInMemCache.invalidateAll(setOfHistToClear);

        setOfHistToClear.forEach(b -> {
            int branch = b.branch();
            countersStorage.increment(branch);
        });
    }

    /**
     * @param srvId Server id mask high.
     * @param buildId Build id.
     */
    public static long buildIdToCacheKey(long srvId, int buildId) {
        return (long)buildId | srvId << 32;
    }

    /**
     * @param cacheKey Cache key.
     */
    public static int cacheKeyToBuildId(Long cacheKey) {
        long l = cacheKey << 32;
        return (int)(l >> 32);
    }

    /**
     * @param cacheKey Cache key.
     */
    public static int cacheKeyToSrvId(long cacheKey) {
        return (int)(cacheKey >> 32);
    }

    /**
     * @param srvId Server id mask high.
     * @param buildTypeIdId Build type (suite) id from compactor.
     * @param branchNameIds Branch names for query.
     */
    @AutoProfiling
    @Nonnull public List<BuildRefCompacted> getAllBuildsCompacted(int srvId,
        int buildTypeIdId,
        Collection<Integer> branchNameIds) {
        List<BuildRefCompacted> res = new ArrayList<>();

        branchNameIds.forEach(branchNameId -> {
            RunHistKey runHistKey = new RunHistKey(srvId, buildTypeIdId, branchNameId);
            try {
                List<BuildRefCompacted> compactedBuildsForBranch =
                    buildRefsInMemCache.get(runHistKey, () -> {
                        List<BuildRefCompacted> branch = getBuildsForBranch(srvId, branchNameId);

                        List<BuildRefCompacted> resForBranch = branch.stream()
                            .filter(e -> e.buildTypeId() == buildTypeIdId)
                            .collect(Collectors.toList());

                        if (!resForBranch.isEmpty()) {
                            System.err.println("Branch " + compactor.getStringFromId(branchNameId)
                                + " Suite " + compactor.getStringFromId(buildTypeIdId)
                                + " builds " + resForBranch.size() + " ");
                        }

                        return resForBranch;
                    });

                res.addAll(compactedBuildsForBranch);
            }
            catch (ExecutionException e) {
                throw ExceptionUtil.propagateException(e);
            }
        });

        return res;
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

        return compactedBuildsForServer(srvId, bref -> list.contains(bref.state()))
            .collect(Collectors.toList());
    }

    private static long branchNameToHistCacheKey(long srvId, int branchName) {
        return (long)branchName | srvId << 32;
    }


    /**
     * Collects all builds for branch. Short-term cached because builds from same branch may be queued several times.
     *
     * @param srvId Server id.
     * @param branchNameId  Branch name - IDs from compactor.
     */
    @AutoProfiling
    public List<BuildRefCompacted> getBuildsForBranch(int srvId, int branchNameId) {
        long branchKey = branchNameToHistCacheKey(srvId, branchNameId);

        try {
            return buildRefsInMemCacheForAllBranch.get(branchKey, () -> getBuildsForBranchNonCached(srvId, branchNameId));
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }

    public List<BuildRefCompacted> getBuildsForBranchNonCached(int srvId, int branchNameId) {
        List<BuildRefCompacted> list = new ArrayList<>();

        try (QueryCursor<Cache.Entry<Long, BuildRefCompacted>> qryCursor = buildRefsCache.query(
            new SqlQuery<Long, BuildRefCompacted>(BuildRefCompacted.class, "branchName = ?")
                .setArgs((Integer)branchNameId))) {

            for (Cache.Entry<Long, BuildRefCompacted> next : qryCursor) {
                Long key = next.getKey();

                if (!isKeyForServer(key, srvId))
                    continue;

                list.add(next.getValue());
            }
        }

        if (!list.isEmpty()) {
            System.err.println(" Branch " + compactor.getStringFromId(branchNameId)
                + " builds " + list.size() + " (Overall) ");
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
            invalidateHistoryInMem(srvId, Stream.of(refCompacted));

            return true;
        }

        return false;
    }

    @AutoProfiling
    public int[] getAllIds(int srvId) {
        GridIntList res = new GridIntList(buildRefsCache.size());

        getAllBuildRefs(srvId)
                .map(Cache.Entry::getKey)
                .map(BuildRefDao::cacheKeyToBuildId)
                .forEach(res::add);

        return res.array();
    }

    @Nonnull public Stream<Cache.Entry<Long, BuildRefCompacted>> getAllBuildRefs(int srvId) {
        return StreamSupport.stream(buildRefsCache.spliterator(), false)
                .filter(entry -> isKeyForServer(entry.getKey(), srvId));
    }

    public IgniteCache<Long, BuildRefCompacted> buildRefsCache() {
        return buildRefsCache;
    }

    public BuildRefCompacted get(int srvId, Integer buildId) {
        return buildRefsCache.get(buildIdToCacheKey(srvId, buildId));
    }

    public void remove(long key) {
        buildRefsCache.remove(key);
    }

    public void removeAll(Set<Long> keys) {
        buildRefsCache.removeAll(keys);
    }
}
