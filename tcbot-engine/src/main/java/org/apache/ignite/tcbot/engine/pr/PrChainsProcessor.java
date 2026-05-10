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
package org.apache.ignite.tcbot.engine.pr;

import com.google.common.base.Strings;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.RunningInfoCompacted;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.githubignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.githubservice.IGitHubConnection;
import org.apache.ignite.jiraignited.IJiraIgnited;
import org.apache.ignite.jiraignited.IJiraIgnitedProvider;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.chain.FullChainRunCtx;
import org.apache.ignite.tcbot.engine.chain.LatestRebuildMode;
import org.apache.ignite.tcbot.engine.chain.MultBuildRunCtx;
import org.apache.ignite.tcbot.engine.chain.ProcessLogsMode;
import org.apache.ignite.tcbot.engine.build.AiPromptRequestMonitor;
import org.apache.ignite.tcbot.engine.build.TestFailuresAiPromptBuilder;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedChain;
import org.apache.ignite.tcbot.engine.newtests.NewTestsStorage;
import org.apache.ignite.tcbot.engine.pool.TcUpdatePool;
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
import org.apache.ignite.tcbot.engine.testfixes.TestFixesService;
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
import org.apache.ignite.tcbot.engine.ui.DsSuiteUi;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteUi;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteNewTestsUi;
import org.apache.ignite.tcbot.engine.ui.ShortTestFailureUi;
import org.apache.ignite.tcbot.engine.ui.ShortTestUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.build.UpdateCountersStorage;
import org.apache.ignite.tcignited.buildref.BranchEquivalence;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcservice.ITeamcity;

/**
 * Process pull request/untracked branch chain at particular server.
 */
public class PrChainsProcessor {
    /** Max time to wait for fresh AI prompt build context. */
    private static final long AI_PROMPT_CONTEXT_WAIT_MS = TimeUnit.MINUTES.toMillis(1);

    /** Max time to wait for AI prompt build log processing. */
    private static final long AI_PROMPT_LOG_WAIT_MS = TimeUnit.MINUTES.toMillis(1);

    /** */
    private static final ThreadLocal<DateFormat> THREAD_TIME_FORMATTER = new ThreadLocal<DateFormat>() {
        @Override protected DateFormat initialValue() {
            return new SimpleDateFormat("HH:mm");
        }
    };

    private static class Action {
        public static final String HISTORY = "History";
        public static final String LATEST = "Latest";
        public static final String CHAIN = "Chain";
    }

    /** Build chain processor. */
    @Inject private BuildChainProcessor buildChainProcessor;

    /** Tc server provider. */
    @Inject private ITeamcityIgnitedProvider tcIgnitedProvider;

    /** */
    @Inject private IGitHubConnIgnitedProvider gitHubConnIgnitedProvider;

    /** */
    @Inject private IJiraIgnitedProvider jiraIgnProv;

    @Inject private BranchTicketMatcher ticketMatcher;

    @Inject private IStringCompactor compactor;

    /** Config. */
    @Inject private ITcBotConfig cfg;

    @Inject private BranchEquivalence branchEquivalence;

    @Inject private UpdateCountersStorage countersStorage;

    @Inject private NewTestsStorage newTestsStorage;

    /** AI prompt request monitor. */
    @Inject private AiPromptRequestMonitor aiPromptMonitor;

    /** TC update pool for best-effort AI prompt refreshes. */
    @Inject private TcUpdatePool tcUpdatePool;

    /** User-visible process monitor. */
    @Inject private BotProcessMonitor processMonitor;

    /** Test fix matcher. */
    @Inject private TestFixesService testFixesService;

