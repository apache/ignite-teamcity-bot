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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.TestCompactedMult;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnited;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.ci.jira.ignited.IJiraIgnited;
import org.apache.ignite.ci.jira.ignited.IJiraIgnitedProvider;
import org.apache.ignite.ci.tcbot.visa.BranchTicketMatcher;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.history.RunHistSync;
import org.apache.ignite.tcservice.ITeamcity;
import org.jetbrains.annotations.Nullable;

/**
 * Process pull request/untracked branch chain at particular server.
 */
public class PrChainsProcessor {
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

    /**
     * @param creds Credentials.
     * @param srvCode Server id.
     * @param suiteId Suite id.
     * @param branchForTc Branch name in TC identification.
     * @param act Action.
     * @param cnt Count.
     * @param baseBranchForTc Base branch name in TC identification.
     * @param checkAllLogs Check all logs
     * @param mode TC Server Sync Mode
     * @return Test failures summary.
     */
    @AutoProfiling
    public TestFailuresSummary getTestFailuresSummary(
        ITcBotUserCreds creds,
        String srvCode,
        String suiteId,
        String branchForTc,
        String act,
        Integer cnt,
        @Nullable String baseBranchForTc,
        @Nullable Boolean checkAllLogs,
        SyncMode mode) {
        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        //using here non persistent TC allows to skip update statistic
        ITeamcityIgnited tcIgnited = tcIgnitedProvider.server(srvCode, creds);

        IGitHubConnIgnited gitHubConnIgnited = gitHubConnIgnitedProvider.server(srvCode);

        IJiraIgnited jiraIntegration = jiraIgnProv.server(srvCode);

        res.setJavaFlags(gitHubConnIgnited, jiraIntegration);

        LatestRebuildMode rebuild;
        if (FullQueryParams.HISTORY.equals(act))
            rebuild = LatestRebuildMode.ALL;
        else if (FullQueryParams.LATEST.equals(act))
            rebuild = LatestRebuildMode.LATEST;
        else if (FullQueryParams.CHAIN.equals(act))
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

        String baseBranch = Strings.isNullOrEmpty(baseBranchForTc) ? ITeamcity.DEFAULT : baseBranchForTc;

        FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(
            tcIgnited,
            hist,
            rebuild,
            logs,
            buildResMergeCnt == 1,
            baseBranch,
            mode);

        ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus(srvCode, tcIgnited.serverCode(), branchForTc);

        chainStatus.baseBranchForTc = baseBranch;

        if (ctx.isFakeStub())
            chainStatus.setBuildNotFound(true);
        else {
            int cnt0 = (int)ctx.getRunningUpdates().count();

            if (cnt0 > 0)
                runningUpdates.addAndGet(cnt0);

            //fail rate reference is always default (master)
            chainStatus.initFromContext(tcIgnited, ctx, baseBranch, compactor, false); // don't need for PR

            chainStatus.initJiraAndGitInfo(ticketMatcher, jiraIntegration, gitHubConnIgnited);
        }

        res.addChainOnServer(chainStatus);

        res.postProcess(runningUpdates.get());

        return res;
    }

    /**
     * @param buildTypeId  Build type ID, for which visa was ordered.
     * @param branchForTc Branch for TeamCity.
     * @param srvId Server id.
     * @param prov Credentials.
     * @return List of suites with possible blockers.
     */
    @Nullable public List<SuiteCurrentStatus> getBlockersSuitesStatuses(String buildTypeId,
        String branchForTc,
        String srvId,
        ITcBotUserCreds prov) {
        return getBlockersSuitesStatuses(buildTypeId, branchForTc, srvId, prov, SyncMode.RELOAD_QUEUED);
    }

    @Nullable
    public List<SuiteCurrentStatus> getBlockersSuitesStatuses(String buildTypeId, String branchForTc, String srvId,
                                                              ITcBotUserCreds prov, SyncMode syncMode) {
        //using here non persistent TC allows to skip update statistic
        ITeamcityIgnited tcIgnited = tcIgnitedProvider.server(srvId, prov);

        List<Integer> hist = tcIgnited.getLastNBuildsFromHistory(buildTypeId, branchForTc, 1);

        String baseBranch = ITeamcity.DEFAULT;

        FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(
            tcIgnited,
            hist,
            LatestRebuildMode.LATEST,
            ProcessLogsMode.SUITE_NOT_COMPLETE,
            true,
            baseBranch,
            syncMode);

        if (ctx.isFakeStub())
            return null;

        return findBlockerFailures(ctx, tcIgnited, baseBranch);
    }

    /**
     * @return Failures for given server.
     * @param fullChainRunCtx
     * @param tcIgnited
     * @param baseBranch
     */
    //todo may avoid creation of UI model for simple comment.
    private List<SuiteCurrentStatus> findBlockerFailures(FullChainRunCtx fullChainRunCtx, ITeamcityIgnited tcIgnited,
        String baseBranch) {
        return fullChainRunCtx
            .failedChildSuites()
            .map((ctx) -> {
                String normalizedBaseBranch = RunHistSync.normalizeBranch(baseBranch);
                IRunHistory statInBaseBranch = tcIgnited.getSuiteRunHist(ctx.suiteId(), normalizedBaseBranch);

                String suiteComment = ctx.getPossibleBlockerComment(compactor, statInBaseBranch, tcIgnited.config());

                List<TestFailure> failures = ctx.getFailedTests().stream().map(occurrence -> {
                    IRunHistory stat = tcIgnited.getTestRunHist(occurrence.getName(), normalizedBaseBranch);

                    String testBlockerComment = TestCompactedMult.getPossibleBlockerComment(stat);

                    if (!Strings.isNullOrEmpty(testBlockerComment)) {
                        final TestFailure failure = new TestFailure();

                        failure.initFromOccurrence(occurrence, tcIgnited, ctx.projectId(), ctx.branchName(), baseBranch);

                        return failure;
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());


                // test failure based blockers and/or blocker found by suite results
                if (!failures.isEmpty() || !Strings.isNullOrEmpty(suiteComment)) {

                    SuiteCurrentStatus suiteUi = new SuiteCurrentStatus();
                    suiteUi.testFailures = failures;

                    suiteUi.initFromContext(tcIgnited, ctx, baseBranch, compactor, false, false);

                    return suiteUi;
                }

                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
