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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnited;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.github.pure.IGitHubConnectionProvider;
import org.apache.ignite.ci.jira.ignited.IJiraIgnited;
import org.apache.ignite.ci.jira.ignited.IJiraIgnitedProvider;
import org.apache.ignite.ci.tcbot.visa.BranchTicketMatcher;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.SyncMode;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.jetbrains.annotations.Nullable;

/**
 * Process pull request/untracked branch chain at particular server.
 */
public class PrChainsProcessor {
    /** Build chain processor. */
    @Inject private BuildChainProcessor buildChainProcessor;

    /** Tc server provider. */
    @Inject private ITcServerProvider tcSrvProvider;

    /** Tc server provider. */
    @Inject private ITeamcityIgnitedProvider tcIgnitedProvider;

    /** Git hub connection provider. */
    @Inject private IGitHubConnectionProvider gitHubConnProvider;

    /** */
    @Inject private IGitHubConnIgnitedProvider gitHubConnIgnitedProvider;

    /** */
    @Inject private IJiraIgnitedProvider jiraIgnProv;

    @Inject private BranchTicketMatcher ticketMatcher;

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
        ICredentialsProv creds,
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
        IAnalyticsEnabledTeamcity teamcity = tcSrvProvider.server(srvCode, creds);
        ITeamcityIgnited tcIgnited = tcIgnitedProvider.server(srvCode, creds);

        IGitHubConnection gitHubConn = gitHubConnProvider.server(srvCode);

        IGitHubConnIgnited gitHubConnIgnited = gitHubConnIgnitedProvider.server(srvCode);

        IJiraIgnited jiraIntegration = jiraIgnProv.server(srvCode);

        res.setJavaFlags(teamcity, gitHubConn, jiraIntegration);

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

        final FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(teamcity,
            tcIgnited,
            hist,
            rebuild,
            logs,
            buildResMergeCnt == 1,
            baseBranch,
            mode);

        final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus(teamcity.serverId(), branchForTc);

        chainStatus.baseBranchForTc = baseBranch;

        if (ctx.isFakeStub())
            chainStatus.setBuildNotFound(true);
        else {
            int cnt0 = (int)ctx.getRunningUpdates().count();

            if (cnt0 > 0)
                runningUpdates.addAndGet(cnt0);

            //fail rate reference is always default (master)
            chainStatus.initFromContext(tcIgnited, ctx, baseBranch);

            chainStatus.initJiraAndGitInfo(ticketMatcher, jiraIntegration, gitHubConnIgnited);
        }

        res.addChainOnServer(chainStatus);

        res.postProcess(runningUpdates.get());

        return res;
    }

    /**
     * @param buildTypeId Suite name.
     * @param branchForTc Branch for TeamCity.
     * @param srvId Server id.
     * @param prov Credentials.
     * @return List of suites with possible blockers.
     */
    @Nullable public List<SuiteCurrentStatus> getBlockersSuitesStatuses(String buildTypeId,
        String branchForTc,
        String srvId,
        ICredentialsProv prov) {
        return getBlockersSuitesStatuses(buildTypeId, branchForTc, srvId, prov, SyncMode.RELOAD_QUEUED);
    }

    @Nullable
    public List<SuiteCurrentStatus> getBlockersSuitesStatuses(String buildTypeId, String branchForTc, String srvId,
        ICredentialsProv prov, SyncMode queued) {
        List<SuiteCurrentStatus> res = new ArrayList<>();

        TestFailuresSummary summary = getTestFailuresSummary(
            prov, srvId, buildTypeId, branchForTc,
            FullQueryParams.LATEST, null, null, false, queued);

        boolean noBuilds = summary.servers.stream().anyMatch(s -> s.buildNotFound);

        if (noBuilds)
            return null;

        for (ChainAtServerCurrentStatus server : summary.servers) {
            Map<String, List<SuiteCurrentStatus>> fails = findBlockerFailures(server);

            fails.forEach((k, v) -> res.addAll(v));
        }

        return res;
    }

    /**
     * @param srv Server.
     * @return Failures for given server.
     */
    private Map<String, List<SuiteCurrentStatus>> findBlockerFailures(ChainAtServerCurrentStatus srv) {
        Map<String, List<SuiteCurrentStatus>> fails = new LinkedHashMap<>();

        for (SuiteCurrentStatus suite : srv.suites) {
            String suiteRes = suite.result.toLowerCase();
            String failType = null;

            if (suiteRes.contains("compilation"))
                failType = "compilation";

            if (suiteRes.contains("timeout"))
                failType = "timeout";

            if (suiteRes.contains("exit code"))
                failType = "exit code";

            if (suiteRes.contains(ProblemOccurrence.JAVA_LEVEL_DEADLOCK.toLowerCase()))
                failType = "java level deadlock";

            if (suiteRes.contains(ProblemOccurrence.BUILD_FAILURE_ON_MESSAGE.toLowerCase()))
                failType = "build failure on message";

            if (suiteRes.contains(ProblemOccurrence.BUILD_FAILURE_ON_METRIC.toLowerCase()))
                failType = "build failure on metrics";

            if (suiteRes.contains(MultBuildRunCtx.CANCELLED.toLowerCase()))
                failType = MultBuildRunCtx.CANCELLED.toLowerCase();

            if (failType == null) {
                List<TestFailure> failures = new ArrayList<>();

                for (TestFailure testFailure : suite.testFailures) {
                    if (testFailure.isNewFailedTest())
                        failures.add(testFailure);
                }

                if (!failures.isEmpty()) {
                    suite.testFailures = failures;

                    failType = "failed tests";
                }
            }

            if (failType != null)
                fails.computeIfAbsent(failType, k -> new ArrayList<>()).add(suite);
        }

        return fails;
    }
}
