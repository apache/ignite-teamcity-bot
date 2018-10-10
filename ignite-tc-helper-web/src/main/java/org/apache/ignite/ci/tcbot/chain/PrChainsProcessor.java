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

import com.google.common.base.Strings;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.teamcity.pure.ITcServerProvider;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.github.pure.IGitHubConnectionProvider;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Process pull request/untracked branch chain at particular server.
 */
public class PrChainsProcessor {
    /** Build chain processor. */
    @Inject BuildChainProcessor buildChainProcessor;

    /** Tc server provider. */
    @Inject ITcServerProvider tcSrvProvider;
    @Inject IGitHubConnectionProvider gitHubConnProvider;

    /**
     * @param creds Credentials.
     * @param srvId Server id.
     * @param suiteId Suite id.
     * @param branchForTc Branch name in TC identification.
     * @param act Action.
     * @param cnt Count.
     * @param baseBranchForTc Base branch name in TC identification.
     * @param checkAllLogs Check all logs
     * @return Test failures summary.
     */
    @AutoProfiling
    public TestFailuresSummary getTestFailuresSummary(
        ICredentialsProv creds,
        String srvId,
        String suiteId,
        String branchForTc,
        String act,
        Integer cnt,
        @Nullable String baseBranchForTc,
        @Nullable Boolean checkAllLogs) {
        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        //using here non persistent TC allows to skip update statistic
        IAnalyticsEnabledTeamcity teamcity = tcSrvProvider.server(srvId, creds);

        IGitHubConnection gitHubConn = gitHubConnProvider.server(srvId);

        res.setJavaFlags(teamcity, gitHubConn);

        LatestRebuildMode rebuild;
        if (FullQueryParams.HISTORY.equals(act))
            rebuild = LatestRebuildMode.ALL;
        else if (FullQueryParams.LATEST.equals(act))
            rebuild = LatestRebuildMode.LATEST;
        else if (FullQueryParams.CHAIN.equals(act))
            rebuild = LatestRebuildMode.NONE;
        else
            rebuild = LatestRebuildMode.LATEST;

        List<BuildRef> finishedBuilds = teamcity.getFinishedBuildsIncludeSnDepFailed(
            suiteId,
            branchForTc);

        long buildResMergeCnt;
        if (rebuild == LatestRebuildMode.ALL)
            buildResMergeCnt = cnt == null ? 10 : cnt;
        else
            buildResMergeCnt = 1;

        ProcessLogsMode logs;
        if (buildResMergeCnt > 1)
            logs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.DISABLED;
        else
            logs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE;

        final List<BuildRef> chains = finishedBuilds.stream()
            .filter(ref -> !ref.isFakeStub())
            .sorted(Comparator.comparing(BuildRef::getId).reversed())
            .filter(b -> b.getId() != null)
            .limit(buildResMergeCnt)
            .collect(Collectors.toList());

        String baseBranch = Strings.isNullOrEmpty(baseBranchForTc) ? ITeamcity.DEFAULT : baseBranchForTc;

        final FullChainRunCtx val = buildChainProcessor.loadFullChainContext(teamcity, chains,
            rebuild,
            logs, buildResMergeCnt == 1,
            baseBranch);

        Optional<FullChainRunCtx> pubCtx = Optional.of(val);

        final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus(teamcity.serverId(), branchForTc);

        chainStatus.baseBranchForTc = baseBranch;

        pubCtx.ifPresent(ctx -> {
            if (ctx.isFakeStub())
                chainStatus.setBuildNotFound(true);
            else {
                int cnt0 = (int)ctx.getRunningUpdates().count();

                if (cnt0 > 0)
                    runningUpdates.addAndGet(cnt0);

                //fail rate reference is always default (master)
                chainStatus.initFromContext(teamcity, ctx, teamcity, baseBranch);
            }
        });

        res.addChainOnServer(chainStatus);

        res.postProcess(runningUpdates.get());

        return res;
    }
}
