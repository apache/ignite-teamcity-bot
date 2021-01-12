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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
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
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedChain;
import org.apache.ignite.tcbot.engine.newtests.NewTestsStorage;
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
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

        if (ctx.isFakeStub())
            chainStatus.setBuildNotFound(true);
        else {
            //fail rate reference is always default (master)
            chainStatus.initFromContext(tcIgnited, ctx, baseBranchForTc, compactor, false,
                    null, null, -1, null, false, false); // don't need for PR
            chainStatus.findNewTests(ctx, tcIgnited, baseBranchForTc, compactor, newTestsStorage);
            initJiraAndGitInfo(chainStatus, jiraIntegration, gitHubConnIgnited);
        }

        res.addChainOnServer(chainStatus);

        res.initCounters(getPrUpdateCounters(srvCodeOrAlias, branchForTc, baseBranchForTc, creds));

        return res;
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
    //todo may avoid creation of UI model for simple comment.
    private List<ShortSuiteUi> findBlockerFailures(FullChainRunCtx fullChainRunCtx,
        ITeamcityIgnited tcIgnited,
        String baseBranch) {
        String normalizedBaseBranch = BranchEquivalence.normalizeBranch(baseBranch);
        Integer baseBranchId = compactor.getStringIdIfPresent(normalizedBaseBranch);

        Predicate<MultBuildRunCtx> filter = suite ->
            suite.isFailed() || suite.hasTestToReport(tcIgnited, baseBranchId, false, false);

        return fullChainRunCtx
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
                    return new ShortSuiteUi()
                        .testShortFailures(failures)
                        .initFrom(ctx, tcIgnited, compactor, statInBaseBranch);
                }

                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * @return New tests for given server.
     * @param fullChainRunCtx
     * @param tcIgnited
     * @param baseBranch
     */
    //todo may avoid creation of UI model for simple comment.
    private List<ShortSuiteNewTestsUi> findNewTests(FullChainRunCtx fullChainRunCtx,
        ITeamcityIgnited tcIgnited,
        String baseBranch) {
        String normalizedBaseBranch = BranchEquivalence.normalizeBranch(baseBranch);
        Integer baseBranchId = compactor.getStringIdIfPresent(normalizedBaseBranch);

        return fullChainRunCtx
            .suites()
            .map((ctx) -> {
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
}
