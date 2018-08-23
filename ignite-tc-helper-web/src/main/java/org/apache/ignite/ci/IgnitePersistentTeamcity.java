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

package org.apache.ignite.ci;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.analysis.Expirable;
import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.analysis.LogCheckResult;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.db.DbMigrations;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.util.CacheUpdateUtil;
import org.apache.ignite.ci.util.CollectionUtil;
import org.apache.ignite.ci.util.ObjectInterner;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXParseException;

import static org.apache.ignite.ci.BuildChainProcessor.normalizeBranch;

/**
 *
 */
public class IgnitePersistentTeamcity implements IAnalyticsEnabledTeamcity, ITeamcity, ITcAnalytics {

    //V1 caches, 1024 parts

    //V2 caches, 32 parts
    public static final String TESTS_OCCURRENCES = "testOccurrences";
    public static final String TESTS_RUN_STAT = "testsRunStat";
    public static final String LOG_CHECK_RESULT = "logCheckResult";
    public static final String CHANGE_INFO_FULL = "changeInfoFull";
    public static final String CHANGES_LIST = "changesList";
    public static final String TEST_FULL = "testFull";
    public static final String BUILD_PROBLEMS = "buildProblems";
    public static final String BUILD_STATISTICS = "buildStatistics";
    public static final String BUILD_HIST_FINISHED = "buildHistFinished";
    public static final String BUILD_HIST_FINISHED_OR_FAILED = "buildHistFinishedOrFailed";
    public static final String BOT_DETECTED_ISSUES = "botDetectedIssues";

    //todo need separate cache or separate key for 'execution time' because it is placed in statistics
    public static final String BUILDS_FAILURE_RUN_STAT = "buildsFailureRunStat";
    public static final String BUILDS = "builds";

    public static final String BUILD_QUEUE = "buildQueue";
    public static final String RUNNING_BUILDS = "runningBuilds";

    private final Ignite ignite;
    private final IgniteTeamcityHelper teamcity;
    private final String serverId;

    /** cached loads of full test occurrence. */
    private ConcurrentMap<String, CompletableFuture<TestOccurrenceFull>> testOccFullFutures = new ConcurrentHashMap<>();

    /** cached loads of queued builds for branch. */
    private ConcurrentMap<String, CompletableFuture<List<BuildRef>>> queuedBuildsFuts = new ConcurrentHashMap<>();

    /** cached loads of running builds for branch. */
    private ConcurrentMap<String, CompletableFuture<List<BuildRef>>> runningBuildsFuts = new ConcurrentHashMap<>();

    //todo: not good code to keep it static
    private static long lastTriggerMs = System.currentTimeMillis();

    public IgnitePersistentTeamcity(Ignite ignite, @Nullable String srvId) {
        this(ignite, new IgniteTeamcityHelper(srvId));
    }

    private IgnitePersistentTeamcity(Ignite ignite, IgniteTeamcityHelper teamcity) {
        this.ignite = ignite;
        this.teamcity = teamcity;
        this.serverId = teamcity.serverId();

        DbMigrations migrations = new DbMigrations(ignite, teamcity.serverId());

        migrations.dataMigration(
            testOccurrencesCache(), this::addTestOccurrencesToStat,
            this::migrateOccurrencesToLatest,
            buildsCache(), this::addBuildOccurrenceToFailuresStat,
            buildsFailureRunStatCache(), testRunStatCache(),
            testFullCache(),
            buildProblemsCache(),
            buildStatisticsCache(),
            buildHistCache(),
            buildHistIncFailedCache());
    }

