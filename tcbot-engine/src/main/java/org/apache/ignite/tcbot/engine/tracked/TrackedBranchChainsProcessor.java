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
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
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

    /**
     * @param branch Branch.
     * @param buildResMergeCnt Build results merge count.
     * @param creds Credentials.
     * @param syncMode Sync mode.
     * @param tagForHistSelected Selected tag for filtering history.
     * @param sortOption Sort mode.
     * @param maxDetailsChars Max chars to include for every test details block. Non-positive means no limit.
     * @param testName Full test name to include.
     * @param suiteId Suite id to include.
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
        @Nullable String suiteId) {
        long reqId = aiPromptMonitor.start("trackedBranch", branch, null, null, testName);
        StringBuilder res = new StringBuilder();

        try {
            final String branchNn = isNullOrEmpty(branch) ? ITcServerConfig.DEFAULT_TRACKED_BRANCH_NAME : branch;
            final ITrackedBranch tracked = tcBotCfg.getTrackedBranches().getBranchMandatory(branchNn);
            final int maxDetails = maxDetailsChars == null
                ? TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS
                : maxDetailsChars;

            tracked.chainsStream()
                .filter(chainTracked -> tcIgnitedProv.hasAccess(chainTracked.serverCode(), creds))
                .forEach(chainTracked -> {
                    String srvCodeOrAlias = chainTracked.serverCode();
                    String branchForTc = chainTracked.tcBranch();
                    String baseBranchTc = chainTracked.tcBaseBranch().orElse(branchForTc);
                    String suiteIdMandatory = chainTracked.tcSuiteId();

                    aiPromptMonitor.stage(reqId, "loading history: " + srvCodeOrAlias + "/" + suiteIdMandatory);

                    ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, creds);

                    Map<Integer, Integer> requireParamVal = new HashMap<>();

                    if (!Strings.isNullOrEmpty(tagForHistSelected))
                        requireParamVal.putAll(reverseTagToParametersRequired(tagForHistSelected, srvCodeOrAlias));

                    List<Integer> chains = tcIgnited.getLastNBuildsFromHistory(suiteIdMandatory, branchForTc,
                        Math.max(buildResMergeCnt, 1));

                    LatestRebuildMode rebuild = buildResMergeCnt > 1 ? LatestRebuildMode.ALL : LatestRebuildMode.LATEST;

                    aiPromptMonitor.stage(reqId, "loading chain context: " + srvCodeOrAlias + "/" + suiteIdMandatory);

                    FullChainRunCtx ctx = loadAiPromptContextBestEffort(reqId, tcIgnited, chains, rebuild,
                        buildResMergeCnt == 1, baseBranchTc, syncMode, sortOption, requireParamVal,
                        srvCodeOrAlias + "/" + suiteIdMandatory);

                    if (res.length() > 0)
                        res.append("\n\n");

                    aiPromptMonitor.stage(reqId, "building prompt: " + srvCodeOrAlias + "/" + suiteIdMandatory);

                    res.append(new TestFailuresAiPromptBuilder(compactor)
                        .buildPrompt(tcIgnited, ctx, baseBranchTc, maxDetails, testName, suiteId));
                });

            aiPromptMonitor.finish(reqId, "chars=" + res.length());

            return res.toString();
        }
        catch (RuntimeException e) {
            aiPromptMonitor.fail(reqId, e);

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
        String stageSuffix) {
        Future<FullChainRunCtx> live = null;

        try {
            aiPromptMonitor.stage(reqId, "trying fresh context for up to 1s: " + stageSuffix);

            live = tcUpdatePool.getService().submit(() -> chainProc.loadFullChainContext(
                tcIgnited,
                chains,
                rebuild,
                ProcessLogsMode.CACHED_ONLY,
                includeScheduledInfo,
                baseBranchTc,
                liveSyncMode,
                sortOption,
                requireParamVal
            ));

            return live.get(1, TimeUnit.SECONDS);
        }
        catch (TimeoutException e) {
            if (live != null)
                live.cancel(true);

            aiPromptMonitor.stage(reqId, "fresh context timed out, using stale cache: " + stageSuffix);
        }
        catch (InterruptedException e) {
            if (live != null)
                live.cancel(true);

            Thread.currentThread().interrupt();

            aiPromptMonitor.stage(reqId, "fresh context interrupted, using stale cache: " + stageSuffix);
        }
        catch (Exception e) {
            aiPromptMonitor.stage(reqId, "fresh context failed, using stale cache: " + stageSuffix + " - " + e.getMessage());
        }

        return chainProc.loadFullChainContext(
            tcIgnited,
            chains,
            rebuild,
            ProcessLogsMode.CACHED_ONLY,
            false,
            baseBranchTc,
            SyncMode.NONE,
            sortOption,
            requireParamVal
        );
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
            uiInitNanos += System.nanoTime() - stepStart;

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

            List<BuildRefCompacted> hist = tcIgn.getAllBuildsCompacted(chain.tcSuiteId(), chain.tcBranch());

            AtomicInteger finished = new AtomicInteger();
            AtomicInteger running = new AtomicInteger();
            AtomicInteger queued = new AtomicInteger();

            hist.stream()
                .filter(ref -> !ref.isFakeStub())
                .filter(t -> !t.isCancelled(compactor))
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
