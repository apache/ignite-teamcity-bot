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
import com.google.common.cache.CacheBuilder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import javax.cache.processor.MutableEntry;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheEntryProcessor;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.TestCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistKey;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcservice.model.changes.ChangesList;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence;
import org.apache.ignite.tcservice.model.result.stat.Statistics;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrence;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrencesFull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class FatBuildDao {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(FatBuildDao.class);

    /** Cache name */
    public static final String TEAMCITY_FAT_BUILD_CACHE_NAME = "teamcityFatBuild";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, FatBuildCompacted> buildsCache;


    /** Suite history cache. */
    private IgniteCache<RunHistKey, SuiteHistory> suiteHistory;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * Non persistence cache for all suite RunHistory for particular branch.
     * RunHistKey(ServerId||BranchId||suiteId)-> Build reference
     */
    private final com.google.common.cache.Cache<RunHistKey, SuiteHistory> runHistInMemCache
        = CacheBuilder.newBuilder()
        .maximumSize(8000)
        .expireAfterAccess(16, TimeUnit.MINUTES)
        .softValues()
        .build();


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

        invalidateHistoryInMem(srvIdMaskHigh, Stream.of(newBuild));
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
     * @param srvIdMaskHigh Server id mask high.
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

        Set<Long> ids = buildsIds.stream()
            .filter(Objects::nonNull)
            .map(buildId -> buildIdToCacheKey(srvIdMaskHigh, buildId))
            .collect(Collectors.toSet());

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

    public IRunHistory getTestRunHist(int srvIdMaskHigh,
        Supplier<Set<Integer>> buildIdsSupplier, int testName, int suiteName, int branchName) {


        RunHistKey runHistKey = new RunHistKey(srvIdMaskHigh, suiteName, branchName);

        SuiteHistory history;

        try {
            history = runHistInMemCache.get(runHistKey,
                () -> {
                    Set<Integer> buildIds = determineLatestBuilds(buildIdsSupplier);

                    return calcSuiteHistory(srvIdMaskHigh, buildIds);
                });
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }

        return history.testsHistory.get(testName);
    }

    @AutoProfiling
    protected SuiteHistory calcSuiteHistory(int srvIdMaskHigh, Set<Integer> buildIds) {
        Set<Long> cacheKeys = buildIds.stream().map(id -> buildIdToCacheKey(srvIdMaskHigh, id)).collect(Collectors.toSet());

        int successStatusStrId = compactor.getStringId(TestOccurrence.STATUS_SUCCESS);

        CacheEntryProcessor<Long, FatBuildCompacted, Map<Integer, Invocation>> processor = new HistoryCollectProcessor(successStatusStrId);

        Map<Long, EntryProcessorResult<Map<Integer, Invocation>>> map = buildsCache.invokeAll(cacheKeys, processor);

        SuiteHistory hist = new SuiteHistory();

        map.values().forEach(
            res-> {
                if(res==null)
                    return;

                Map<Integer, Invocation> invocationMap = res.get();

                if(invocationMap == null)
                    return;

                invocationMap.forEach((k, v) -> {
                    RunHistCompacted compacted = hist.testsHistory.computeIfAbsent(k,
                        k_ -> new RunHistCompacted());

                    compacted.innerAddInvocation(v);
                });

            }
        );

        System.err.println("Suite history: tests in scope "
                + hist.testsHistory.size()
                + " for " +buildIds.size() + " builds checked"
                + " size " + hist.size(igniteProvider.get()));

        return hist;
    }

    @AutoProfiling
    protected Set<Integer> determineLatestBuilds(Supplier<Set<Integer>> buildIdsSupplier) {
        return buildIdsSupplier.get();
    }

    public void invalidateHistoryInMem(int srvId, Stream<BuildRefCompacted> stream) {
        Iterable<RunHistKey> objects =
            stream
                .map(b -> new RunHistKey(srvId, b.buildTypeId(), b.branchName()))
                .collect(Collectors.toSet());

        runHistInMemCache.invalidateAll(objects);
    }


    private static class HistoryCollectProcessor implements CacheEntryProcessor<Long, FatBuildCompacted, Map<Integer, Invocation>> {
        private final int successStatusStrId;

        public HistoryCollectProcessor(int successStatusStrId) {
            this.successStatusStrId = successStatusStrId;
        }

        @Override public Map<Integer, Invocation> process(MutableEntry<Long, FatBuildCompacted> entry,
            Object... arguments) throws EntryProcessorException {
            if (entry.getValue() == null)
                return null;

            Map<Integer, Invocation> hist = new HashMap<>();
            FatBuildCompacted fatBuildCompacted = entry.getValue();
            Stream<TestCompacted> tests = fatBuildCompacted.getAllTests();
            tests.forEach(
                testCompacted -> {
                    Invocation invocation = testCompacted.toInvocation(fatBuildCompacted, (k, v) -> false, successStatusStrId);

                    hist.put(testCompacted.testName(), invocation);
                }
            );

            return hist;
        }
    }
}
