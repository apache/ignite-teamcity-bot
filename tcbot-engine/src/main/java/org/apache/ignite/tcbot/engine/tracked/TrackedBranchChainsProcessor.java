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
package org.apache.ignite.tcbot.engine.tracked;

import com.google.common.base.Strings;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.tcbot.common.conf.IBuildParameterSpec;
import org.apache.ignite.tcbot.common.conf.IParameterValueSpec;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.chain.FullChainRunCtx;
import org.apache.ignite.tcbot.engine.chain.LatestRebuildMode;
import org.apache.ignite.tcbot.engine.chain.ProcessLogsMode;
import org.apache.ignite.tcbot.engine.chain.SortOption;
import org.apache.ignite.tcbot.engine.build.AiPromptRequestMonitor;
import org.apache.ignite.tcbot.engine.build.TestFailuresAiPromptBuilder;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedChain;
import org.apache.ignite.tcbot.engine.pool.TcUpdatePool;
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
import org.apache.ignite.tcbot.engine.testfixes.TestFixesService;
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.engine.ui.DsSuiteUi;
import org.apache.ignite.tcbot.engine.ui.GuardBranchStatusUi;
import org.apache.ignite.tcbot.engine.ui.LrTestsFullSummaryUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.build.UpdateCountersStorage;
import org.apache.ignite.tcignited.buildref.BranchEquivalence;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Process failures for some setup tracked branch, which may be triggered/monitored by TC Bot.
 */