    /**
     * @param creds Credentials.
     * @param srvCodeOrAlias Server code or alias.
     * @param suiteId Suite id.
     * @param branchForTc Branch name in TC identification.
     * @param act Action.
     * @param cnt Count.
    * @param tcBaseBranchParm Base branch name in TC identification.
     * @param checkAllLogs Check all logs
     * @param mode TC Server Sync Mode
     * @return Test failures summary.
     */
    @AutoProfiling
    public DsSummaryUi getTestFailuresSummary(
        ICredentialsProv creds,
        String srvCodeOrAlias,
        String suiteId,
        String branchForTc,
        String act,
        Integer cnt,
        @Nullable String tcBaseBranchParm,
        @Nullable Boolean checkAllLogs,
        SyncMode mode) {
        final DsSummaryUi res = new DsSummaryUi();

        ITeamcityIgnited tcIgnited = tcIgnitedProvider.server(srvCodeOrAlias, creds);

        IGitHubConnIgnited gitHubConnIgnited = gitHubConnIgnitedProvider.server(srvCodeOrAlias);

        IJiraIgnited jiraIntegration = jiraIgnProv.server(srvCodeOrAlias);

        res.setJavaFlags(gitHubConnIgnited.config(), jiraIntegration.config());

        LatestRebuildMode rebuild;
        if (Action.HISTORY.equals(act))
            rebuild = LatestRebuildMode.ALL;
        else if (Action.LATEST.equals(act))
            rebuild = LatestRebuildMode.LATEST;
        else if (Action.CHAIN.equals(act))
            rebuild = LatestRebuildMode.NONE;
        else
            rebuild = LatestRebuildMode.LATEST;

        int buildResMergeCnt;
        if (rebuild == LatestRebuildMode.ALL)
            buildResMergeCnt = cnt == null ? 10 : cnt;
        else
            buildResMergeCnt = 1;

        ProcessLogsMode logs;
        if (buildResMergeCnt > 1)
            logs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.DISABLED;
        else
            logs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE;

        List<Integer> hist = tcIgnited.getLastNBuildsFromHistory(suiteId, branchForTc, buildResMergeCnt);

        String baseBranchForTc = Strings.isNullOrEmpty(tcBaseBranchParm) ? dfltBaseTcBranch(srvCodeOrAlias) : tcBaseBranchParm;

        FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(
            tcIgnited,
            hist,
            rebuild,
            logs,
            buildResMergeCnt == 1,
            baseBranchForTc,
            mode,
            null, null);

        DsChainUi chainStatus = new DsChainUi(srvCodeOrAlias, tcIgnited.serverCode(), branchForTc);

        chainStatus.baseBranchForTc = baseBranchForTc;
        chainStatus.suiteId = suiteId;

        if (ctx.isFakeStub()) {
            if (!initInProgressChainStatus(chainStatus, tcIgnited, suiteId, branchForTc, mode))
                chainStatus.setBuildNotFound(true);
        }
        else {
            //fail rate reference is always default (master)
            chainStatus.initFromContext(tcIgnited, ctx, baseBranchForTc, compactor, false,
                    null, null, -1, null, false, false); // don't need for PR
            chainStatus.suites.forEach(testFixesService::decorate);
            chainStatus.findNewTests(ctx, tcIgnited, baseBranchForTc, compactor, newTestsStorage);
            initJiraAndGitInfo(chainStatus, jiraIntegration, gitHubConnIgnited);
        }

        res.addChainOnServer(chainStatus);

        res.initCounters(getPrUpdateCounters(srvCodeOrAlias, branchForTc, baseBranchForTc, creds));

        return res;
    }

    /**
     * Initializes minimal UI when the requested PR run exists in TeamCity but has not produced finished results yet.
     *
     * @param chainStatus Chain UI.
     * @param tcIgnited TeamCity facade.
     * @param suiteId Suite id.
     * @param branchForTc Branch name.
     * @param mode Refresh mode.
     */
    private boolean initInProgressChainStatus(DsChainUi chainStatus, ITeamcityIgnited tcIgnited, String suiteId,
        String branchForTc, SyncMode mode) {
        List<BuildRefCompacted> liveBuilds = tcIgnited.getAllBuildsCompacted(suiteId, branchForTc)
            .stream()
            .filter(ref -> ref.isNotCancelled(compactor))
            .filter(ref -> ref.isQueued(compactor) || ref.isRunning(compactor))
            .collect(Collectors.toList());

        if (liveBuilds.isEmpty())
            return false;

        chainStatus.buildInProgress = true;
        chainStatus.suiteId = suiteId;
        chainStatus.webToHist = DsSuiteUi.buildWebLinkToHist(tcIgnited, suiteId, branchForTc);

        BuildTypeCompacted buildType = tcIgnited.getBuildType(suiteId);

        if (buildType != null)
            chainStatus.chainName = buildType.name(compactor);

        BuildRefCompacted mainBuild = liveBuilds.stream()
            .filter(ref -> ref.isRunning(compactor))
            .findFirst()
            .orElse(liveBuilds.get(0));

        chainStatus.runningBuildId = mainBuild.getId();
        chainStatus.webToBuild = buildWebLinkToLiveBuild(tcIgnited, mainBuild);

        fillRunningProgress(chainStatus, tcIgnited, liveBuilds, mode);

        return true;
    }

