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
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.analysis.*;
import org.apache.ignite.ci.db.DbMigrations;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.cache.GuavaCached;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildTypeFull;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.util.CollectionUtil;
import org.apache.ignite.ci.util.ObjectInterner;
import org.apache.ignite.ci.web.model.hist.VisasHistoryStorage;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.apache.ignite.ci.teamcity.ignited.runhist.RunHistSync.normalizeBranch;

/**
 * Apache Ignite based cache over teamcity responses (REST caches).
 *
 * Cache is now overloaded with data, Compacted
 */
@Deprecated
public class IgnitePersistentTeamcity implements IAnalyticsEnabledTeamcity, ITeamcity, ITcAnalytics {
    //V2 caches, 32 parts (V1 caches were 1024 parts)
    @Deprecated
    private static final String TESTS_RUN_STAT = "testsRunStat";
    @Deprecated
    private static final String CALCULATED_STATISTIC = "calculatedStatistic";
    private static final String LOG_CHECK_RESULT = "logCheckResult";

    //todo need separate cache or separate key for 'execution time' because it is placed in statistics
    private static final String BUILDS_FAILURE_RUN_STAT = "buildsFailureRunStat";
    public static final String BUILDS = "builds";

    @Inject
    private Ignite ignite;

    /** */
    @Inject
    private VisasHistoryStorage visasHistStorage;

    /**
     * Teamcity
     */
    private ITeamcity teamcity;

    @Nullable
    private String serverId;

    /** cached running builds for branch. */
    private ConcurrentMap<String, Expirable<List<BuildRef>>> queuedBuilds = new ConcurrentHashMap<>();

    /** cached loads of queued builds for branch. */
    private ConcurrentMap<String, CompletableFuture<List<BuildRef>>> queuedBuildsFuts = new ConcurrentHashMap<>();

    //todo: not good code to keep it static
    @Deprecated
    private static long lastTriggerMs = System.currentTimeMillis();

    @Override public void init(ITeamcity conn) {
        this.teamcity = conn;
        this.serverId = conn.serverId();

        DbMigrations migrations = new DbMigrations(ignite, conn.serverId());

        migrations.dataMigration(
            buildsCache(), this::addBuildOccurrenceToFailuresStat,
                buildsFailureRunStatCache(), testRunStatCache(),
                visasHistStorage.visas());
    }

    @Override
    public void init(String serverId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public User getUserByUsername(String username) {
        return teamcity.getUserByUsername(username);
    }

    /**
     * Creates atomic cache with 32 parts.
     * @param name Cache name.
     */
    private <K, V> IgniteCache<K, V> getOrCreateCacheV2(String name) {
        final IgniteCache<K, V> cache = ignite.getOrCreateCache(TcHelperDb.getCacheV2Config(name));

        cache.enableStatistics(true);

        return cache;
    }

    /**
     * Creates transactional cache with 32 parts.
     * @param name Cache name.
     */
    private <K, V> IgniteCache<K, V> getOrCreateCacheV2Tx(String name) {
        final IgniteCache<K, V> cache = ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(name));

        cache.enableStatistics(true);

        return cache;
    }

    /**
     * @return {@link Build}s cache, 32 parts.
     */
    private IgniteCache<String, Build> buildsCache() {
        return getOrCreateCacheV2(ignCacheNme(BUILDS));
    }

    /** {@inheritDoc} */
    @AutoProfiling
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

    @Deprecated
    private <K, V> CompletableFuture<V> loadAsyncIfAbsentOrExpired(ConcurrentMap<K, Expirable<V>> cache,
                                                                   K key,
                                                                   ConcurrentMap<K, CompletableFuture<V>> cachedComputations,
                                                                   Function<K, CompletableFuture<V>> realLoadFunction,
                                                                   int maxAgeSecs,
                                                                   boolean alwaysProvidePersisted) {
        @Nullable final Expirable<V> persistedVal = cache.get(key);

        if (persistedVal != null && persistedVal.isAgeLessThanSecs(maxAgeSecs))
            return CompletableFuture.completedFuture(persistedVal.getData());

        AtomicReference<CompletableFuture<V>> submitRef = new AtomicReference<>();

        CompletableFuture<V> loadFut = cachedComputations.computeIfAbsent(key,
                k -> {
                    CompletableFuture<V> future = realLoadFunction.apply(k)
                            .thenApplyAsync(valueLoaded -> {
                                final Expirable<V> cached = new Expirable<>(valueLoaded);

                                ObjectInterner.internFields(cached);

                                cache.put(k, cached);

                                return valueLoaded;
                            });

                    submitRef.set(future);

                    return future;
                }
        ).thenApply(res -> {
            CompletableFuture<V> f = submitRef.get();

            if (f != null)
                cachedComputations.remove(key, f);

            return res;
        });

        if (alwaysProvidePersisted && persistedVal != null)
            return CompletableFuture.completedFuture(persistedVal.getData());

        return loadFut;
    }


