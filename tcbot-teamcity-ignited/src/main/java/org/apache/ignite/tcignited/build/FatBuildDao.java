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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
import org.apache.ignite.tcignited.history.SuiteInvocation;
import org.apache.ignite.tcignited.history.SuiteInvocationHistoryDao;
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

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    @Inject SuiteInvocationHistoryDao historyDao;

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

        historyDao.init();

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

    /**
     * @param srvIdMaskHigh Server id mask to be placed at high bits in the key.
     * @param buildIdsSupplier Latest actual Build ids supplier. This supplier should handle all equivalent branches in it.
     * @param testName Test name.
     * @param buildTypeId Suite (Build type) id.
     * @param normalizedBaseBranch Branch name.
     */
    @AutoProfiling
    public IRunHistory getTestRunHist(int srvIdMaskHigh,
        Function<Set<Integer>, Set<Integer>> buildIdsSupplier, int testName, int buildTypeId, int normalizedBaseBranch) {

        RunHistKey runHistKey = new RunHistKey(srvIdMaskHigh, buildTypeId, normalizedBaseBranch);

        SuiteHistory hist;
        try {
            hist = runHistInMemCache.get(runHistKey,
                () -> loadSuiteHistory(srvIdMaskHigh, buildIdsSupplier, buildTypeId, normalizedBaseBranch));
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }

        return hist.testsHistory.get(testName);
    }


    //todo create standalone history collector class
    @AutoProfiling
    public SuiteHistory loadSuiteHistory(int srvId,
        Function<Set<Integer>, Set<Integer>> buildIdsSupplier,
        int buildTypeId,
        int normalizedBaseBranch) {
        Map<Integer, SuiteInvocation> suiteRunHist = historyDao.getSuiteRunHist(srvId, buildTypeId, normalizedBaseBranch);

        Set<Integer> buildIds = determineLatestBuildsFunction(buildIdsSupplier, suiteRunHist.keySet());

        HashSet<Integer> missedBuildsIds = new HashSet<>(buildIds);

        missedBuildsIds.removeAll(suiteRunHist.keySet());

        if (!missedBuildsIds.isEmpty()) {
            Map<Integer, SuiteInvocation> addl = addSuiteInvocationsToHistory(srvId, missedBuildsIds, normalizedBaseBranch);

            System.err.println("***** + Adding to persisted history for suite "
                + compactor.getStringFromId(buildTypeId)
                + " branch " + compactor.getStringFromId(normalizedBaseBranch) + " requires " +
                addl.size() + " invocations");

            historyDao.putAll(srvId, addl);
            suiteRunHist.putAll(addl);
        }

        SuiteHistory sumary = new SuiteHistory();

        suiteRunHist.forEach((buildId, suiteInv) -> {
            suiteInv.tests().forEach((tName, test) -> {
                sumary.testsHistory.computeIfAbsent(tName,
                    k_ -> new RunHistCompacted()).innerAddInvocation(test);
            });

        });

        System.err.println("***** History for suite "
            + compactor.getStringFromId(buildTypeId)
            + " branch" + compactor.getStringFromId(normalizedBaseBranch) + " requires " +
            sumary.size(igniteProvider.get()) + " bytes");

        return sumary;
    }

    @AutoProfiling
    public Map<Integer, SuiteInvocation> addSuiteInvocationsToHistory(int srvId,
        HashSet<Integer> missedBuildsIds, int normalizedBaseBranch) {
        Map<Integer, SuiteInvocation> suiteRunHist = new HashMap<>();
        int successStatusStrId = compactor.getStringId(TestOccurrence.STATUS_SUCCESS);

        getAllFatBuilds(srvId, missedBuildsIds).forEach((buildCacheKey, fatBuildCompacted) -> {
            SuiteInvocation sinv = new SuiteInvocation(srvId, normalizedBaseBranch, fatBuildCompacted, compactor, (k, v) -> false);

            Stream<TestCompacted> tests = fatBuildCompacted.getAllTests();
            tests.forEach(
                testCompacted -> {
                    Invocation invocation = testCompacted.toInvocation(fatBuildCompacted, (k, v) -> false, successStatusStrId);

                    sinv.addTest(testCompacted.testName(), invocation);
                }
            );

            suiteRunHist.put(fatBuildCompacted.id(), sinv);
        });

        return suiteRunHist;
    }

    @AutoProfiling
    protected SuiteHistory calcSuiteHistory(int srvIdMaskHigh, Set<Integer> buildIds) {
        Set<Long> cacheKeys = buildsIdsToCacheKeys(srvIdMaskHigh, buildIds);

        int successStatusStrId = compactor.getStringId(TestOccurrence.STATUS_SUCCESS);

        CacheEntryProcessor<Long, FatBuildCompacted, Map<Integer, Invocation>> processor = new HistoryCollectProcessor(successStatusStrId);

        Map<Long, EntryProcessorResult<Map<Integer, Invocation>>> map = buildsCache.invokeAll(cacheKeys, processor);

        SuiteHistory hist = new SuiteHistory();

        map.values().forEach(
            res -> {
                if (res == null)
                    return;

                Map<Integer, Invocation> invocationMap = res.get();

                if (invocationMap == null)
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

    private static Set<Long> buildsIdsToCacheKeys(int srvIdMaskHigh, Collection<Integer> stream) {
        return stream.stream()
            .filter(Objects::nonNull).map(id -> buildIdToCacheKey(srvIdMaskHigh, id)).collect(Collectors.toSet());
    }

    @AutoProfiling
    protected Set<Integer> determineLatestBuildsFunction(Function<Set<Integer>, Set<Integer>> buildIdsSupplier,
        Set<Integer> known) {
        return buildIdsSupplier.apply(known);
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