    /**
     * @param tcIgnited TeamCity facade.
     * @param ref Build reference.
     */
    private String buildWebLinkToLiveBuild(ITeamcityIgnited tcIgnited, BuildRefCompacted ref) {
        if (ref.isQueued(compactor))
            return tcIgnited.host() + "viewQueued.html?itemId=" + ref.id();

        return tcIgnited.host() + "viewLog.html?buildId=" + ref.id();
    }

    /**
     * @param chainStatus Chain UI.
     * @param tcIgnited TeamCity facade.
     * @param liveBuilds Live builds.
     * @param mode Refresh mode.
     */
    private void fillRunningProgress(DsChainUi chainStatus, ITeamcityIgnited tcIgnited,
        List<BuildRefCompacted> liveBuilds, SyncMode mode) {
        int progressSum = 0;
        int progressCnt = 0;
        long maxEstimatedFinishTs = -1;
        long maxQueuedAgeMs = -1;
        String stage = null;
        boolean probablyHanging = false;

        for (BuildRefCompacted ref : liveBuilds) {
            if (ref.isQueued(compactor))
                chainStatus.queuedBuilds++;

            if (ref.isRunning(compactor))
                chainStatus.runningBuilds++;

            if (ref.getId() == null)
                continue;

            FatBuildCompacted build;

            try {
                build = tcIgnited.getFatBuild(ref.id(), mode);
            }
            catch (RuntimeException e) {
                continue;
            }

            if (build == null || build.isFakeStub())
                continue;

            if (ref.isQueued(compactor)) {
                long queuedAgeMs = queuedAgeMs(build);

                if (queuedAgeMs >= 0)
                    maxQueuedAgeMs = Math.max(maxQueuedAgeMs, queuedAgeMs);
            }

            RunningInfoCompacted runningInfo = build.runningInfo();

            if (runningInfo == null)
                continue;

            Integer percent = runningInfo.percentageComplete();

            if (percent != null) {
                progressSum += percent;
                progressCnt++;
            }

            Long estimatedFinishTs = estimatedFinishTs(build, runningInfo);

            if (estimatedFinishTs != null)
                maxEstimatedFinishTs = Math.max(maxEstimatedFinishTs, estimatedFinishTs);

            String stageText = runningInfo.currentStageText(compactor);

            if (!Strings.isNullOrEmpty(stageText) && Strings.isNullOrEmpty(stage))
                stage = shorten(stageText, 160);

            if (Boolean.TRUE.equals(runningInfo.probablyHanging()))
                probablyHanging = true;
        }

        chainStatus.runningProgress = runningProgressText(liveBuilds.size(), chainStatus.runningBuilds,
            chainStatus.queuedBuilds, progressCnt == 0 ? null : progressSum / progressCnt, stage, probablyHanging);

        if (maxEstimatedFinishTs >= 0)
            chainStatus.estimatedCompletion = estimatedCompletionText(maxEstimatedFinishTs,
                chainStatus.queuedBuilds, maxQueuedAgeMs);
        else if (chainStatus.runningBuilds > 0)
            chainStatus.estimatedCompletion = chainStatus.queuedBuilds > 0 ?
                "TeamCity running estimate unavailable; " +
                    queuedBuildsText(chainStatus.queuedBuilds, maxQueuedAgeMs) :
                "TeamCity estimate unavailable";
        else if (chainStatus.queuedBuilds > 0)
            chainStatus.estimatedCompletion = queuedBuildsText(chainStatus.queuedBuilds, maxQueuedAgeMs);
    }

