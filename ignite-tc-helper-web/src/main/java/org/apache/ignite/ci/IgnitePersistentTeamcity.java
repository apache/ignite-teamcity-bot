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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;

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
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.issues.IssuesUsagesList;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.util.CacheUpdateUtil;
import org.apache.ignite.ci.util.CollectionUtil;
import org.apache.ignite.ci.util.ObjectInterner;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXParseException;

import static org.apache.ignite.ci.BuildChainProcessor.normalizeBranch;

/**
 * Apache Ignite based cache over teamcity responses
 */
public class IgnitePersistentTeamcity implements IAnalyticsEnabledTeamcity, ITeamcity, ITcAnalytics {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(IgnitePersistentTeamcity.class);

    //V2 caches, 32 parts (V1 caches were 1024 parts)
    private static final String TESTS_OCCURRENCES = "testOccurrences";
    private static final String TESTS_RUN_STAT = "testsRunStat";
    private static final String CALCULATED_STATISTIC = "calculatedStatistic";
    private static final String LOG_CHECK_RESULT = "logCheckResult";
    private static final String CHANGE_INFO_FULL = "changeInfoFull";
    private static final String CHANGES_LIST = "changesList";
    private static final String ISSUES_USAGES_LIST = "issuesUsagesList";
    private static final String TEST_FULL = "testFull";
    private static final String BUILD_PROBLEMS = "buildProblems";
    private static final String BUILD_STATISTICS = "buildStatistics";
    private static final String BUILD_HIST_FINISHED = "buildHistFinished";
    private static final String BUILD_HIST_FINISHED_OR_FAILED = "buildHistFinishedOrFailed";
    public static final String BOT_DETECTED_ISSUES = "botDetectedIssues";

    //todo need separate cache or separate key for 'execution time' because it is placed in statistics
    private static final String BUILDS_FAILURE_RUN_STAT = "buildsFailureRunStat";
    public static final String BUILDS = "builds";

    /** Number of builds to re-query from TC to be sure some builds in the middle are not lost. */
    private static final int MAX_BUILDS_IN_PAST_TO_RELOAD = 5;

    @Inject
    private Ignite ignite;
    /**
     * Teamcity
     */
    private ITeamcity teamcity;
    private String serverId;

    /**
     * cached loads of full test occurrence.
     */
    private ConcurrentMap<String, CompletableFuture<TestOccurrenceFull>> testOccFullFutures = new ConcurrentHashMap<>();

    /** cached running builds for branch. */
    private ConcurrentMap<String, Expirable<List<BuildRef>>> queuedBuilds = new ConcurrentHashMap<>();

    /** cached loads of queued builds for branch. */
    private ConcurrentMap<String, CompletableFuture<List<BuildRef>>> queuedBuildsFuts = new ConcurrentHashMap<>();

    /** cached running builds for branch. */
    private ConcurrentMap<String, Expirable<List<BuildRef>>> runningBuilds = new ConcurrentHashMap<>();

    /** cached loads of running builds for branch. */
    private ConcurrentMap<String, CompletableFuture<List<BuildRef>>> runningBuildsFuts = new ConcurrentHashMap<>();

    /**   */
    private ConcurrentMap<SuiteInBranch, Long> lastQueuedHistory = new ConcurrentHashMap<>();

    //todo: not good code to keep it static
    private static long lastTriggerMs = System.currentTimeMillis();

    private static final boolean noLocks = true;

    @Deprecated
    public IgnitePersistentTeamcity(Ignite ignite, @Nullable String srvId) {
        this(ignite, new IgniteTeamcityConnection(srvId));
    }

    //for DI
    public IgnitePersistentTeamcity() {}

    @Deprecated
    private IgnitePersistentTeamcity(Ignite ignite, IgniteTeamcityConnection teamcity) {
        init(teamcity);
        this.ignite = ignite;
    }