    /**
     * Creates atomic cache with 32 parts.
     * @param name Cache name.
     */
    private <K, V> IgniteCache<K, V> getOrCreateCacheV2(String name) {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2Config(name));
    }

    /**
     * Creates transactional cache with 32 parts.
     * @param name Cache name.
     */
    private <K, V> IgniteCache<K, V> getOrCreateCacheV2Tx(String name) {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(name));
    }

    /**
     * @return {@link Build}s cache, 32 parts.
     */
    private IgniteCache<String, Build> buildsCache() {
        return getOrCreateCacheV2(ignCacheNme(BUILDS));
    }

    /**
     * @return {@link TestOccurrences} instances cache, 32 parts.
     */
    private IgniteCache<String, TestOccurrences> testOccurrencesCache() {
        return getOrCreateCacheV2(ignCacheNme(TESTS_OCCURRENCES));
    }

    /**
     * @return {@link TestOccurrenceFull} instances cache, 32 parts.
     */
    private IgniteCache<String, TestOccurrenceFull> testFullCache() {
        return getOrCreateCacheV2(ignCacheNme(TEST_FULL));
    }

    /**
     * @return Build {@link ProblemOccurrences} instances cache, 32 parts.
     */
    private IgniteCache<String, ProblemOccurrences> buildProblemsCache() {
        return getOrCreateCacheV2(ignCacheNme(BUILD_PROBLEMS));
    }

    /**
     * @return Build {@link Statistics} instances cache, 32 parts.
     */
    private IgniteCache<String, Statistics> buildStatisticsCache() {
        return getOrCreateCacheV2(ignCacheNme(BUILD_STATISTICS));
    }


    /**
     * @return Build history: {@link BuildRef} lists cache, 32 parts.
     */
    private IgniteCache<SuiteInBranch, Expirable<List<BuildRef>>> buildHistCache() {
        return getOrCreateCacheV2Tx(ignCacheNme(BUILD_HIST_FINISHED));
    }

    /**
     * @return Build history: {@link BuildRef} lists cache, 32 parts, transaactional
     */
    public IgniteCache<SuiteInBranch, Expirable<List<BuildRef>>> buildHistIncFailedCache() {
        return getOrCreateCacheV2Tx(ignCacheNme(BUILD_HIST_FINISHED_OR_FAILED));
    }


    /** {@inheritDoc} */
    @Override public CompletableFuture<List<BuildType>> getProjectSuites(String projectId) {
        return teamcity.getProjectSuites(projectId);
    }

    /** {@inheritDoc} */
    @Override public String serverId() {
        return serverId;
    }

    private <K, V> V loadIfAbsentV2(String cacheName, K key, Function<K, V> loadFunction) {
        return loadIfAbsent(getOrCreateCacheV2(ignCacheNme(cacheName)), key, loadFunction, (V v) -> true);
    }

    private <K, V> V loadIfAbsent(IgniteCache<K, V> cache, K key, Function<K, V> loadFunction) {
        return loadIfAbsent(cache, key, loadFunction, null);
    }

    private <K, V> V loadIfAbsent(IgniteCache<K, V> cache, K key, Function<K, V> loadFunction,
        Predicate<V> saveValueFilter) {
        @Nullable final V persistedBuilds = cache.get(key);

        if (persistedBuilds != null) {
            int fields = ObjectInterner.internFields(persistedBuilds);

            return persistedBuilds;
        }

        final V loaded = loadFunction.apply(key);

        if (saveValueFilter == null || saveValueFilter.test(loaded))
            cache.put(key, loaded);

        return loaded;
    }

    private <K, V> V timedLoadIfAbsentOrMerge(IgniteCache<K, Expirable<V>> cache, int seconds, K key,
        BiFunction<K, V, V> loadWithMerge) {
        @Nullable final Expirable<V> persistedBuilds = cache.get(key);

        int fields = ObjectInterner.internFields(persistedBuilds);

        if (persistedBuilds != null) {
            if (persistedBuilds.isAgeLessThanSecs(seconds))
                return persistedBuilds.getData();
        }

        Lock lock = cache.lock(key);
        lock.lock();

        V apply;
        try {
            apply = loadWithMerge.apply(key, persistedBuilds != null ? persistedBuilds.getData() : null);

            final Expirable<V> newVal = new Expirable<>(System.currentTimeMillis(), apply);

            cache.put(key, newVal);
        }
        finally {
            lock.unlock();
        }

        return apply;
    }

    /** {@inheritDoc} */
    @Override public List<BuildRef> getFinishedBuilds(String projectId, String branch) {
        final SuiteInBranch suiteInBranch = new SuiteInBranch(projectId, branch);

        return timedLoadIfAbsentOrMerge(buildHistCache(), 60, suiteInBranch,
            (key, persistedValue) -> {
                List<BuildRef> builds;
                try {
                    builds = teamcity.getFinishedBuilds(projectId, branch);
                }
                catch (Exception e) {
                    if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                        System.err.println("Build history not found for build : " + projectId + " in " + branch);
                        builds = Collections.emptyList();
                    }
                    else
                        throw e;
                }

                return mergeByIdToHistoricalOrder(persistedValue, builds);
            });
    }

    @NotNull
    private List<BuildRef> mergeByIdToHistoricalOrder(List<BuildRef> persistedVal, List<BuildRef> mostActualVal) {
        final SortedMap<Integer, BuildRef> merge = new TreeMap<>();

        if (persistedVal != null)
            persistedVal.forEach(b -> merge.put(b.getId(), b));

        mostActualVal.forEach(b -> merge.put(b.getId(), b)); //to overwrite data from persistence by values from REST

        return new ArrayList<>(merge.values());
    }

    //loads build history with following parameter: defaultFilter:false,state:finished

    /** {@inheritDoc} */
    @Override public List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch) {
        final SuiteInBranch suiteInBranch = new SuiteInBranch(projectId, branch);

        return timedLoadIfAbsentOrMerge(buildHistIncFailedCache(), 60, suiteInBranch,
            (key, persistedValue) -> {
                List<BuildRef> failed = teamcity.getFinishedBuildsIncludeSnDepFailed(projectId, branch);

                return mergeByIdToHistoricalOrder(persistedValue, failed);
            });
    }


    /** {@inheritDoc} */
    @Override public CompletableFuture<List<BuildRef>> getRunningBuilds(String branch) {
        int defaultSecs = 60;
        int secondsUseCached = getTriggerRelCacheValidSecs(defaultSecs);

        return CacheUpdateUtil.loadAsyncIfAbsentOrExpired(
            getOrCreateCacheV2(ignCacheNme(RUNNING_BUILDS)),
            Strings.nullToEmpty(branch),
            runningBuildsFuts,
            teamcity::getRunningBuilds,
            secondsUseCached,
            secondsUseCached == defaultSecs);
    }

    public static int getTriggerRelCacheValidSecs(int defaultSecs) {
        long msSinceTrigger = System.currentTimeMillis() - lastTriggerMs;
        long secondsSinceTigger = TimeUnit.MILLISECONDS.toSeconds(msSinceTrigger);
        return Math.min((int)secondsSinceTigger, defaultSecs);
    }

    /** {@inheritDoc} */
    @Override public CompletableFuture<List<BuildRef>> getQueuedBuilds(@Nullable final String branch) {
        int defaultSecs = 60;
        int secondsUseCached = getTriggerRelCacheValidSecs(defaultSecs);

        return CacheUpdateUtil.loadAsyncIfAbsentOrExpired(
            getOrCreateCacheV2(ignCacheNme(BUILD_QUEUE)),
            Strings.nullToEmpty(branch),
            queuedBuildsFuts,
            teamcity::getQueuedBuilds,
            secondsUseCached,
            secondsUseCached == defaultSecs);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public Build getBuild(String href) {
        final IgniteCache<String, Build> cache = buildsCache();

        @Nullable final Build persistedBuild = cache.get(href);

        int fields = ObjectInterner.internFields(persistedBuild);

        if (persistedBuild != null) {
            if (!persistedBuild.isOutdatedEntityVersion())
                return persistedBuild;
        }

        final Build loaded = realLoadBuild(href);
        //can't reload, but cached has value
        if (loaded.isFakeStub() && persistedBuild != null && persistedBuild.isOutdatedEntityVersion()) {
            persistedBuild._version = persistedBuild.latestVersion();
            cache.put(href, persistedBuild);

            return persistedBuild;
        }

        if (loaded.isFakeStub() || loaded.hasFinishDate()) {
            cache.put(href, loaded);

            addBuildOccurrenceToFailuresStat(loaded);
        }

        return loaded;
    }

    private void addBuildOccurrenceToFailuresStat(Build loaded) {
        if (loaded.isFakeStub())
            return;

        String suiteId = loaded.suiteId();
        if (Strings.isNullOrEmpty(suiteId))
            return;

        SuiteInBranch key = keyForBuild(loaded);

        buildsFailureRunStatCache().invoke(key, (entry, arguments) -> {
            SuiteInBranch suiteInBranch = entry.getKey();

            Build build = (Build)arguments[0];

            RunStat val = entry.getValue();

            if (val == null)
                val = new RunStat(suiteInBranch.getSuiteId());

            val.addBuildRun(build);

            entry.setValue(val);

            return null;
        }, loaded);
    }

    @NotNull private SuiteInBranch keyForBuild(Build loaded) {
        return new SuiteInBranch(loaded.suiteId(), normalizeBranch(loaded));
    }

    private Build realLoadBuild(String href1) {
        try {
            return teamcity.getBuild(href1);
        }
        catch (Exception e) {
            if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                System.err.println("Exception " + e.getClass().getSimpleName() + ": " + e.getMessage());
                return Build.createFakeStub();// save null result, because persistence may refer to some non-existent build on TC
            }
            else
                throw e;
        }
    }

    @NotNull private String ignCacheNme(String cache) {
        return ignCacheNme(cache, serverId);
    }

    @NotNull public static String ignCacheNme(String cache, String serverId) {
        return serverId + "." + cache;
    }

    /** {@inheritDoc} */
    @Override public String host() {
        return teamcity.host();
    }

    /** {@inheritDoc}*/
    @Override public ProblemOccurrences getProblems(Build build) {
        String href = build.problemOccurrences.href;

        return loadIfAbsent(
            buildProblemsCache(),
            href,
            k -> {
                ProblemOccurrences problems = teamcity.getProblems(build);

                registerCriticalBuildProblemInStat(build, problems);

                return problems;
            });
    }

    private void registerCriticalBuildProblemInStat(Build build, ProblemOccurrences problems) {
        boolean criticalFail = problems.getProblemsNonNull().stream().anyMatch(occurrence ->
            occurrence.isExecutionTimeout() || occurrence.isJvmCrash());

        String suiteId = build.suiteId();
        Integer buildId = build.getId();

        if (!criticalFail)
            return;

        if (buildId != null && !Strings.isNullOrEmpty(suiteId)) {
            SuiteInBranch key = new SuiteInBranch(suiteId, normalizeBranch(build));

            buildsFailureRunStatCache().invoke(key, (entry, arguments) -> {
                SuiteInBranch suiteInBranch = entry.getKey();

                Integer bId = (Integer)arguments[0];

                RunStat val = entry.getValue();

                if (val == null)
                    val = new RunStat(suiteInBranch.getSuiteId());

                val.setBuildCriticalError(bId);

                entry.setValue(val);

                return null;
            }, buildId);
        }
    }

    /** {@inheritDoc} */
    @Override public TestOccurrences getTests(String href, String normalizedBranch) {
        String hrefForDb = DbMigrations.removeCountFromRef(href);

        return loadIfAbsent(testOccurrencesCache(),
            hrefForDb,  //hack to avoid test reloading from store in case of href filter replaced
            hrefIgnored -> {
                TestOccurrences loadedTests = teamcity.getTests(href, normalizedBranch);

                //todo first touch of build here will cause build and its stat will be diverged
                addTestOccurrencesToStat(loadedTests, normalizedBranch);

                return loadedTests;
            });
    }

    private void addTestOccurrencesToStat(TestOccurrences val) {
        addTestOccurrencesToStat(val, ITeamcity.DEFAULT);
    }

    private void addTestOccurrencesToStat(TestOccurrences val, String normalizedBranch) {
        for (TestOccurrence next : val.getTests())
            addTestOccurrenceToStat(next, normalizedBranch);
    }

    /** {@inheritDoc} */
    @Override public Statistics getBuildStatistics(String href) {
        return loadIfAbsent(buildStatisticsCache(),
            href,
            href1 -> {
                try {
                    return teamcity.getBuildStatistics(href1);
                }
                catch (Exception e) {
                    if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                        e.printStackTrace();
                        return new Statistics();// save null result, because persistence may refer to some  unexistent build on TC
                    }
                    else
                        throw e;
                }
            });
    }

    /** {@inheritDoc} */
    @Override public CompletableFuture<TestOccurrenceFull> getTestFull(String href) {
        return CacheUpdateUtil.loadAsyncIfAbsent(
            testFullCache(),
            href,
            testOccFullFutures,
            teamcity::getTestFull);
    }

    /** {@inheritDoc} */
    @Override public Change getChange(String href) {
        return loadIfAbsentV2(CHANGE_INFO_FULL, href, href1 -> {
            try {
                return teamcity.getChange(href1);
            }
            catch (Exception e) {
                if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                    System.err.println("Change history not found for href : " + href);

                    return new Change();
                }
                if (Throwables.getRootCause(e) instanceof SAXParseException) {
                    System.err.println("Change data seems to be invalid: " + href);

                    return new Change();
                }
                else
                    throw e;
            }
        });
    }

    /** {@inheritDoc} */
    @Override public ChangesList getChangesList(String href) {
        return loadIfAbsentV2(CHANGES_LIST, href, href1 -> {
            try {
                return teamcity.getChangesList(href1);
            }
            catch (Exception e) {
                if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                    System.err.println("Change List not found for href : " + href);

                    return new ChangesList();
                }
                else
                    throw e;
            }
        });
    }

    /** {@inheritDoc} */
    @Override public List<RunStat> topTestFailing(int cnt) {
        return CollectionUtil.top(allTestAnalysis(), cnt, Comparator.comparing(RunStat::getFailRate));
    }

    /** {@inheritDoc} */
    @Override public List<RunStat> topTestsLongRunning(int cnt) {
        return CollectionUtil.top(allTestAnalysis(), cnt, Comparator.comparing(RunStat::getAverageDurationMs));
    }

    /** {@inheritDoc} */
    @Override public Function<TestInBranch, RunStat> getTestRunStatProvider() {
        return key -> key == null ? null : testRunStatCache().get(key);
    }


    private Stream<RunStat> allTestAnalysis() {
        return StreamSupport.stream(testRunStatCache().spliterator(), false)
            .map(Cache.Entry::getValue);
    }

    private IgniteCache<TestInBranch, RunStat> testRunStatCache() {
        return getOrCreateCacheV2(ignCacheNme(TESTS_RUN_STAT));
    }

    /** {@inheritDoc} */
    @Override public Function<SuiteInBranch, RunStat> getBuildFailureRunStatProvider() {
        return key -> key == null ? null : buildsFailureRunStatCache().get(key);
    }

    private Stream<RunStat> buildsFailureAnalysis() {
        return StreamSupport.stream(buildsFailureRunStatCache().spliterator(), false)
            .map(Cache.Entry::getValue);
    }

    /**
     * @return cache from suite name to its failure statistics
     */
    private IgniteCache<SuiteInBranch, RunStat> buildsFailureRunStatCache() {
        return getOrCreateCacheV2(ignCacheNme(BUILDS_FAILURE_RUN_STAT));
    }

    private IgniteCache<Integer, LogCheckResult> logCheckResultCache() {
        return getOrCreateCacheV2(ignCacheNme(LOG_CHECK_RESULT));
    }

    private void addTestOccurrenceToStat(TestOccurrence next, String normalizedBranch) {
        String name = next.getName();
        if (Strings.isNullOrEmpty(name))
            return;

        if (next.isMutedTest() || next.isIgnoredTest())
            return;

        TestInBranch k = new TestInBranch(name, normalizedBranch);

        testRunStatCache().invoke(k, (entry, arguments) -> {
            TestInBranch key = entry.getKey();
            TestOccurrence testOccurrence = (TestOccurrence)arguments[0];

            RunStat val = entry.getValue();
            if (val == null)
                val = new RunStat(key.getName());

            val.addTestRun(testOccurrence);

            entry.setValue(val);

            return null;
        }, next);
    }

    private void migrateOccurrencesToLatest(TestOccurrences val) {
        for (TestOccurrence next : val.getTests())
            migrateTestOneOcurrToAddToLatest(next);
    }

    private void migrateTestOneOcurrToAddToLatest(TestOccurrence next) {
        String name = next.getName();
        if (Strings.isNullOrEmpty(name))
            return;

        if (next.isMutedTest() || next.isIgnoredTest())
            return;

        TestInBranch k = new TestInBranch(name, ITeamcity.DEFAULT);

        testRunStatCache().invoke(k, (entry, arguments) -> {
            TestInBranch key = entry.getKey();
            TestOccurrence testOccurrence = (TestOccurrence)arguments[0];

            RunStat val = entry.getValue();
            if (val == null)
                val = new RunStat(key.name);

            val.addTestRunToLatest(testOccurrence);

            entry.setValue(val);

            return null;
        }, next);
    }

    /** {@inheritDoc} */
    public List<RunStat> topFailingSuite(int cnt) {
        return CollectionUtil.top(buildsFailureAnalysis(), cnt, Comparator.comparing(RunStat::getFailRate));
    }

    /** {@inheritDoc} */
    @Override public void close() {

    }

    /** {@inheritDoc} */
    @Override public CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut) {
        return teamcity.unzipFirstFile(fut);
    }

    /** {@inheritDoc} */
    @Override public CompletableFuture<File> downloadBuildLogZip(int id) {
        return teamcity.downloadBuildLogZip(id);
    }

    /** {@inheritDoc} */
    @Override public CompletableFuture<LogCheckResult> analyzeBuildLog(Integer buildId, SingleBuildRunCtx ctx) {
        return loadFutureIfAbsentVers(logCheckResultCache(), buildId,
            k -> teamcity.analyzeBuildLog(buildId, ctx));
    }

    public String getThreadDumpCached(Integer buildId) {
        IgniteCache<Integer, LogCheckResult> entries = logCheckResultCache();

        LogCheckResult logCheckResult = entries.get(buildId);

        if (logCheckResult == null)
            return null;

        int fields = ObjectInterner.internFields(logCheckResult);

        return logCheckResult.getLastThreadDump();
    }

    /**
     * @param cache
     * @param key
     * @param submitFunction caching of already submitted computations should be done by this function.
     * @param <K>
     * @param <V>
     * @return
     */
    public <K, V extends IVersionedEntity> CompletableFuture<V> loadFutureIfAbsentVers(IgniteCache<K, V> cache,
        K key,
        Function<K, CompletableFuture<V>> submitFunction) {
        @Nullable final V persistedValue = cache.get(key);

        if (persistedValue != null && !persistedValue.isOutdatedEntityVersion()) {
            int fields = ObjectInterner.internFields(persistedValue);

            return CompletableFuture.completedFuture(persistedValue);
        }

        CompletableFuture<V> apply = submitFunction.apply(key);

        return apply.thenApplyAsync(val -> {
            if (val != null)
                cache.put(key, val);

            return val;
        });
    }

    /** {@inheritDoc} */
    public void setExecutor(ExecutorService executor) {
        this.teamcity.setExecutor(executor);
    }

    /** {@inheritDoc} */
    @Override public void triggerBuild(String id, String name, boolean cleanRebuild, boolean queueAtTop) {
        lastTriggerMs = System.currentTimeMillis();

        teamcity.triggerBuild(id, name, cleanRebuild, queueAtTop);
    }

    @Override
    public void setAuthToken(String token) {
        teamcity.setAuthToken(token);
    }

    /** {@inheritDoc} */
    @Override public List<Agent> agents(boolean connected, boolean authorized) {
        return teamcity.agents(connected, authorized);
    }
}