    /**
     * @param total Total live builds.
     * @param running Running builds.
     * @param queued Queued builds.
     * @param percent Average TeamCity percentage.
     * @param stage Current TeamCity stage.
     * @param probablyHanging Whether TeamCity suspects hang.
     */
    private static String runningProgressText(int total, int running, int queued, @Nullable Integer percent,
        @Nullable String stage, boolean probablyHanging) {
        List<String> parts = new ArrayList<>();

        if (running > 0)
            parts.add(running + "/" + total + " builds running");

        if (queued > 0)
            parts.add(queued + " queued");

        if (percent != null)
            parts.add("about " + percent + "%");

        if (probablyHanging)
            parts.add("TeamCity suspects hanging build");

        String res = String.join(", ", parts);

        return Strings.isNullOrEmpty(stage) ? res : res + ": " + stage;
    }

    /**
     * @param build Build.
     * @param runningInfo TeamCity running info.
     */
    @Nullable private static Long estimatedFinishTs(FatBuildCompacted build, RunningInfoCompacted runningInfo) {
        long startTs = build.getStartDateTs();

        if (startTs <= 0)
            return null;

        Long estimatedTotalSeconds = runningInfo.estimatedTotalSeconds();

        if (estimatedTotalSeconds != null && estimatedTotalSeconds >= 0)
            return startTs + TimeUnit.SECONDS.toMillis(estimatedTotalSeconds);

        Long elapsedSeconds = runningInfo.elapsedSeconds();
        Long leftSeconds = runningInfo.leftSeconds();

        if (elapsedSeconds != null && elapsedSeconds >= 0 && leftSeconds != null && leftSeconds >= 0)
            return startTs + TimeUnit.SECONDS.toMillis(elapsedSeconds + leftSeconds);

        return null;
    }

    /**
     * @param estimatedFinishTs TeamCity estimated finish timestamp.
     * @param queued Queued builds count.
     * @param queuedAgeMs Max queued build age.
     */
    private static String estimatedCompletionText(long estimatedFinishTs, int queued, long queuedAgeMs) {
        long leftMs = estimatedFinishTs - System.currentTimeMillis();
        String moment = estimateMoment(estimatedFinishTs);
        String eta = leftMs <= 0
            ? "last TeamCity running estimate was " + moment + " (already past)"
            : (queued > 0 ? "at least " : "") + hoursMinutes(leftMs) + " left (" + moment + ")";

        return queued > 0 ? eta + "; " + queuedBuildsText(queued, queuedAgeMs) : eta;
    }

    /**
     * @param ts Timestamp.
     */
    private static String estimateMoment(long ts) {
        return "around " + THREAD_TIME_FORMATTER.get().format(new Date(ts));
    }

    /**
     * @param queued Queued builds count.
     * @param queuedAgeMs Max queued build age.
     */
    private static String queuedBuildsText(int queued, long queuedAgeMs) {
        String builds = queued + " queued " + (queued == 1 ? "build" : "builds") + " not started";

        return queuedAgeMs >= 0 ? builds + " for " + durationText(queuedAgeMs) + ", start estimate unavailable" :
            builds + ", start estimate unavailable";
    }

    /**
     * @param ms Duration in millis.
     */
    private static String hoursMinutes(long ms) {
        return "in " + durationText(ms);
    }

    /**
     * @param ms Duration in millis.
     */
    private static String durationText(long ms) {
        long totalMins = Math.max(1, TimeUnit.MILLISECONDS.toMinutes(ms));
        long hours = totalMins / 60;
        long mins = totalMins % 60;

        if (hours == 0)
            return mins + "m";

        if (mins == 0)
            return hours + "h";

        return hours + "h " + mins + "m";
    }

    /**
     * @param build Build.
     */
    private static long queuedAgeMs(FatBuildCompacted build) {
        long queuedTs = build.getQueuedDateTs();

        return queuedTs > 0 ? Math.max(0, System.currentTimeMillis() - queuedTs) : -1;
    }

    /**
     * @param text Text to shorten.
     * @param limit Max chars.
     */
    private static String shorten(String text, int limit) {
        if (text == null || text.length() <= limit)
            return text;

        return text.substring(0, Math.max(0, limit - 3)) + "...";
    }