    @Override public void init(ITeamcity conn) {
        this.teamcity = conn;
        this.serverId = conn.serverId();

        DbMigrations migrations = new DbMigrations(ignite, conn.serverId());

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

    @Override
    public void init(String serverId) {
        throw new UnsupportedOperationException();
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
     * @return Build history: {@link BuildRef} lists cache, 32 parts, transactional.
     */
    private IgniteCache<SuiteInBranch, Expirable<List<BuildRef>>> buildHistCache() {
        return getOrCreateCacheV2Tx(ignCacheNme(BUILD_HIST_FINISHED));
    }

    /**
     * @return Build history: {@link BuildRef} lists cache, 32 parts, transactional.
     */
    private IgniteCache<SuiteInBranch, Expirable<List<BuildRef>>> buildHistIncFailedCache() {
        return getOrCreateCacheV2Tx(ignCacheNme(BUILD_HIST_FINISHED_OR_FAILED));
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

    protected List<BuildRef> loadBuildHistory(IgniteCache<SuiteInBranch, Expirable<List<BuildRef>>> cache,
                                              int seconds,
                                              Long cnt,
                                              SuiteInBranch key,
                                              BiFunction<SuiteInBranch, Integer, List<BuildRef>> realLoad) {
        @Nullable Expirable<List<BuildRef>> persistedBuilds = readBuildHistEntry(cache, key);

        if (persistedBuilds != null
                && (isHistoryAgeLessThanSecs(key, seconds, persistedBuilds)
                && (cnt == null || persistedBuilds.hasCounterGreaterThan(cnt)))) {
            ObjectInterner.internFields(persistedBuilds);

            return persistedBuilds.getData();
        }

        Lock lock = lockBuildHistEntry(cache, key);

        try {
            if (!noLocks) {
                if (persistedBuilds != null
                        && (isHistoryAgeLessThanSecs(key, seconds, persistedBuilds)
                        && (cnt == null || persistedBuilds.hasCounterGreaterThan(cnt)))) {
                    ObjectInterner.internFields(persistedBuilds);

                    return persistedBuilds.getData();
                }
            }

            Integer sinceBuildId;
            if (persistedBuilds != null) {
                List<BuildRef> prevData = persistedBuilds.getData();
                if (prevData.size() >= MAX_BUILDS_IN_PAST_TO_RELOAD) {
                    BuildRef buildRef = prevData.get(prevData.size() - MAX_BUILDS_IN_PAST_TO_RELOAD);

                    sinceBuildId = buildRef.getId();
                } else {
                    sinceBuildId = null;
                }
            } else
                sinceBuildId = null;

            List<BuildRef> dataFromRest;
            try {
                dataFromRest = realLoad.apply(key, sinceBuildId);
            } catch (Exception e) {
                if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                    System.err.println("Build history not found for build : " + key);
                    dataFromRest = Collections.emptyList();
                } else if (Throwables.getRootCause(e) instanceof BadRequestException) {
                    //probably referenced build not found
                    if (sinceBuildId != null)
                        dataFromRest = realLoad.apply(key, null);
                    else
                        throw e;
                } else
                    throw e;
            }
            final List<BuildRef> persistedList = persistedBuilds != null ? persistedBuilds.getData() : null;
            final List<BuildRef> buildRefs = mergeHistoryMaps(persistedList, dataFromRest);

            final Expirable<List<BuildRef>> newVal = new Expirable<>(0L, buildRefs.size(), buildRefs);

            if (persistedList != null && persistedList.equals(buildRefs)) {
                lastQueuedHistory.put(key, System.currentTimeMillis());

                return buildRefs;
            }
            saveBuildHistoryEntry(cache, key, newVal);

            lastQueuedHistory.put(key, System.currentTimeMillis());

            return buildRefs;
        }
        finally {
            if (!noLocks)
                lock.unlock();
        }
    }

    private boolean isHistoryAgeLessThanSecs(SuiteInBranch key, int seconds, Expirable<List<BuildRef>> persistedBuilds) {
        Long loaded = lastQueuedHistory.get(key);
        if (loaded == null)
            return false;

        long ageMs = System.currentTimeMillis() - loaded;

        return ageMs < TimeUnit.SECONDS.toMillis(seconds);
    }

    @AutoProfiling
    @SuppressWarnings("WeakerAccess")
    protected <K> void saveBuildHistoryEntry(IgniteCache<K, Expirable<List<BuildRef>>> cache, K key, Expirable<List<BuildRef>> newVal) {
        cache.put(key, newVal);
    }


    @AutoProfiling
    @SuppressWarnings("WeakerAccess")
    protected <K> Expirable<List<BuildRef>> readBuildHistEntry(IgniteCache<K, Expirable<List<BuildRef>>> cache, K key) {
        return cache.get(key);
    }

    @AutoProfiling
    @SuppressWarnings("WeakerAccess")
    protected <K> Lock lockBuildHistEntry(IgniteCache<K, Expirable<List<BuildRef>>> cache, K key) {
        if (noLocks)
            return null;

        Lock lock = cache.lock(key);

        lock.lock();

        return lock;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRef> getFinishedBuilds(String projectId,
                                                      String branch,
                                                      Long cnt,
                                                      Integer ignored) {
        final SuiteInBranch suiteInBranch = new SuiteInBranch(projectId, branch);

        List<BuildRef> buildRefs = loadBuildHistory(buildHistCache(), 90, cnt, suiteInBranch,
            (key, sinceBuildId) -> teamcity.getFinishedBuilds(projectId, branch, cnt, sinceBuildId));

        if (cnt == null)
            return buildRefs;

        return buildRefs.stream()
            .skip(cnt < buildRefs.size() ? buildRefs.size() - cnt : 0)
            .collect(Collectors.toList());
    }

    @NotNull
    @AutoProfiling
    @SuppressWarnings("WeakerAccess")
    protected List<BuildRef> mergeHistoryMaps(@Nullable List<BuildRef> persistedVal, List<BuildRef> mostActualVal) {
        final SortedMap<Integer, BuildRef> merge = new TreeMap<>();

        if (persistedVal != null)
            persistedVal.forEach(b -> merge.put(b.getId(), b));

        mostActualVal.forEach(b -> merge.put(b.getId(), b)); //to overwrite data from persistence by values from REST

        return new ArrayList<>(merge.values());
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId,
                                                                        String branch,
                                                                        Long cnt,
                                                                        Integer ignored) {
        final SuiteInBranch suiteInBranch = new SuiteInBranch(projectId, branch);

        return loadBuildHistory(buildHistIncFailedCache(), 91, cnt, suiteInBranch,
            (key, sinceBuildId) -> teamcity.getFinishedBuildsIncludeSnDepFailed(projectId, branch, cnt, sinceBuildId));
    }


    private <K, V> CompletableFuture<V> loadAsyncIfAbsentOrExpired(ConcurrentMap<K, Expirable<V>> cache,
                                                                   K key,
                                                                   ConcurrentMap<K, CompletableFuture<V>> cachedComputations,
                                                                   Function<K, CompletableFuture<V>> realLoadFunction,
                                                                   int maxAgeSecs,
                                                                   boolean alwaysProvidePersisted) {
        @Nullable final Expirable<V> persistedValue = cache.get(key);

        if (persistedValue != null && persistedValue.isAgeLessThanSecs(maxAgeSecs))
            return CompletableFuture.completedFuture(persistedValue.getData());

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

        if (alwaysProvidePersisted && persistedValue != null)
            return CompletableFuture.completedFuture(persistedValue.getData());

        return loadFut;
    }


    /** {@inheritDoc} */
    @Override public CompletableFuture<List<BuildRef>> getRunningBuilds(String branch) {
        int defaultSecs = 60;
        int secondsUseCached = getTriggerRelCacheValidSecs(defaultSecs);

        return loadAsyncIfAbsentOrExpired(
            runningBuilds,
            Strings.nullToEmpty(branch),
            runningBuildsFuts,
            teamcity::getRunningBuilds,
            secondsUseCached,
            secondsUseCached == defaultSecs);
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
    @AutoProfiling
    @Override public ProblemOccurrences getProblems(Build build) {
        if (build.problemOccurrences != null) {
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
        else
            return new ProblemOccurrences();
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
    @AutoProfiling
    @Override public TestOccurrences getTests(String href, String normalizedBranch) {
        String hrefForDb = DbMigrations.removeCountFromRef(href);

        return loadIfAbsent(testOccurrencesCache(),
            hrefForDb,  //hack to avoid test reloading from store in case of href filter replaced
            hrefIgnored -> teamcity.getTests(href, normalizedBranch));
    }

    private void addTestOccurrencesToStat(TestOccurrences val) {
        for (TestOccurrence next : val.getTests())
            addTestOccurrenceToStat(next, ITeamcity.DEFAULT, null);
    }

    /** {@inheritDoc} */
    @AutoProfiling
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
    @AutoProfiling
    @Override public CompletableFuture<TestOccurrenceFull> getTestFull(String href) {
        return CacheUpdateUtil.loadAsyncIfAbsent(
            testFullCache(),
            href,
            testOccFullFutures,
            href1 -> {
                try {
                    return teamcity.getTestFull(href1);
                }
                catch (Exception e) {
                    if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                        System.err.println("TestOccurrenceFull not found for href : " + href);

                        return CompletableFuture.completedFuture(new TestOccurrenceFull());
                    }
                    throw e;
                }
            });
    }

    /** {@inheritDoc} */
    @AutoProfiling
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
    @AutoProfiling
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

    @AutoProfiling
    @Override public IssuesUsagesList getIssuesUsagesList(String href) {
        IssuesUsagesList issuesUsages =  loadIfAbsentV2(ISSUES_USAGES_LIST, href, href1 -> {
            try {
                return teamcity.getIssuesUsagesList(href1);
            }
            catch (Exception e) {
                if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                    System.err.println("Issues Usage List not found for href : " + href);

                    return new IssuesUsagesList();
                }
                else
                    throw e;
            }
        });
        return issuesUsages;
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

    private IgniteCache<Integer, Boolean> calculatedStatistic() {
        return getOrCreateCacheV2(ignCacheNme(CALCULATED_STATISTIC));
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

            val.addTestRunToLatest(testOccurrence, RunStat.ChangesState.UNKNOWN);

            entry.setValue(val);

            return null;
        }, next);
    }

    /** {@inheritDoc} */
    @Override
    public List<RunStat> topFailingSuite(int cnt) {
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
    @Override
    public String getThreadDumpCached(Integer buildId) {
        IgniteCache<Integer, LogCheckResult> entries = logCheckResultCache();

        LogCheckResult logCheckResult = entries.get(buildId);

        if (logCheckResult == null)
            return null;

        int fields = ObjectInterner.internFields(logCheckResult);

        return logCheckResult.getLastThreadDump();
    }

    /** {@inheritDoc} */
    @Override public void calculateBuildStatistic(SingleBuildRunCtx ctx) {
        if (calculatedStatistic().containsKey(ctx.buildId()))
            return;

        for (TestOccurrence testOccurrence : ctx.getTests()) {
            addTestOccurrenceToStat(testOccurrence, normalizeBranch(ctx.getBuild()), !ctx.getChanges().isEmpty());
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
    @AutoProfiling
    @Override public Build triggerBuild(String id, String name, boolean cleanRebuild, boolean queueAtTop) {
        lastTriggerMs = System.currentTimeMillis();

        return teamcity.triggerBuild(id, name, cleanRebuild, queueAtTop);
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
    @Override public void setGitToken(String tok) {
        teamcity.setGitToken(tok);
    }

    /** {@inheritDoc} */
    @Override public boolean isGitTokenAvailable() {
        return teamcity.isGitTokenAvailable();
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
    @Override public boolean sendJiraComment(String ticket, String comment) {
        return teamcity.sendJiraComment(ticket, comment);
    }

    /** {@inheritDoc} */
    @Override public PullRequest getPullRequest(String branchForTc) {
        return teamcity.getPullRequest(branchForTc);
    }

    /** {@inheritDoc} */
    @Override public boolean notifyGit(String url, String body) {
        return teamcity.notifyGit(url, body);
    }

    /** {@inheritDoc} */
    @Override public List<Agent> agents(boolean connected, boolean authorized) {
        return teamcity.agents(connected, authorized);
    }
}
