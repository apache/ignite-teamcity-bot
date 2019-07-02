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

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.chain.FullChainRunCtx;
import org.apache.ignite.tcbot.engine.chain.LatestRebuildMode;
import org.apache.ignite.tcbot.engine.chain.ProcessLogsMode;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.engine.ui.LrTestsFullSummaryUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
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

    @Override
    @AutoProfiling
    @Nonnull
    public DsSummaryUi getTrackedBranchTestFailures(
        @Nullable String branch,
        @Nullable Boolean checkAllLogs,
        int buildResMergeCnt,
        ICredentialsProv creds,
        SyncMode syncMode,
        boolean calcTrustedTests) {
        final DsSummaryUi res = new DsSummaryUi();
        final AtomicInteger runningUpdates = new AtomicInteger();

        final String branchNn = isNullOrEmpty(branch) ? ITcServerConfig.DEFAULT_TRACKED_BRANCH_NAME : branch;
        res.setTrackedBranch(branchNn);

        final ITrackedBranch tracked = tcBotCfg.getTrackedBranches().getBranchMandatory(branchNn);

        tracked.chainsStream()
            .filter(chainTracked -> tcIgnitedProv.hasAccess(chainTracked.serverCode(), creds))
            .map(chainTracked -> {
                final String srvCode = chainTracked.serverCode();

                final String branchForTc = chainTracked.tcBranch();

                //branch is tracked, so fail rate should be taken from this branch data (otherwise it is specified).
                final String baseBranchTc = chainTracked.tcBaseBranch().orElse(branchForTc);

                ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCode, creds);

                DsChainUi chainStatus = new DsChainUi(srvCode,
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
                    syncMode
                );

                int cnt = (int)ctx.getRunningUpdates().count();
                if (cnt > 0)
                    runningUpdates.addAndGet(cnt);

                chainStatus.initFromContext(tcIgnited, ctx, baseBranchTc, compactor, calcTrustedTests);

                return chainStatus;
            })
            .forEach(res::addChainOnServer);

        res.servers.sort(Comparator.comparing(DsChainUi::serverName));

        res.postProcess(runningUpdates.get());

        return res;
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
