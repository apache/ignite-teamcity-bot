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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;

import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.githubservice.IGitHubConnection;
import org.apache.ignite.tcbot.engine.chain.*;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.githubignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.jiraignited.IJiraIgnited;
import org.apache.ignite.jiraignited.IJiraIgnitedProvider;
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
import org.apache.ignite.tcbot.engine.ui.DsSuiteUi;
import org.apache.ignite.tcbot.engine.ui.DsTestFailureUi;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.history.RunHistSync;
import org.apache.ignite.tcservice.ITeamcity;

/**
 * Process pull request/untracked branch chain at particular server.
 */
public class PrChainsProcessor {
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
    public DsSummaryUi getTestFailuresSummary(
        ICredentialsProv creds,
        String srvCode,
        String suiteId,
        String branchForTc,
        String act,
        Integer cnt,
        @Nullable String baseBranchForTc,
        @Nullable Boolean checkAllLogs,
        SyncMode mode) {
        final DsSummaryUi res = new DsSummaryUi();
        final AtomicInteger runningUpdates = new AtomicInteger();

        //using here non persistent TC allows to skip update statistic
        ITeamcityIgnited tcIgnited = tcIgnitedProvider.server(srvCode, creds);

        IGitHubConnIgnited gitHubConnIgnited = gitHubConnIgnitedProvider.server(srvCode);

        IJiraIgnited jiraIntegration = jiraIgnProv.server(srvCode);

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

        String baseBranch = Strings.isNullOrEmpty(baseBranchForTc) ? ITeamcity.DEFAULT : baseBranchForTc;

        FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(
            tcIgnited,
            hist,
            rebuild,
            logs,
            buildResMergeCnt == 1,
            baseBranch,
            mode);

        DsChainUi chainStatus = new DsChainUi(srvCode, tcIgnited.serverCode(), branchForTc);

        chainStatus.baseBranchForTc = baseBranch;

        if (ctx.isFakeStub())
            chainStatus.setBuildNotFound(true);
        else {
            int cnt0 = (int)ctx.getRunningUpdates().count();

            if (cnt0 > 0)
                runningUpdates.addAndGet(cnt0);

            //fail rate reference is always default (master)
            chainStatus.initFromContext(tcIgnited, ctx, baseBranch, compactor, false); // don't need for PR

            initJiraAndGitInfo(chainStatus, jiraIntegration, gitHubConnIgnited);
        }

        res.addChainOnServer(chainStatus);

        res.postProcess(runningUpdates.get());

        return res;
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
     * @param srvId Server id.
     * @param prov Credentials.
     * @return List of suites with possible blockers.
     */
    @Nullable public List<DsSuiteUi> getBlockersSuitesStatuses(String buildTypeId,
                                                               String branchForTc,
                                                               String srvId,
                                                               ICredentialsProv prov) {
        return getBlockersSuitesStatuses(buildTypeId, branchForTc, srvId, prov, SyncMode.RELOAD_QUEUED);
    }

    @Nullable
    public List<DsSuiteUi> getBlockersSuitesStatuses(String buildTypeId, String branchForTc, String srvId,
                                                     ICredentialsProv prov, SyncMode syncMode) {
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
    private List<DsSuiteUi> findBlockerFailures(FullChainRunCtx fullChainRunCtx, ITeamcityIgnited tcIgnited,
                                                String baseBranch) {
        return fullChainRunCtx
            .failedChildSuites()
            .map((ctx) -> {
                String normalizedBaseBranch = RunHistSync.normalizeBranch(baseBranch);
                Integer baseBranchId = compactor.getStringIdIfPresent(normalizedBaseBranch);
                IRunHistory statInBaseBranch = ctx.history(tcIgnited, baseBranchId);
                Integer suiteId = compactor.getStringIdIfPresent(ctx.suiteId()); // can be inlined

                String suiteComment = ctx.getPossibleBlockerComment(compactor, statInBaseBranch, tcIgnited.config());

                List<DsTestFailureUi> failures = ctx.getFailedTests().stream().map(occurrence -> {
                    IRunHistory stat = occurrence.history(tcIgnited, suiteId, baseBranchId);
                    String testBlockerComment = TestCompactedMult.getPossibleBlockerComment(stat);

                    if (!Strings.isNullOrEmpty(testBlockerComment)) {
                        final DsTestFailureUi failure = new DsTestFailureUi();

                        failure.initFromOccurrence(occurrence, ctx.buildTypeIdId(), tcIgnited, ctx.projectId(), ctx.branchName(), baseBranch, baseBranchId);

                        return failure;
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());


                // test failure based blockers and/or blocker found by suite results
                if (!failures.isEmpty() || !Strings.isNullOrEmpty(suiteComment)) {

                    DsSuiteUi suiteUi = new DsSuiteUi();
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