    public static int getTriggerRelCacheValidSecs(int defaultSecs) {
        long msSinceTrigger = System.currentTimeMillis() - lastTriggerMs;
        long secondsSinceTrigger = TimeUnit.MILLISECONDS.toSeconds(msSinceTrigger);
        return Math.min((int)secondsSinceTrigger, defaultSecs);
    }

    /** {@inheritDoc} */
    @Override public CompletableFuture<List<BuildRef>> getQueuedBuilds(@Nullable final String branch) {
        int defaultSecs = 60;
        int secondsUseCached = getTriggerRelCacheValidSecs(defaultSecs);

        return loadAsyncIfAbsentOrExpired(
            queuedBuilds,
            Strings.nullToEmpty(branch),
            queuedBuildsFuts,
            teamcity::getQueuedBuilds,
            secondsUseCached,
            secondsUseCached == defaultSecs);
    }

    /** {@inheritDoc} */
    @Nullable
    @AutoProfiling
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
        return new SuiteInBranch(loaded.suiteId(), normalizeBranch(loaded.branchName));
    }

    @Deprecated
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

    /** {@inheritDoc} */
    @Override public Build getBuild(int buildId) {
        return teamcity.getBuild(buildId);
    }

    @Deprecated
    private void registerCriticalBuildProblemInStat(BuildRef build, ProblemOccurrences problems) {
        boolean criticalFail = problems.getProblemsNonNull().stream().anyMatch(occurrence ->
            occurrence.isExecutionTimeout()
                || occurrence.isJvmCrash()
                || occurrence.isFailureOnMetric()
                || occurrence.isCompilationError());

        String suiteId = build.suiteId();
        Integer buildId = build.getId();

        if (!criticalFail)
            return;

        if (buildId != null && !Strings.isNullOrEmpty(suiteId)) {
            SuiteInBranch key = new SuiteInBranch(suiteId, normalizeBranch(build.branchName));

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
    @Override public List<RunStat> topTestFailing(int cnt) {
        return CollectionUtil.top(allTestAnalysis(), cnt, Comparator.comparing(RunStat::getFailRate));
    }

    /** {@inheritDoc} */
    @Override public List<RunStat> topTestsLongRunning(int cnt) {
        return CollectionUtil.top(allTestAnalysis(), cnt, Comparator.comparing(RunStat::getAverageDurationMs));
    }

    /** {@inheritDoc} */
    @Override public Function<TestInBranch, RunStat> getTestRunStatProvider() {
        return key -> key == null ? null : getRunStatForTest(key);
    }

    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    @GuavaCached(maximumSize = 200, expireAfterAccessSecs = 30, softValues = true)
    protected RunStat getRunStatForTest(TestInBranch key) {
        return testRunStatCache().get(key);
    }

    private Stream<RunStat> allTestAnalysis() {
        return StreamSupport.stream(testRunStatCache().spliterator(), false)
            .map(Cache.Entry::getValue);
    }

    @Deprecated
    private IgniteCache<TestInBranch, RunStat> testRunStatCache() {
        return getOrCreateCacheV2(ignCacheNme(TESTS_RUN_STAT));
    }

    @Deprecated
    private IgniteCache<Integer, Boolean> calculatedStatistic() {
        return getOrCreateCacheV2(ignCacheNme(CALCULATED_STATISTIC));
    }

    /** {@inheritDoc} */
    @Override public Function<SuiteInBranch, RunStat> getBuildFailureRunStatProvider() {
        return key -> key == null ? null : getRunStatForSuite(key);
    }


    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    @GuavaCached(maximumSize = 500, expireAfterAccessSecs = 90, softValues = true)
    protected RunStat getRunStatForSuite(SuiteInBranch key) {
        return buildsFailureRunStatCache().get(key);
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

    @Deprecated
    private void addTestOccurrenceToStat(TestOccurrence next, String normalizedBranch, Boolean changesExist) {
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

            val.addTestRun(testOccurrence, changesExist);

            entry.setValue(val);

            return null;
        }, next);
    }

    /** {@inheritDoc} */
    @Override public List<RunStat> topFailingSuite(int cnt) {
        return CollectionUtil.top(buildsFailureAnalysis(), cnt, Comparator.comparing(RunStat::getFailRate));
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


    @AutoProfiling
    @Override public String getThreadDumpCached(Integer buildId) {
        IgniteCache<Integer, LogCheckResult> entries = logCheckResultCache();

        LogCheckResult logCheckRes = entries.get(buildId);

        if (logCheckRes == null)
            return null;

        int fields = ObjectInterner.internFields(logCheckRes);

        return logCheckRes.getLastThreadDump();
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public void calculateBuildStatistic(SingleBuildRunCtx ctx) {
        if (ctx.buildId() == null)
            return;

        if (calculatedStatistic().containsKey(ctx.buildId()))
            return;

        for (TestOccurrence testOccurrence : ctx.getTests()) {
            String branch = normalizeBranch(ctx.getBranch());

            addTestOccurrenceToStat(testOccurrence, branch, !ctx.getChanges().isEmpty());
        }

        calculatedStatistic().put(ctx.buildId(), true);
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
        @Nullable final V persistedVal = cache.get(key);

        if (persistedVal != null && !persistedVal.isOutdatedEntityVersion()) {
            int fields = ObjectInterner.internFields(persistedVal);

            return CompletableFuture.completedFuture(persistedVal);
        }

        CompletableFuture<V> apply = submitFunction.apply(key);

        return apply.thenApplyAsync(val -> {
            if (val != null)
                cache.put(key, val);

            return val;
        });
    }

    public Executor getExecutor() {
        return this.teamcity.getExecutor();
    }

    /** {@inheritDoc} */
    @Override public void setExecutor(ExecutorService executor) {
        this.teamcity.setExecutor(executor);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public Build triggerBuild(String buildTypeId, @NotNull String branchName, boolean cleanRebuild, boolean queueAtTop) {
        lastTriggerMs = System.currentTimeMillis();

        return teamcity.triggerBuild(buildTypeId, branchName, cleanRebuild, queueAtTop);
    }

    @Override public ProblemOccurrences getProblems(int buildId) {
        return teamcity.getProblems(buildId);
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override public ProblemOccurrences getProblemsAndRegisterCritical(BuildRef build) {
        ProblemOccurrences problems = teamcity.getProblems(build.getId());

        registerCriticalBuildProblemInStat(build, problems);

        return problems;
    }

    @Override public Statistics getStatistics(int buildId) {
        return teamcity.getStatistics(buildId);
    }

    @Override public ChangesList getChangesList(int buildId) {
        return teamcity.getChangesList(buildId);
    }

    @Override public Change getChange(int changeId) {
        return teamcity.getChange(changeId);
    }

    /** {@inheritDoc} */
    @Override public BuildTypeFull getBuildType(String buildTypeId) {
        return teamcity.getBuildType(buildTypeId);
    }

    /** {@inheritDoc} */
    @Override public void setAuthToken(String tok) {
        teamcity.setAuthToken(tok);
    }

    /** {@inheritDoc} */
    @Override public boolean isTeamCityTokenAvailable() {
        return teamcity.isTeamCityTokenAvailable();
    }

    /** {@inheritDoc} */
    @Override public void setJiraToken(String tok) {
        teamcity.setJiraToken(tok);
    }

    /** {@inheritDoc} */
    @Override public boolean isJiraTokenAvailable() {
        return teamcity.isJiraTokenAvailable();
    }

    /** {@inheritDoc} */
    @Override public String sendJiraComment(String ticket, String comment) throws IOException {
        return teamcity.sendJiraComment(ticket, comment);
    }

    /** {@inheritDoc} */
    @Override public void setJiraApiUrl(String url) {
        teamcity.setJiraApiUrl(url);
    }

    /** {@inheritDoc} */
    @Override public String getJiraApiUrl() {
        return teamcity.getJiraApiUrl();
    }

    /** {@inheritDoc} */
    @Override public List<Agent> agents(boolean connected, boolean authorized) {
        return teamcity.agents(connected, authorized);
    }

    /** {@inheritDoc} */
    @Override public List<BuildRef> getBuildRefsPage(String fullUrl, AtomicReference<String> nextPage) {
        return teamcity.getBuildRefsPage(fullUrl, nextPage);
    }

    /** {@inheritDoc} */
    @Override public TestOccurrencesFull getTestsPage(int buildId, String href, boolean testDtls) {
        return teamcity.getTestsPage(buildId, href, testDtls);
    }
}
