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
import java.io.IOException;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Inject;
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
import org.apache.ignite.ci.di.cache.GuavaCached;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.Project;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildTypeFull;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.mute.MuteInfo;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.util.ObjectInterner;
import org.apache.ignite.ci.web.model.hist.VisasHistoryStorage;
import org.jetbrains.annotations.NotNull;

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
    private static final String LOG_CHECK_RESULT = "logCheckResult";

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

    //todo: remove triggering dependency from getTrackedBranch processing, use TC Bot DB data.
    @Deprecated
    private static long lastTriggerMs = System.currentTimeMillis();

    @Override public void init(ITeamcity conn) {
        this.teamcity = conn;
        this.serverId = conn.serverId();

        DbMigrations migrations = new DbMigrations(ignite, conn.serverId());

        migrations.dataMigration(
                testRunStatCache(),
                visasHistStorage.visas());
    }

    /** {@inheritDoc} */
    @Override public void init(String srvId) {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override public User getUserByUsername(String username) {
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

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildType> getBuildTypes(String projectId) {
        return teamcity.getBuildTypes(projectId);
    }

    /** {@inheritDoc} */
    @Override public String serverId() {
        return serverId;
    }

    public static int getTriggerRelCacheValidSecs(int defaultSecs) {
        long msSinceTrigger = System.currentTimeMillis() - lastTriggerMs;
        long secondsSinceTrigger = TimeUnit.MILLISECONDS.toSeconds(msSinceTrigger);
        return Math.min((int) secondsSinceTrigger, defaultSecs);
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

    @Deprecated
    private IgniteCache<TestInBranch, RunStat> testRunStatCache() {
        return getOrCreateCacheV2(ignCacheNme(TESTS_RUN_STAT));
    }

    private IgniteCache<Integer, LogCheckResult> logCheckResultCache() {
        return getOrCreateCacheV2(ignCacheNme(LOG_CHECK_RESULT));
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

    /** {@inheritDoc} */
    @Override public ProblemOccurrences getProblems(int buildId) {
        return teamcity.getProblems(buildId);
    }

    /** {@inheritDoc} */
    @Override public List<Project> getProjects() {
        return teamcity.getProjects();
    }

    /** {@inheritDoc} */
    @Override public String gitBranchPrefix() {
        return teamcity.gitBranchPrefix();
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
    @Override public List<Agent> agents(boolean connected, boolean authorized) {
        return teamcity.agents(connected, authorized);
    }

    /** {@inheritDoc} */
    @Override public List<BuildRef> getBuildRefsPage(String fullUrl, AtomicReference<String> nextPage) {
        return teamcity.getBuildRefsPage(fullUrl, nextPage);
    }

    /** {@inheritDoc} */
    @Override public SortedSet<MuteInfo> getMutesPage(String buildTypeId, String fullUrl, AtomicReference<String> nextPage) {
        return teamcity.getMutesPage(buildTypeId, fullUrl, nextPage);
    }

    /** {@inheritDoc} */
    @Override public TestOccurrencesFull getTestsPage(int buildId, String href, boolean testDtls) {
        return teamcity.getTestsPage(buildId, href, testDtls);
    }
}