public class TrackedBranchChainsProcessor implements IDetailedStatusForTrackedBranch {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TrackedBranchChainsProcessor.class);

    /** Slow tracked branch operation threshold. */
    private static final long SLOW_TRACKED_BRANCH_WARN_MS =
        Long.getLong("tcbot.tracked.slowOperationWarnMs", 1000L);

    /** Max time to wait for fresh AI prompt build context. */
    private static final long AI_PROMPT_CONTEXT_WAIT_MS = TimeUnit.MINUTES.toMillis(1);

    /** Max time to wait for AI prompt build log processing. */
    private static final long AI_PROMPT_LOG_WAIT_MS = TimeUnit.MINUTES.toMillis(1);

    /** TC ignited server provider. */
    @Inject private ITeamcityIgnitedProvider tcIgnitedProv;

    /** Tc Bot config. */
    @Inject private ITcBotConfig tcBotCfg;

    /** Chains processor. */
    @Inject private BuildChainProcessor chainProc;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    @Inject private BranchEquivalence branchEquivalence;

    /** Update Counters for branch-related changes storage. */
    @Inject private UpdateCountersStorage countersStorage;

    /** AI prompt monitor. */
    @Inject private AiPromptRequestMonitor aiPromptMonitor;

    /** TC update pool for best-effort AI prompt refreshes. */
    @Inject private TcUpdatePool tcUpdatePool;

    /** User-visible process monitor. */
    @Inject private BotProcessMonitor processMonitor;

    /** Test fix matcher. */
    @Inject private TestFixesService testFixesService;

    /**
     * @param branch Branch.
     * @param buildResMergeCnt Build results merge count.
     * @param creds Credentials.
     * @param syncMode Sync mode.
     * @param tagForHistSelected Selected tag for filtering history.
     * @param sortOption Sort mode.
     * @param maxDetailsChars Max chars to include for every test details block. Non-positive means default cap.
     * @param testName Full test name to include.
     * @param suiteId Suite id to include.
     * @param waitForTc Wait for fresh TeamCity context and build log processing.
     */
    @Nonnull public String getTrackedBranchFailuresAiPrompt(
        @Nullable String branch,
        int buildResMergeCnt,
        ICredentialsProv creds,
        SyncMode syncMode,
        @Nullable String tagForHistSelected,
        @Nullable SortOption sortOption,
        @Nullable Integer maxDetailsChars,
        @Nullable String testName,
        @Nullable String suiteId,
        boolean waitForTc) {
        return getTrackedBranchFailuresAiPrompt(branch, buildResMergeCnt, creds, syncMode, tagForHistSelected,
            sortOption, maxDetailsChars, testName, suiteId, waitForTc, null);
    }

    /**
     * @param processId User-visible process id.
     */
    @Nonnull public String getTrackedBranchFailuresAiPrompt(
        @Nullable String branch,
        int buildResMergeCnt,
        ICredentialsProv creds,
        SyncMode syncMode,
        @Nullable String tagForHistSelected,
        @Nullable SortOption sortOption,
        @Nullable Integer maxDetailsChars,
        @Nullable String testName,
        @Nullable String suiteId,
        boolean waitForTc,
        @Nullable Long processId) {
        long reqId = aiPromptMonitor.start("trackedBranch", branch, null, null, testName);
        processMonitor.start(processId, "aiPrompt", "Preparing AI prompt generation.");
        StringBuilder res = new StringBuilder();

        try {
            final String branchNn = isNullOrEmpty(branch) ? ITcServerConfig.DEFAULT_TRACKED_BRANCH_NAME : branch;
            final ITrackedBranch tracked = tcBotCfg.getTrackedBranches().getBranchMandatory(branchNn);
            final int maxDetails = TestFailuresAiPromptBuilder.restMaxDetailsChars(maxDetailsChars);

            tracked.chainsStream()
                .filter(chainTracked -> tcIgnitedProv.hasAccess(chainTracked.serverCode(), creds))
                .forEach(chainTracked -> {
                    String srvCodeOrAlias = chainTracked.serverCode();
                    String branchForTc = chainTracked.tcBranch();
                    String baseBranchTc = chainTracked.tcBaseBranch().orElse(branchForTc);
                    String suiteIdMandatory = chainTracked.tcSuiteId();

                    promptStatus(processId, reqId, "Loading build history for the prompt.");

                    ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, creds);

                    Map<Integer, Integer> requireParamVal = new HashMap<>();

                    if (!Strings.isNullOrEmpty(tagForHistSelected))
                        requireParamVal.putAll(reverseTagToParametersRequired(tagForHistSelected, srvCodeOrAlias));

                    List<Integer> chains = tcIgnited.getLastNBuildsFromHistory(suiteIdMandatory, branchForTc,
                        Math.max(buildResMergeCnt, 1));

                    LatestRebuildMode rebuild = buildResMergeCnt > 1 ? LatestRebuildMode.ALL : LatestRebuildMode.LATEST;

                    promptStatus(processId, reqId, "Collecting build and test details for the prompt.");

                    FullChainRunCtx ctx = loadAiPromptContextBestEffort(reqId, tcIgnited, chains, rebuild,
                        buildResMergeCnt == 1, baseBranchTc, syncMode, sortOption, requireParamVal,
                        srvCodeOrAlias + "/" + suiteIdMandatory, waitForTc, processId);

                    if (waitForTc)
                        waitForAiPromptLogs(reqId, ctx, srvCodeOrAlias + "/" + suiteIdMandatory, processId);

                    if (res.length() > 0)
                        res.append("\n\n");

                    promptStatus(processId, reqId, "Assembling the final prompt text.");

                    res.append(new TestFailuresAiPromptBuilder(compactor)
                        .buildPrompt(tcIgnited, ctx, baseBranchTc, maxDetails, testName, suiteId));
                });

            aiPromptMonitor.finish(reqId, "chars=" + res.length());
            processMonitor.finish(processId, "Prompt text is ready.");

            return res.toString();
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
     * @param chains Entry builds.
     * @param rebuild Rebuild mode.
     * @param includeScheduledInfo Include scheduled info.
     * @param baseBranchTc Base branch.
     * @param liveSyncMode Live sync mode.
     * @param sortOption Sort option.
     * @param requireParamVal Required parameter values.
     * @param stageSuffix Stage suffix.
     * @param waitForTc Wait for fresh TeamCity context and build log processing.
     */
    private FullChainRunCtx loadAiPromptContextBestEffort(
        long reqId,
        ITeamcityIgnited tcIgnited,
        List<Integer> chains,
        LatestRebuildMode rebuild,
        boolean includeScheduledInfo,
        String baseBranchTc,
        SyncMode liveSyncMode,
        @Nullable SortOption sortOption,
        @Nullable Map<Integer, Integer> requireParamVal,
        String stageSuffix,
        boolean waitForTc,
        @Nullable Long processId) {
        if (!waitForTc) {
            promptStatus(processId, reqId, "Using cached build and test details for the prompt.");

            return chainProc.loadFullChainContext(
                tcIgnited,
                chains,
                LatestRebuildMode.NONE,
                ProcessLogsMode.CACHED_ONLY,
                false,
                baseBranchTc,
                SyncMode.NONE,
                sortOption,
                requireParamVal
            );
        }

        Future<FullChainRunCtx> live = null;

        try {
            promptStatus(processId, reqId, "Requesting fresh TeamCity data for the prompt.");

            live = tcUpdatePool.getService().submit(() -> chainProc.loadFullChainContext(
                tcIgnited,
                chains,
                rebuild,
                ProcessLogsMode.ALL,
                includeScheduledInfo,
                baseBranchTc,
                liveSyncMode,
                sortOption,
                requireParamVal
            ));

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

        return chainProc.loadFullChainContext(
            tcIgnited,
            chains,
            LatestRebuildMode.NONE,
            ProcessLogsMode.CACHED_ONLY,
            false,
            baseBranchTc,
            SyncMode.NONE,
            sortOption,
            requireParamVal
        );
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

    /** {@inheritDoc} */
    @AutoProfiling
    @Nonnull
    @Override public DsSummaryUi getTrackedBranchTestFailures(
        @Nullable String branch,
        @Nullable Boolean checkAllLogs,
        int buildResMergeCnt,
        ICredentialsProv creds,
        SyncMode syncMode,
        boolean calcTrustedTests,
        @Nullable String tagSelected,
        @Nullable String tagForHistSelected,
        @Nullable String suiteId,
        @Nullable DisplayMode displayMode,
        @Nullable SortOption sortOption,
        int maxDurationSec,
        boolean showMuted,
        boolean showIgnored) {
        long startNanos = System.nanoTime();
        long chainTotalNanos = 0;
        long serverResolveNanos = 0;
        long tagFilterNanos = 0;
        long historyNanos = 0;
        long chainContextNanos = 0;
        long uiInitNanos = 0;
        long countersNanos;

        final DsSummaryUi res = new DsSummaryUi();

        final String branchNn = isNullOrEmpty(branch) ? ITcServerConfig.DEFAULT_TRACKED_BRANCH_NAME : branch;
        res.setTrackedBranch(branchNn);

        final ITrackedBranch tracked = tcBotCfg.getTrackedBranches().getBranchMandatory(branchNn);

        List<ITrackedChain> accessibleChains = tracked.chainsStream()
            .filter(chainTracked -> tcIgnitedProv.hasAccess(chainTracked.serverCode(), creds))
            .collect(Collectors.toList());

        for (ITrackedChain chainTracked : accessibleChains) {
            long chainStart = System.nanoTime();
            final String srvCodeOrAlias = chainTracked.serverCode();

            final String branchForTc = chainTracked.tcBranch();

            //branch is tracked, so fail rate should be taken from this branch data (otherwise it is specified).
            final String baseBranchTc = chainTracked.tcBaseBranch().orElse(branchForTc);

            long stepStart = System.nanoTime();
            ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, creds);
            serverResolveNanos += System.nanoTime() - stepStart;

            Map<Integer, Integer> requireParamVal = new HashMap<>();

            if (!Strings.isNullOrEmpty(tagForHistSelected)) {
                stepStart = System.nanoTime();
                requireParamVal.putAll(
                    reverseTagToParametersRequired(tagForHistSelected, srvCodeOrAlias));
                tagFilterNanos += System.nanoTime() - stepStart;
            }

            DsChainUi chainStatus = new DsChainUi(srvCodeOrAlias,
                tcIgnited.serverCode(),
                branchForTc);

            chainStatus.baseBranchForTc = baseBranchTc;

            String suiteIdMandatory = chainTracked.tcSuiteId();

            stepStart = System.nanoTime();
            List<Integer> chains = tcIgnited.getLastNBuildsFromHistory(suiteIdMandatory, branchForTc, buildResMergeCnt);
            historyNanos += System.nanoTime() - stepStart;

            ProcessLogsMode logs;
            if (buildResMergeCnt > 1)
                logs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.DISABLED;
            else
                logs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE;

            LatestRebuildMode rebuild = buildResMergeCnt > 1 ? LatestRebuildMode.ALL : LatestRebuildMode.LATEST;

            boolean includeScheduled = buildResMergeCnt == 1;

            stepStart = System.nanoTime();
            final FullChainRunCtx ctx = chainProc.loadFullChainContext(
                tcIgnited,
                chains,
                rebuild,
                logs,
                includeScheduled,
                baseBranchTc,
                syncMode,
                sortOption,
                requireParamVal
            );
            chainContextNanos += System.nanoTime() - stepStart;

            stepStart = System.nanoTime();
            chainStatus.initFromContext(tcIgnited, ctx, baseBranchTc, compactor, calcTrustedTests, tagSelected,
                displayMode, maxDurationSec, requireParamVal,
                showMuted, showIgnored);
            filterSuites(chainStatus, suiteId);
            chainStatus.suites.forEach(testFixesService::decorate);
            uiInitNanos += System.nanoTime() - stepStart;

            if (Strings.isNullOrEmpty(suiteId) || suiteId.equals(chainStatus.suiteId) || !chainStatus.suites.isEmpty())
                res.addChainOnServer(chainStatus);

            chainTotalNanos += System.nanoTime() - chainStart;
        }

        res.servers.sort(Comparator.comparing(DsChainUi::serverName));

        long stepStart = System.nanoTime();
        res.initCounters(getTrackedBranchUpdateCounters(branch, creds));
        countersNanos = System.nanoTime() - stepStart;

        long totalMs = nanosToMillis(System.nanoTime() - startNanos);

        if (totalMs >= SLOW_TRACKED_BRANCH_WARN_MS) {
            logger.warn("Slow tracked branch budget: branch={}, chains={}, count={}, syncMode={}, totalMs={}, " +
                    "chainTotalMs={}, serverResolveMs={}, tagFilterMs={}, historyMs={}, chainContextMs={}, " +
                    "uiInitMs={}, countersMs={}",
                branchNn, accessibleChains.size(), buildResMergeCnt, syncMode, totalMs,
                nanosToMillis(chainTotalNanos), nanosToMillis(serverResolveNanos), nanosToMillis(tagFilterNanos),
                nanosToMillis(historyNanos), nanosToMillis(chainContextNanos), nanosToMillis(uiInitNanos),
                nanosToMillis(countersNanos));
        }

        return res;
    }

    /**
     * Keeps a requested child suite inside the loaded root tracked chain.
     *
     * @param chainStatus Chain UI.
     * @param suiteId Optional requested suite id.
     */
    private static void filterSuites(DsChainUi chainStatus, @Nullable String suiteId) {
        if (chainStatus == null || Strings.isNullOrEmpty(suiteId) || suiteId.equals(chainStatus.suiteId))
            return;

        chainStatus.suites = chainStatus.suites.stream()
            .filter(suite -> suiteId.equals(suite.suiteId))
            .collect(Collectors.toList());

        chainStatus.failedTests = sum(chainStatus.suites.stream().map(suite -> suite.failedTests)
            .collect(Collectors.toList()));
        chainStatus.totalTests = sum(chainStatus.suites.stream().map(suite -> suite.totalTests)
            .collect(Collectors.toList()));
        chainStatus.trustedTests = sum(chainStatus.suites.stream().map(suite -> suite.trustedTests)
            .collect(Collectors.toList()));
        chainStatus.totalBlockers = chainStatus.suites.stream().mapToInt(DsSuiteUi::totalBlockers).sum();
    }

    /**
     * @param vals Values.
     */
    private static Integer sum(Collection<Integer> vals) {
        int res = 0;
        boolean found = false;

        for (Integer val : vals) {
            if (val == null)
                continue;

            res += val;
            found = true;
        }

        return found ? res : null;
    }

    /**
     * @param nanos Nanoseconds.
     */
    private static long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    public Map<Integer, Integer> reverseTagToParametersRequired(@Nullable String tagForHistSelected,
        String srvCodeOrAlias) {

        Map<Integer, Integer> requireParamVal = new HashMap<>();

        ITcServerConfig cfg = tcBotCfg.getTeamcityConfig(srvCodeOrAlias);
        Collection<? extends IBuildParameterSpec> specs = cfg.filteringParameters();
        for (IBuildParameterSpec buildParameterSpec : specs) {
            Collection<? extends IParameterValueSpec> selection = buildParameterSpec.selection();
            for (IParameterValueSpec valueSpec : selection) {
                if(tagForHistSelected.equals(valueSpec.label())
                    && !Strings.isNullOrEmpty(valueSpec.value())) {

                    requireParamVal.put(
                        compactor.getStringId(buildParameterSpec.name()),
                        compactor.getStringId(valueSpec.value()));
                }
            }
        }

        return requireParamVal;
    }

    @Override public GuardBranchStatusUi getBranchSummary(String name, ICredentialsProv prov) {
        ITrackedBranch tb = tcBotCfg.getTrackedBranches().getBranchMandatory(name);
        List<ITrackedChain> accessibleChains =
            tb.chainsStream()
                .filter(chain -> tcIgnitedProv.hasAccess(chain.serverCode(), prov))
                .collect(Collectors.toList());

        if (accessibleChains == null)
            return null;

        int ageDays = 1;
        long minStartTime = System.currentTimeMillis() - Duration.ofDays(ageDays).toMillis();

        GuardBranchStatusUi statusUi = new GuardBranchStatusUi();
        statusUi.setName(tb.name());

        for (ITrackedChain chain : accessibleChains) {
            String srvCodeOrAlias = chain.serverCode();
            ITeamcityIgnited tcIgn = tcIgnitedProv.server(srvCodeOrAlias, prov);
            String suiteId = chain.tcSuiteId();

            if (chain.triggerBuild())
                statusUi.addAutoTriggerSuite(suiteId);

            List<BuildRefCompacted> hist = tcIgn.getAllBuildsCompacted(suiteId, chain.tcBranch());

            AtomicInteger finished = new AtomicInteger();
            AtomicInteger running = new AtomicInteger();
            AtomicInteger queued = new AtomicInteger();

            List<BuildRefCompacted> validHist = hist.stream()
                .filter(ref -> !ref.isFakeStub())
                .filter(t -> !t.isCancelled(compactor))
                .collect(Collectors.toList());

            List<BuildRefCompacted> lastFinished = validHist.stream()
                .filter(ref -> ref.isFinished(compactor))
                .sorted(Comparator.comparing(BuildRefCompacted::id).reversed())
                .limit(10)
                .collect(Collectors.toList());

            addChainCompositionStats(statusUi, tcIgn, lastFinished);

            validHist.stream()
                .peek(ref -> {
                    if (ref.isRunning(compactor))
                        running.incrementAndGet();
                    else if (ref.isQueued(compactor))
                        queued.incrementAndGet();
                })
                .filter(ref -> ref.isFinished(compactor))
                .filter(ref -> {
                    Integer borderId = tcIgn.getBorderForAgeForBuildId(ageDays);
                    return borderId == null || ref.id() >= borderId;
                })
                .filter(ref -> {
                    Long startTime = tcIgn.getBuildStartTime(ref.id());

                    return startTime != null && startTime > minStartTime;
                })
                .forEach(ref -> {
                    finished.incrementAndGet();
                });

            statusUi.addSuiteRunStat(finished.get(), running.get(), queued.get());
        }

        return statusUi;
    }

    /**
     * Adds cached suite composition stats for last and last-10 chain builds.
     *
     * @param statusUi Guard status UI.
     * @param tcIgn TeamCity facade.
     * @param lastFinished Last finished chain builds, newest first.
     */
    private void addChainCompositionStats(GuardBranchStatusUi statusUi, ITeamcityIgnited tcIgn,
        List<BuildRefCompacted> lastFinished) {
        if (lastFinished.isEmpty())
            return;

        statusUi.addLastBuildsChecked(1);
        statusUi.addTenBuildsChecked(lastFinished.size());

        ChainComposition last = chainComposition(tcIgn, lastFinished.get(0));

        last.failedSuites.forEach(statusUi::addLastFailedSuite);
        last.stableSuites.forEach(statusUi::addLastStableSuite);

        Map<String, Boolean> stableBySuite = new LinkedHashMap<>();

        for (BuildRefCompacted chainBuild : lastFinished) {
            ChainComposition composition = chainComposition(tcIgn, chainBuild);

            composition.stableSuites.forEach(suiteId -> stableBySuite.putIfAbsent(suiteId, true));
            composition.failedSuites.forEach(suiteId -> stableBySuite.put(suiteId, false));
        }

        stableBySuite.forEach((suiteId, stable) -> {
            if (stable)
                statusUi.addTenBuildStableSuite(suiteId);
            else
                statusUi.addTenBuildFailedSuite(suiteId);
        });
    }

    /**
     * @param tcIgn TeamCity facade.
     * @param chainBuildRef Chain build ref.
     */
    private ChainComposition chainComposition(ITeamcityIgnited tcIgn, BuildRefCompacted chainBuildRef) {
        ChainComposition res = new ChainComposition();

        FatBuildCompacted chainBuild = cachedFatBuild(tcIgn, chainBuildRef.id());

        if (chainBuild == null)
            return res;

        for (int depId : chainBuild.snapshotDependencies()) {
            FatBuildCompacted dep = cachedFatBuild(tcIgn, depId);

            if (dep == null || dep.isFakeStub())
                continue;

            String depSuiteId = dep.buildTypeId(compactor);

            if (Strings.isNullOrEmpty(depSuiteId))
                continue;

            if (dep.isSuccess(compactor))
                res.stableSuites.add(depSuiteId);
            else
                res.failedSuites.add(depSuiteId);
        }

        return res;
    }

    /**
     * @param tcIgn TeamCity facade.
     * @param buildId Build id.
     */
    @Nullable private FatBuildCompacted cachedFatBuild(ITeamcityIgnited tcIgn, Integer buildId) {
        if (buildId == null)
            return null;

        try {
            return tcIgn.getFatBuild(buildId, SyncMode.NONE);
        }
        catch (RuntimeException e) {
            return null;
        }
    }

    /** Cached composition of one chain run. */
    private static class ChainComposition {
        /** Failed suites. */
        private final Set<String> failedSuites = new LinkedHashSet<>();

        /** Stable suites. */
        private final Set<String> stableSuites = new LinkedHashSet<>();
    }

    @Override public Map<Integer, Integer> getTrackedBranchUpdateCounters(@Nullable String branch,
        @Nonnull ICredentialsProv creds) {

        final String branchNn = isNullOrEmpty(branch) ? ITcServerConfig.DEFAULT_TRACKED_BRANCH_NAME : branch;
        final ITrackedBranch tracked = tcBotCfg.getTrackedBranches().getBranchMandatory(branchNn);

        Set<Integer> allBranches = new HashSet<>();
        tracked.chainsStream()
            .filter(chainTracked -> tcIgnitedProv.hasAccess(chainTracked.serverCode(), creds))
            .forEach(chainTracked -> {
                String tcBranch = chainTracked.tcBranch();

                Set<Integer> allBranchIds = new HashSet<>(branchEquivalence.branchIdsForQuery(tcBranch, compactor));

                chainTracked.tcBaseBranch().ifPresent(base -> {
                    allBranchIds.addAll(branchEquivalence.branchIdsForQuery(base, compactor));
                });

                allBranches.addAll(allBranchIds);
            });

        return countersStorage.getCounters(allBranches);
    }

    /**
     * Collects data about all long-running tests (run time more than one minute) within one transfer object.
     *
     * @param branch
     * @param creds
     * @return
     */
    public LrTestsFullSummaryUi getTrackedBranchLongRunningTestsSummary(@Nullable String branch,
                                                                        ICredentialsProv creds) {
        LrTestsFullSummaryUi summary = new LrTestsFullSummaryUi();

        final String branchNn = isNullOrEmpty(branch) ? ITcServerConfig.DEFAULT_TRACKED_BRANCH_NAME : branch;
        final ITrackedBranch tracked = tcBotCfg.getTrackedBranches().getBranchMandatory(branchNn);

        tracked.chainsStream()
            .filter(chainTracked -> tcIgnitedProv.hasAccess(chainTracked.serverCode(), creds))
            .map(chainTracked -> {
                final String srvId = chainTracked.serverCode();

                final String branchForTc = chainTracked.tcBranch();

                ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvId, creds);

                List<Integer> hist = tcIgnited.getLastNBuildsFromHistory(chainTracked.tcSuiteId(), branchForTc, 1);

                return chainProc.loadLongRunningTestsSummary(tcIgnited, hist);
            })
            .forEach(summary::addSuiteSummaries);

        return summary;
    }
}