    /**
     * Gets deafault TC identified base (reference) branch
     * @param srvCodeOrAlias TC service code or aliad
     */
    public String dfltBaseTcBranch(String srvCodeOrAlias) {
        ITcServerConfig tcCfg = cfg.getTeamcityConfig(srvCodeOrAlias);
        String dfltTrackedBranch = tcCfg.defaultTrackedBranch();

        String tcRealSvc = tcCfg.reference();

        Optional<ITrackedBranch> branch = cfg.getTrackedBranches().get(dfltTrackedBranch);

        if (!branch.isPresent())
            return ITeamcity.DEFAULT;

        Predicate<ITrackedChain> relatedToTcFilter = chain ->
            Objects.equals(chain.serverCode(), srvCodeOrAlias)
                || (!Strings.isNullOrEmpty(tcRealSvc) && Objects.equals(chain.serverCode(), tcRealSvc));

        Optional<ITrackedChain> chainAtSrv = branch.get().chainsStream().filter(relatedToTcFilter).findAny();

        if (!chainAtSrv.isPresent())
            return ITeamcity.DEFAULT;

        return chainAtSrv.get().tcBranch();
    }

    /**
     * Set up ticket and PR related information.
     *  @param chainStatus Ticket matcher.
     * @param jiraIntegration Jira integration.
     * @param gitHubConnIgnited Git hub connection ignited.
     */
    public void initJiraAndGitInfo(DsChainUi chainStatus,
                                   IJiraIgnited jiraIntegration,
                                   IGitHubConnIgnited gitHubConnIgnited) {

        String ticketFullName = null;
        String branchName = chainStatus.branchName;
        try {
            ticketFullName = ticketMatcher
                    .resolveTicketFromBranch(jiraIntegration.config().getCode(),
                            null,
                            branchName);
        }
        catch (BranchTicketMatcher.TicketNotFoundException ignore) {
        }

        Integer prNum = IGitHubConnection.convertBranchToPrId(branchName);

        String prUrl = null;
        String ticketUrl = null;

        if (prNum != null) {
            PullRequest pullReq = gitHubConnIgnited.getPullRequest(prNum);

            if (pullReq != null && pullReq.getTitle() != null)
                prUrl = pullReq.htmlUrl();
        }

        if (!Strings.isNullOrEmpty(ticketFullName) && jiraIntegration.config().getUrl() != null)
            ticketUrl = jiraIntegration.generateTicketUrl(ticketFullName);

        chainStatus.setPrInfo(prNum, prUrl);
        chainStatus.setJiraTicketInfo(ticketFullName, ticketUrl);
    }

    /**
     * @param buildTypeId  Build type ID, for which visa was ordered.
     * @param branchForTc Branch for TeamCity.
     * @param srvCodeOrAlias Server id.
     * @param prov Credentials.
     * @param syncMode
     * @param baseBranchForTc
     * @return List of suites with possible blockers.
     */
    @Nullable
    public List<ShortSuiteUi> getBlockersSuitesStatuses(
        String buildTypeId,
        String branchForTc,
        String srvCodeOrAlias,
        ICredentialsProv prov,
        SyncMode syncMode,
        @Nullable String baseBranchForTc) {
        ITeamcityIgnited tcIgnited = tcIgnitedProvider.server(srvCodeOrAlias, prov);

        List<Integer> hist = tcIgnited.getLastNBuildsFromHistory(buildTypeId, branchForTc, 1);

        String baseBranch = Strings.isNullOrEmpty(baseBranchForTc) ? dfltBaseTcBranch(srvCodeOrAlias) : baseBranchForTc;

        FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(
            tcIgnited,
            hist,
            LatestRebuildMode.LATEST,
            ProcessLogsMode.SUITE_NOT_COMPLETE,
            false,
            baseBranch,
            syncMode,
            null, null);

        if (ctx.isFakeStub())
            return null;

        return findBlockerFailures(ctx, tcIgnited, baseBranch);
    }

