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

package org.apache.ignite.tcignited.build;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CacheEntryProcessor;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.buildtime.BuildTimeResult;
import org.apache.ignite.tcignited.history.HistoryCollector;
import org.apache.ignite.tcservice.model.changes.ChangesList;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence;
import org.apache.ignite.tcservice.model.result.stat.Statistics;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrencesFull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import javax.cache.processor.MutableEntry;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 *
 */
public class FatBuildDao {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(FatBuildDao.class);

    /** Cache name */
    public static final String TEAMCITY_FAT_BUILD_CACHE_NAME = "teamcityFatBuild";
    public static final int MAX_FAT_BUILD_CHUNK = 32 * 10;

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, FatBuildCompacted> buildsCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /** History collector. */
    @Inject private HistoryCollector histCollector;

    /**
     *
     */
    public FatBuildDao init() {
        buildsCache = igniteProvider.get().getOrCreateCache(CacheConfigs.getCacheV2Config(TEAMCITY_FAT_BUILD_CACHE_NAME));

        return this;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId
     * @param build Build data.
     * @param tests TestOccurrences one or several pages.
     * @param problems
     * @param statistics
     * @param changesList
     * @param existingBuild existing version of build in the DB.
     * @return Fat Build saved (if modifications detected), otherwise null.
     */
    @Nullable public FatBuildCompacted saveBuild(int srvIdMaskHigh,
                                       int buildId,
                                       @Nonnull Build build,
                                       @Nonnull List<TestOccurrencesFull> tests,
                                       @Nullable List<ProblemOccurrence> problems,
                                       @Nullable Statistics statistics,
                                       @Nullable ChangesList changesList,
                                       @Nullable FatBuildCompacted existingBuild) {
        Preconditions.checkNotNull(buildsCache, "init() was not called");
        Preconditions.checkNotNull(build, "build can't be null");

        FatBuildCompacted newBuild = new FatBuildCompacted(compactor, build);

        for (TestOccurrencesFull next : tests)
            newBuild.addTests(compactor, next.getTests());

        if (problems != null)
            newBuild.addProblems(compactor, problems);

        if (statistics != null)
            newBuild.statistics(compactor, statistics);

        if (changesList != null)
            newBuild.changes(extractChangeIds(changesList));

        if (existingBuild == null || !existingBuild.equals(newBuild)) {
            putFatBuild(srvIdMaskHigh, buildId, newBuild);

            return newBuild;
        }

        return null;
    }

    @AutoProfiling
    public void putFatBuild(int srvIdMaskHigh, int buildId, FatBuildCompacted newBuild) {
        buildsCache.put(buildIdToCacheKey(srvIdMaskHigh, buildId), newBuild);

        histCollector.invalidateHistoryInMem(srvIdMaskHigh, newBuild);
    }

    public static int[] extractChangeIds(@Nonnull ChangesList changesList) {
        return changesList.changes().stream().mapToInt(
                        ch -> {
                            try {
                                return Integer.parseInt(ch.id);
                            } catch (Exception e) {
                                logger.error("Unable to parse change id ", e);
                                return -1;
                            }
                        }
                ).filter(id -> id > 0).toArray();
    }

    /**
     * @param srvIdMaskHigh Server id mask to be placed at high bits of the key.
     * @param buildId Build id.
     */
    public static long buildIdToCacheKey(int srvIdMaskHigh, int buildId) {
        return (long)buildId | (long)srvIdMaskHigh << 32;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId Build id.
     */
    @AutoProfiling
    public FatBuildCompacted getFatBuild(int srvIdMaskHigh, int buildId) {
        Preconditions.checkNotNull(buildsCache, "init() was not called");

        return buildsCache.get(buildIdToCacheKey(srvIdMaskHigh, buildId));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildsIds Builds ids.
     */
    public Map<Long, FatBuildCompacted> getAllFatBuilds(int srvIdMaskHigh, Collection<Integer> buildsIds) {
        Preconditions.checkNotNull(buildsCache, "init() was not called");

        Set<Long> ids = buildsIdsToCacheKeys(srvIdMaskHigh, buildsIds);

        return buildsCache.getAll(ids);
    }

    /**
     * @param key Key.
     * @param srvId Server id.
     */
    public static boolean isKeyForServer(Long key, int srvId) {
        return key != null && key >> 32 == srvId;
    }

    public boolean containsKey(int srvIdMaskHigh, int buildId) {
        return buildsCache.containsKey(buildIdToCacheKey(srvIdMaskHigh, buildId));
    }

    public Stream<Cache.Entry<Long, FatBuildCompacted>> outdatedVersionEntries(int srvId) {
        return StreamSupport.stream(buildsCache.spliterator(), false)
            .filter(entry -> entry.getValue().isOutdatedEntityVersion())
            .filter(entry -> isKeyForServer(entry.getKey(), srvId));
    }

    private static Set<Long> buildsIdsToCacheKeys(int srvId, Collection<Integer> stream) {
        return stream.stream()
            .filter(Objects::nonNull).map(id -> buildIdToCacheKey(srvId, id)).collect(Collectors.toSet());
    }

    /**
     * @param srvId Server id.
     * @param buildId Build Id.
     */
    @Nullable public Long getBuildStartTime(int srvId, Integer buildId) {
        IgniteCache<Long, BinaryObject> cacheBin = buildsCache.withKeepBinary();
        long key = buildIdToCacheKey(srvId, buildId);

        return cacheBin.invoke(key, new GetStartTimeProc());
    }

    /**
     * @param srvId Server id.
     * @param ids Ids.
     */
    public Map<Integer, Long> getBuildStartTime(int srvId, Set<Integer> ids) {
        IgniteCache<Long, BinaryObject> cacheBin = buildsCache.withKeepBinary();
        Set<Long> keys = buildsIdsToCacheKeys(srvId, ids);
        HashMap<Integer, Long> res = new HashMap<>();

        Iterables.partition(keys, MAX_FAT_BUILD_CHUNK).forEach(
            chunk -> {
                Map<Long, EntryProcessorResult<Long>> map = cacheBin.invokeAll(keys, new GetStartTimeProc());
                map.forEach((k, r) -> {
                    Long ts = r.get();
                    if (ts != null)
                        res.put(BuildRefDao.cacheKeyToBuildId(k), ts);
                });
            }
        );

        return res;
    }

    public BuildTimeResult loadBuildTimeResult(int ageDays, List<Long> idsToCheck) {
        int stateRunning = compactor.getStringId(BuildRef.STATE_RUNNING);
        Integer buildDurationId = compactor.getStringIdIfPresent(Statistics.BUILD_DURATION);
        int timeoutProblemCode = compactor.getStringId(ProblemOccurrence.TC_EXECUTION_TIMEOUT);

        BuildTimeResult res = new BuildTimeResult();

        // also may take affinity into account
        Iterables.partition(idsToCheck, MAX_FAT_BUILD_CHUNK).forEach(
                chunk -> {
                    HashSet<Long> keys = new HashSet<>(chunk);
                    Map<Long, FatBuildCompacted> all = buildsCache.getAll(keys);
                    all.forEach((key, build) -> {
                        if (build.isComposite())
                            return;

                        long runningTime = getBuildRunningTime(stateRunning, buildDurationId, build);
                        if (runningTime > 0) {
                            int buildTypeId = build.buildTypeId();
                            System.err.println("Running " + runningTime + " BT: " + buildTypeId);

                            int srvId = BuildRefDao.cacheKeyToSrvId(key);
                            boolean hasTimeout = build.hasBuildProblemType(timeoutProblemCode);

                            res.add(srvId, buildTypeId, runningTime, hasTimeout);
                        }
                    });
                }
        );

        return res;

    }

    public static long getBuildRunningTime(int stateRunning, Integer buildDurationId,
                                           FatBuildCompacted buildBinary) {
        long startTs = buildBinary.getStartDateTs();

        if (startTs <= 0)
            return -1;

        int state = buildBinary.state();

        long runningTime = -1;
        if (stateRunning == state)
            runningTime = System.currentTimeMillis() - startTs;

        if (runningTime < 0) {
            if (buildDurationId != null) {
                Long val = buildBinary.statisticValue(buildDurationId);

                runningTime = (val != null && val >= 0) ? val : -1;
            }


        }

        if (runningTime < 0) {
            long finishTs = buildBinary.getFinishDateTs();

            if (finishTs > 0)
                runningTime = finishTs - startTs;
        }

        return runningTime;
    }

    public static long getBuildRunningTime(int stateRunning, Integer buildDurationId,
        BinaryObject buildBinary) {
        Long startTs = buildBinary.field("startDate");

        if (startTs == null || startTs <= 0)
            return -1;


        int status = buildBinary.field("status");
        int state = buildBinary.field("state");

        long runningTime = -1;
        if(stateRunning == state)
            runningTime = System.currentTimeMillis() - startTs;

        if(runningTime<0){

            if (buildDurationId != null) {
                BinaryObject statistics = buildBinary.field("statistics");
                if(statistics!=null) {
                    // statistics.field()
                }
                long val = -1; //statistics.findPropertyValue(buildDurationId);

                runningTime = val >= 0 ? val : -1;
            }


        }

        if(runningTime<0) {
            Long finishTs= buildBinary.field("finishDate");

            if(finishTs!=null)
                runningTime = finishTs - startTs;
        }

        return runningTime;
    }

    private static class GetStartTimeProc implements CacheEntryProcessor<Long, BinaryObject, Long> {
        public GetStartTimeProc() {
        }

        /** {@inheritDoc} */
        @Override public Long process(MutableEntry<Long, BinaryObject> entry,
            Object... arguments) throws EntryProcessorException {
            if (entry.getValue() == null)
                return null;

            BinaryObject buildBinary = entry.getValue();

            Long startDate = buildBinary.field("startDate");

            if (startDate == null || startDate <= 0)
                return null;

            return startDate;
        }
    }
}
