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
package org.apache.ignite.ci.tcbot.chain;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.tcbot.conf.BranchTracked;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.conf.TcServerConfig;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.model.long_running.FullLRTestsSummary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Process failures for some setup tracked branch, which may be triggered/monitored by TC Bot.
 */
public class TrackedBranchChainsProcessor {
    /** TC ignited server provider. */
    @Inject private ITeamcityIgnitedProvider tcIgnitedProv;

    /** Tc Bot config. */
    @Inject private ITcBotConfig tcBotCfg;

    /** Chains processor. */
    @Inject private BuildChainProcessor chainProc;

    @Inject private IStringCompactor compactor;

    @AutoProfiling
    @NotNull
    public TestFailuresSummary getTrackedBranchTestFailures(
        @Nullable String branch,
        @Nullable Boolean checkAllLogs,
        int buildResMergeCnt,
        ITcBotUserCreds creds,
        SyncMode syncMode) {
        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        final String branchNn = isNullOrEmpty(branch) ? TcServerConfig.DEFAULT_TRACKED_BRANCH_NAME : branch;
        res.setTrackedBranch(branchNn);

        final BranchTracked tracked = tcBotCfg.getTrackedBranches().getBranchMandatory(branchNn);

        tracked.chains.stream()
            .filter(chainTracked -> tcIgnitedProv.hasAccess(chainTracked.serverId, creds))
            .map(chainTracked -> {
                final String srvCode = chainTracked.serverId;

                final String branchForTc = chainTracked.getBranchForRestMandatory();

                //branch is tracked, so fail rate should be taken from this branch data (otherwise it is specified).
                final String baseBranchTc = chainTracked.getBaseBranchForTc().orElse(branchForTc);

                ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCode, creds);

                ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus(srvCode,
                    tcIgnited.serverCode(),
                    branchForTc);

                chainStatus.baseBranchForTc = baseBranchTc;

                String suiteIdMandatory = chainTracked.getSuiteIdMandatory();

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

                chainStatus.initFromContext(tcIgnited, ctx, baseBranchTc, compactor);

                return chainStatus;
            })
            .forEach(res::addChainOnServer);

        res.servers.sort(Comparator.comparing(ChainAtServerCurrentStatus::serverName));

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
    public FullLRTestsSummary getTrackedBranchLongRunningTestsSummary(@Nullable String branch,
        ITcBotUserCreds creds) {
        FullLRTestsSummary summary = new FullLRTestsSummary();

        final String branchNn = isNullOrEmpty(branch) ? TcServerConfig.DEFAULT_TRACKED_BRANCH_NAME : branch;
        final BranchTracked tracked = tcBotCfg.getTrackedBranches().getBranchMandatory(branchNn);

        tracked.chains.stream()
            .filter(chainTracked -> tcIgnitedProv.hasAccess(chainTracked.serverId, creds))
            .map(chainTracked -> {
                final String srvId = chainTracked.serverId;

                final String branchForTc = chainTracked.getBranchForRestMandatory();

                ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvId, creds);

                List<Integer> hist = tcIgnited.getLastNBuildsFromHistory(chainTracked.getSuiteIdMandatory(), branchForTc, 1);

                return chainProc.loadLongRunningTestsSummary(tcIgnited, hist);
            })
            .forEach(summary::addSuiteSummaries);

        return summary;
    }
}
