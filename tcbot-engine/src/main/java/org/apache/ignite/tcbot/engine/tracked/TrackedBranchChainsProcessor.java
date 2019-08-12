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
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedChain;
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

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Process failures for some setup tracked branch, which may be triggered/monitored by TC Bot.
 */
public class TrackedBranchChainsProcessor implements IDetailedStatusForTrackedBranch {
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
        final DsSummaryUi res = new DsSummaryUi();

        final String branchNn = isNullOrEmpty(branch) ? ITcServerConfig.DEFAULT_TRACKED_BRANCH_NAME : branch;
        res.setTrackedBranch(branchNn);

        final ITrackedBranch tracked = tcBotCfg.getTrackedBranches().getBranchMandatory(branchNn);

        tracked.chainsStream()
            .filter(chainTracked -> tcIgnitedProv.hasAccess(chainTracked.serverCode(), creds))
            .map(chainTracked -> {
                final String srvCodeOrAlias = chainTracked.serverCode();

                final String branchForTc = chainTracked.tcBranch();

                //branch is tracked, so fail rate should be taken from this branch data (otherwise it is specified).
                final String baseBranchTc = chainTracked.tcBaseBranch().orElse(branchForTc);

                ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, creds);

                Map<Integer, Integer> requireParamVal = new HashMap<>();

                if (!Strings.isNullOrEmpty(tagForHistSelected)) {
                    requireParamVal.putAll(
                        reverseTagToParametersRequired(tagForHistSelected, srvCodeOrAlias));
                }

                DsChainUi chainStatus = new DsChainUi(srvCodeOrAlias,
                    tcIgnited.serverCode(),
                    branchForTc);

                chainStatus.baseBranchForTc = baseBranchTc;

                String suiteIdMandatory = chainTracked.tcSuiteId();

                List<Integer> chains = tcIgnited.getLastNBuildsFromHistory(suiteIdMandatory, branchForTc, buildResMergeCnt);

                ProcessLogsMode logs;
                if (buildResMergeCnt > 1)
                    logs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.DISABLED;
                else
                    logs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE;

                LatestRebuildMode rebuild = buildResMergeCnt > 1 ? LatestRebuildMode.ALL : LatestRebuildMode.LATEST;

                boolean includeScheduled = buildResMergeCnt == 1;

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

                chainStatus.initFromContext(tcIgnited, ctx, baseBranchTc, compactor, calcTrustedTests, tagSelected,
                    displayMode, maxDurationSec, requireParamVal,
                    showMuted, showIgnored);

                return chainStatus;
            })
            .forEach(res::addChainOnServer);

        res.servers.sort(Comparator.comparing(DsChainUi::serverName));

        res.initCounters(getTrackedBranchUpdateCounters(branch, creds));

        return res;
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