    /**
     * @param buildTypeId  Build type ID, for which visa was ordered.
     * @param branchForTc Branch for TeamCity.
     * @param srvCodeOrAlias Server id.
     * @param prov Credentials.
     * @param syncMode
     * @param baseBranchForTc
     * @return List of suites with possible blockers.
     */
    @Nullable
    public List<ShortSuiteNewTestsUi> getNewTestsSuitesStatuses(
        String buildTypeId,
        String branchForTc,
        String srvCodeOrAlias,
        ICredentialsProv prov,
        SyncMode syncMode,
        @Nullable String baseBranchForTc) {
        ITeamcityIgnited tcIgnited = tcIgnitedProvider.server(srvCodeOrAlias, prov);

        List<Integer> hist = tcIgnited.getLastNBuildsFromHistory(buildTypeId, branchForTc, 1);

        String baseBranch = Strings.isNullOrEmpty(baseBranchForTc) ? dfltBaseTcBranch(srvCodeOrAlias) : baseBranchForTc;

        FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(
            tcIgnited,
            hist,
            LatestRebuildMode.LATEST,
            ProcessLogsMode.SUITE_NOT_COMPLETE,
            false,
            baseBranch,
            syncMode,
            null, null);

        if (ctx.isFakeStub())
            return null;

        return findNewTests(ctx, tcIgnited, baseBranch);
    }

    /**
     * @return Blocker failures for given server.
     * @param fullChainRunCtx
     * @param tcIgnited
     * @param baseBranch
     */
    private List<ShortSuiteUi> findBlockerFailures(FullChainRunCtx fullChainRunCtx,
        ITeamcityIgnited tcIgnited,
        String baseBranch) {
        String normalizedBaseBranch = BranchEquivalence.normalizeBranch(baseBranch);
        Integer baseBranchId = compactor.getStringIdIfPresent(normalizedBaseBranch);

        Predicate<MultBuildRunCtx> filter = suite ->
            suite.isFailed() || suite.hasTestToReport(tcIgnited, baseBranchId, false, false);

        List<ShortSuiteUi> suites = fullChainRunCtx
            .filteredChildSuites(filter)
            .map((ctx) -> {
                IRunHistory statInBaseBranch = ctx.history(tcIgnited, baseBranchId, null);

                String suiteComment = ctx.getPossibleBlockerComment(compactor, statInBaseBranch, tcIgnited.config());

                List<ShortTestFailureUi> failures = ctx.getFilteredTests(test -> test.includeIntoReport(tcIgnited, baseBranchId, false, false))
                    .stream()
                    .map(occurrence -> {
                        ShortTestFailureUi tst = new ShortTestFailureUi().initFrom(occurrence, tcIgnited, baseBranchId);

                        return tst.isPossibleBlocker() ? tst : null;
                    }).filter(Objects::nonNull).collect(Collectors.toList());


                // test failure based blockers and/or blocker found by suite results
                if (!failures.isEmpty() || !Strings.isNullOrEmpty(suiteComment)) {
                    ShortSuiteUi suite = new ShortSuiteUi()
                        .testShortFailures(failures)
                        .initFrom(ctx, tcIgnited, compactor, statInBaseBranch);

                    return suite;
                }

                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        testFixesService.decorate(suites);

        return suites;
    }

    /**
     * @return New tests for given server.
     * @param fullChainRunCtx
     * @param tcIgnited
     * @param baseBranch
     */
    private List<ShortSuiteNewTestsUi> findNewTests(FullChainRunCtx fullChainRunCtx,
        ITeamcityIgnited tcIgnited,
        String baseBranch) {
        String normalizedBaseBranch = BranchEquivalence.normalizeBranch(baseBranch);
        Integer baseBranchId = compactor.getStringIdIfPresent(normalizedBaseBranch);

        if (baseBranchId == null)
            return Collections.emptyList();

        return fullChainRunCtx
            .suites()
            .map((ctx) -> {
                IRunHistory suiteHistory = ctx.history(tcIgnited, baseBranchId, null);

                if (suiteHistory == null)
                    return null;

                List<ShortTestUi> missingTests = ctx.getFilteredTests(test -> {
                    IRunHistory history = test.history(tcIgnited, baseBranchId, null);
                    if (history == null && !test.isMutedOrIgored()) {

                        if (test.getId() != null &&
                            newTestsStorage.isNewTestAndPut(tcIgnited.serverCode(),
                                test.getId(), normalizedBaseBranch, ctx.branchName()))
                            return true;
                        else
                            return false;
                    }
                    else
                        return false;
                })
                    .stream()
                    .map(occurrence -> new ShortTestUi().initFrom(occurrence, occurrence.isPassed()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

                if (!missingTests.isEmpty()) {
                    return new ShortSuiteNewTestsUi()
                        .tests(missingTests)
                        .initFrom(ctx, tcIgnited);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public Map<Integer, Integer> getPrUpdateCounters(String srvCodeOrAlias, String branchForTc, String tcBaseBranchParm,
        ICredentialsProv creds) {
        String baseBranchForTc = Strings.isNullOrEmpty(tcBaseBranchParm) ? dfltBaseTcBranch(srvCodeOrAlias) : tcBaseBranchParm;

        Set<Integer> allRelatedBranchCodes = new HashSet<>();
        allRelatedBranchCodes.addAll(branchEquivalence.branchIdsForQuery(branchForTc, compactor));
        allRelatedBranchCodes.addAll(branchEquivalence.branchIdsForQuery(baseBranchForTc, compactor));

        return countersStorage.getCounters(allRelatedBranchCodes);
    }

    /**
     * @param creds Credentials.
     * @param srvCodeOrAlias Server code or alias.
     * @param suiteId Suite id.
     * @param branchForTc Branch name in TC identification.
     * @param act Action.
     * @param cnt Count.
     * @param tcBaseBranchParm Base branch name in TC identification.
     * @param maxDetailsChars Max chars to include for every test details block. Non-positive means default cap.
     * @param testName Optional full test name filter.
     * @param promptSuiteId Optional suite id filter.
     * @param waitForTc Wait for fresh TeamCity context and build log processing.
     * @return AI prompt with PR failure context.
     */
    @AutoProfiling
    public String getPrFailuresAiPrompt(
        ICredentialsProv creds,
        String srvCodeOrAlias,
        String suiteId,
        String branchForTc,
        String act,
        Integer cnt,
        @Nullable String tcBaseBranchParm,
        int maxDetailsChars,
        @Nullable String testName,
        @Nullable String promptSuiteId,
        boolean waitForTc) {
        return getPrFailuresAiPrompt(creds, srvCodeOrAlias, suiteId, branchForTc, act, cnt, tcBaseBranchParm,
            maxDetailsChars, testName, promptSuiteId, waitForTc, null);
    }

    /**
     * @param processId User-visible process id.
     */
    @AutoProfiling
    public String getPrFailuresAiPrompt(
        ICredentialsProv creds,
        String srvCodeOrAlias,
        String suiteId,
        String branchForTc,
        String act,
        Integer cnt,
        @Nullable String tcBaseBranchParm,
        int maxDetailsChars,
        @Nullable String testName,
        @Nullable String promptSuiteId,
        boolean waitForTc,
        @Nullable Long processId) {
        long reqId = aiPromptMonitor.start("pr", branchForTc, srvCodeOrAlias, suiteId, testName);
        processMonitor.start(processId, "aiPrompt", "Preparing AI prompt generation.");

        try {
            ITeamcityIgnited tcIgnited = tcIgnitedProvider.server(srvCodeOrAlias, creds);

            LatestRebuildMode rebuild;
            if (Action.HISTORY.equals(act))
                rebuild = LatestRebuildMode.ALL;
            else if (Action.CHAIN.equals(act))
                rebuild = LatestRebuildMode.NONE;
            else
                rebuild = LatestRebuildMode.LATEST;

            int buildResMergeCnt = rebuild == LatestRebuildMode.ALL ? cnt == null ? 10 : cnt : 1;

            promptStatus(processId, reqId, "Loading build history for the prompt.");

            List<Integer> hist = tcIgnited.getLastNBuildsFromHistory(suiteId, branchForTc, buildResMergeCnt);

            String baseBranchForTc = Strings.isNullOrEmpty(tcBaseBranchParm)
                ? dfltBaseTcBranch(srvCodeOrAlias)
                : tcBaseBranchParm;

            promptStatus(processId, reqId, "Collecting build and test details for the prompt.");

            FullChainRunCtx ctx = loadAiPromptContextBestEffort(reqId, tcIgnited, hist, rebuild,
                buildResMergeCnt == 1, baseBranchForTc, srvCodeOrAlias + "/" + suiteId, waitForTc, processId);

            if (waitForTc)
                waitForAiPromptLogs(reqId, ctx, srvCodeOrAlias + "/" + suiteId, processId);

            promptStatus(processId, reqId, "Assembling the final prompt text.");

            String res = new TestFailuresAiPromptBuilder(compactor)
                .buildPrompt(tcIgnited, ctx, baseBranchForTc,
                    TestFailuresAiPromptBuilder.restMaxDetailsChars(maxDetailsChars), testName, promptSuiteId);

            aiPromptMonitor.finish(reqId, "chars=" + res.length());
            processMonitor.finish(processId, "Prompt text is ready.");

            return res;
        }
        catch (RuntimeException e) {
            aiPromptMonitor.fail(reqId, e);
            processMonitor.fail(processId, e);

            throw e;
        }
    }

    /**
     * @param reqId Monitor request id.
     * @param tcIgnited TeamCity facade.
     * @param hist Entry builds.
     * @param rebuild Rebuild mode.
     * @param includeScheduledInfo Include scheduled info.
     * @param baseBranchForTc Base branch.
     * @param stageSuffix Stage suffix.
     * @param waitForTc Wait for fresh TeamCity context and build log processing.
     */
    private FullChainRunCtx loadAiPromptContextBestEffort(
        long reqId,
        ITeamcityIgnited tcIgnited,
        List<Integer> hist,
        LatestRebuildMode rebuild,
        boolean includeScheduledInfo,
        String baseBranchForTc,
        String stageSuffix,
        boolean waitForTc,
        @Nullable Long processId) {
        if (!waitForTc) {
            promptStatus(processId, reqId, "Using cached build and test details for the prompt.");

            return buildChainProcessor.loadFullChainContext(
                tcIgnited,
                hist,
                LatestRebuildMode.NONE,
                ProcessLogsMode.CACHED_ONLY,
                false,
                baseBranchForTc,
                SyncMode.NONE,
                null, null);
        }

        Future<FullChainRunCtx> live = null;

        try {
            promptStatus(processId, reqId, "Requesting fresh TeamCity data for the prompt.");

            live = tcUpdatePool.getService().submit(() -> buildChainProcessor.loadFullChainContext(
                tcIgnited,
                hist,
                rebuild,
                ProcessLogsMode.ALL,
                includeScheduledInfo,
                baseBranchForTc,
                SyncMode.RELOAD_QUEUED,
                null, null));

            return live.get(AI_PROMPT_CONTEXT_WAIT_MS, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e) {
            if (live != null)
                live.cancel(true);

            promptStatus(processId, reqId, "TeamCity refresh timed out; using cached build context.");
        }
        catch (InterruptedException e) {
            if (live != null)
                live.cancel(true);

            Thread.currentThread().interrupt();

            promptStatus(processId, reqId, "TeamCity refresh was interrupted.");

            throw new IllegalStateException("Interrupted while loading fresh TeamCity context: " + stageSuffix, e);
        }
        catch (Exception e) {
            promptStatus(processId, reqId, "TeamCity refresh failed; using cached build context.");
        }

        return buildChainProcessor.loadFullChainContext(
            tcIgnited,
            hist,
            LatestRebuildMode.NONE,
            ProcessLogsMode.CACHED_ONLY,
            false,
            baseBranchForTc,
            SyncMode.NONE,
            null, null);
    }

    /**
     * @param reqId AI prompt request id.
     * @param ctx Chain context.
     * @param stageSuffix Stage suffix.
     */
    private void waitForAiPromptLogs(long reqId, FullChainRunCtx ctx, String stageSuffix, @Nullable Long processId) {
        long started = ctx.logChecksStartedCount();

        if (started == 0)
            return;

        promptStatus(processId, reqId, "Analyzing build logs for the prompt.");

        ctx.awaitLogChecks(AI_PROMPT_LOG_WAIT_MS);

        long pending = ctx.pendingLogChecksCount();

        if (pending > 0)
            promptStatus(processId, reqId, "Build log analysis timed out; using available log context.");
        else
            promptStatus(processId, reqId, "Build log analysis finished.");
    }

    /**
     * @param processId User-visible process id.
     * @param reqId AI prompt monitor request id.
     * @param stage Shared process stage.
     * @param text Detailed AI prompt stage text.
     */
    private void promptStatus(@Nullable Long processId, long reqId, String text) {
        aiPromptMonitor.stage(reqId, text);
        processMonitor.status(processId, text);
    }
}
