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
package org.apache.ignite.tcbot.engine.board;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProblemCompacted;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.common.util.FutureUtil;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.chain.SingleBuildRunCtx;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.defect.BlameCandidate;
import org.apache.ignite.tcbot.engine.defect.DefectCompacted;
import org.apache.ignite.tcbot.engine.defect.DefectFirstBuild;
import org.apache.ignite.tcbot.engine.defect.DefectIssue;
import org.apache.ignite.tcbot.engine.defect.DefectsStorage;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcbot.engine.issue.IssueType;
import org.apache.ignite.tcbot.engine.ui.BoardDefectIssueUi;
import org.apache.ignite.tcbot.engine.ui.BoardDefectSummaryUi;
import org.apache.ignite.tcbot.engine.ui.BoardSummaryUi;
import org.apache.ignite.tcbot.engine.ui.DsSuiteUi;
import org.apache.ignite.tcbot.engine.ui.DsTestFailureUi;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.build.ITest;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.apache.ignite.tcignited.history.IRunHistory;

import static org.apache.ignite.tcignited.history.RunStatus.RES_MISSING;
import static org.apache.ignite.tcignited.history.RunStatus.RES_OK;

public class BoardService {
    @Inject IIssuesStorage issuesStorage;
    @Inject FatBuildDao fatBuildDao;
    @Inject ChangeDao changeDao;
    @Inject ITeamcityIgnitedProvider tcProv;
    @Inject DefectsStorage defectStorage;
    @Inject IScheduler scheduler;
    @Inject IStringCompactor compactor;
    @Inject BuildChainProcessor buildChainProcessor;
    @Inject IUserStorage userStorage;
    @Inject ITcBotConfig cfg;

    /**
     * @param creds Credentials.
     */
    public BoardSummaryUi summary(ICredentialsProv creds) {
        issuesToDefectsLater();

        Map<Integer, Future<FatBuildCompacted>> allBuildsMap = new HashMap<>();

        List<DefectCompacted> defects = defectStorage.loadAllDefects();

        BoardSummaryUi res = new BoardSummaryUi();
        boolean admin = userStorage.getUser(creds.getPrincipalId()).isAdmin();
        for (DefectCompacted next : defects) {
            BoardDefectSummaryUi defectUi = new BoardDefectSummaryUi(next, compactor);
            defectUi.setForceResolveAllowed(admin);

            String srvCode = next.tcSrvCode(compactor);

            if (!creds.hasAccess(srvCode))
                continue;

            ITeamcityIgnited tcIgn = tcProv.server(srvCode, creds);

            ITcServerConfig cfg = tcIgn.config();

            List<BlameCandidate> candidates = next.blameCandidates();

            Map<Integer, DefectFirstBuild> build = next.buildsInvolved();
            for (DefectFirstBuild cause : build.values()) {
                FatBuildCompacted firstBuild = cause.build();
                defectUi.addTags(SingleBuildRunCtx.getBuildTagsFromParameters(cfg, compactor, firstBuild));
                FatBuildCompacted fatBuild = fatBuildDao.getFatBuild(next.tcSrvId(), firstBuild.id());

                List<Future<FatBuildCompacted>> futures = buildChainProcessor.replaceWithRecent(fatBuild, allBuildsMap, tcIgn);

                Stream<FatBuildCompacted> results = FutureUtil.getResults(futures);
                List<FatBuildCompacted> freshRebuild = results.collect(Collectors.toList());

                Optional<FatBuildCompacted> rebuild;

                rebuild = !freshRebuild.isEmpty() ? freshRebuild.stream().findFirst() : Optional.empty();

                for (DefectIssue issue : cause.issues()) {
                    BoardDefectIssueUi issueUi = processIssue(tcIgn, rebuild, issue, firstBuild.buildTypeId());

                    defectUi.addIssue(issueUi);
                }
            }

            defectUi.branch = next.tcBranch(compactor);

            res.addDefect(defectUi);
        }

        return res;
    }

    public BoardDefectIssueUi processIssue(ITeamcityIgnited tcIgn,
                                           Optional<FatBuildCompacted> rebuild,
                                           DefectIssue issue, int projectId) {
        Optional<ITest> testResult;

        String issueType = compactor.getStringFromId(issue.issueTypeCode());

        boolean suiteProblem = IssueType.newCriticalFailure.code().equals(issueType)
                || IssueType.newTrustedSuiteFailure.code().equals(issueType);

        String webUrl = null;
        IssueResolveStatus status;
        if (suiteProblem) {
            if (rebuild.isPresent()) {
                FatBuildCompacted fatBuildCompacted = rebuild.get();
                List<ProblemCompacted> problems = fatBuildCompacted.problems();

                if (IssueType.newCriticalFailure.code().equals(issueType)) {
                    boolean hasCriticalProblem = problems.stream().anyMatch(occurrence -> occurrence.isCriticalProblem(compactor));
                    status = hasCriticalProblem ? IssueResolveStatus.FAILING : IssueResolveStatus.FIXED;
                } else {
                    boolean hasBuildProblem = problems.stream().anyMatch(p -> !p.isFailedTests(compactor) && !p.isSnapshotDepProblem(compactor));
                    status = hasBuildProblem ? IssueResolveStatus.FAILING : IssueResolveStatus.FIXED;
                }

                webUrl = DsSuiteUi.buildWebLinkToHist(tcIgn,
                        fatBuildCompacted.buildTypeId(compactor),
                        fatBuildCompacted.branchName(compactor)
                );
            } else
                status = IssueResolveStatus.UNKNOWN;
        } else {
            if (rebuild.isPresent()) {
                testResult = rebuild.get().getAllTests()
                        .filter(t -> t.testName() == issue.testNameCid())
                        .findAny();
            } else
                testResult = Optional.empty();

            if (testResult.isPresent()) {
                ITest test = testResult.get();

                if (test.isIgnoredTest() || test.isMutedTest())
                    status = IssueResolveStatus.IGNORED;
                else if (IssueType.newTestWithHighFlakyRate.code().equals(issueType)) {
                    int fullSuiteNameAndFullTestName = issue.testNameCid();

                    int branchName = rebuild.get().branchName();

                    IRunHistory runStat = tcIgn.getTestRunHist(fullSuiteNameAndFullTestName, projectId, branchName);

                    if (runStat == null)
                        status = IssueResolveStatus.UNKNOWN;
                    else {
                        List<Integer> runResults = runStat.getLatestRunResults();
                        if (runResults == null)
                            status = IssueResolveStatus.UNKNOWN;
                        else {
                            int confidenceOkTestsRow = Math.max(1,
                                (int) Math.ceil(Math.log(1 - cfg.confidence()) / Math.log(1 - issue.getFlakyRate() / 100.0)));
                            Collections.reverse(runResults);
                            int okTestRow = 0;

                            for (Integer run : runResults) {
                                if (run == null || run == RES_MISSING.getCode())
                                    continue;
                                if (run == RES_OK.getCode() && (okTestRow < confidenceOkTestsRow))
                                    okTestRow++;
                                else
                                    break;
                            }

                            status = okTestRow >= confidenceOkTestsRow ? IssueResolveStatus.FIXED : IssueResolveStatus.FAILING;
                        }
                    }
                }
                else
                    status = test.isFailedTest(compactor) ? IssueResolveStatus.FAILING : IssueResolveStatus.FIXED;

                FatBuildCompacted fatBuildCompacted = rebuild.get();
                Long testNameId = test.getTestId();
                String RebuildProjectId = fatBuildCompacted.projectId(compactor);
                String branchName = fatBuildCompacted.branchName(compactor);

                webUrl = DsTestFailureUi.buildWebLink(tcIgn, testNameId, RebuildProjectId, branchName);
            }
            else {
                //exception for new test. removal of test means test is fixed
                status = IssueType.newContributedTestFailure.code().equals(issueType)
                    ? IssueResolveStatus.FIXED
                    : IssueResolveStatus.UNKNOWN;
            }
        }

        return new BoardDefectIssueUi(status, compactor, issue, suiteProblem, webUrl);
    }

    public void issuesToDefectsLater() {
        scheduler.sheduleNamed("issuesToDefects", this::issuesToDefects, 15, TimeUnit.MINUTES);
    }

    @MonitoredTask(name = "Convert issues to defect")
    protected String issuesToDefects() {
        Stream<Issue> stream = issuesStorage.allIssues();

        //todo make property how old issues can be considered as configuration parameter
        long minIssueTs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14);

        //todo not so good to to call init() twice
        fatBuildDao.init();
        changeDao.init();

        AtomicInteger cntIssues = new AtomicInteger();
        HashSet<Integer> processedDefects = new HashSet<>();
        stream
            .filter(issue -> {
                long detected = issue.detectedTs == null ? 0 : issue.detectedTs;

                return detected >= minIssueTs;
            })
            .filter(issue -> {
                //String type = issue.type;
                //return !IssueType.newContributedTestFailure.code().equals(type);

                return true;
            })
            .forEach(issue -> {
                cntIssues.incrementAndGet();

                IssueKey key = issue.issueKey;
                String srvCode = key.getServer();
                //just for init call

                int srvId = ITeamcityIgnited.serverIdToInt(srvCode);
                FatBuildCompacted fatBuild = fatBuildDao.getFatBuild(srvId, key.buildId);
                if (fatBuild == null)
                    return;

                //todo non test failures
                String testName = issue.issueKey().getTestOrBuildName();

                int issueTypeCid = compactor.getStringId(issue.type);
                Integer testNameCid = compactor.getStringIdIfPresent(testName);
                int trackedBranchCid = compactor.getStringId(issue.trackedBranchName);
                double flakyRate = issue.flakyRate;

                int tcSrvCodeCid = compactor.getStringId(srvCode);
                defectStorage.merge(tcSrvCodeCid, srvId, fatBuild,
                    (k, defect) -> {
                        processedDefects.add(defect.id());

                        defect.trackedBranchCidSetIfEmpty(trackedBranchCid);

                        defect.computeIfAbsent(fatBuild).addIssue(issueTypeCid, testNameCid, flakyRate);

                        defect.removeOldVerBlameCandidates();

                        if(defect.blameCandidates().isEmpty())
                            fillBlameCandidates(srvId, fatBuild, defect);

                        return defect;
                    });

            });

        return processedDefects.size() + " defects processed for " + cntIssues.get() + " issues checked";
    }

    private void fillBlameCandidates(int srvId, FatBuildCompacted fatBuild, DefectCompacted defect) {
        //save changes because it can be missed in older DB versions
        defect.changeMap(changeDao.getAll(srvId, fatBuild.changes()));

        Map<Integer, ChangeCompacted> map = defect.changeMap();

        Collection<ChangeCompacted> values = map.values();
        for (ChangeCompacted change : values) {
            BlameCandidate candidate = new BlameCandidate();
            int vcsUsernameCid = change.vcsUsername();
            candidate.vcsUsername(vcsUsernameCid);

            int tcUserUsername = change.tcUserUsername();
            @Nullable TcHelperUser tcHelperUser = null;
            if (tcUserUsername != -1)
                tcHelperUser = userStorage.getUser(compactor.getStringFromId(tcUserUsername));
            else {
                String strVcsUsername = compactor.getStringFromId(vcsUsernameCid);

                if(!Strings.isNullOrEmpty(strVcsUsername) &&
                    strVcsUsername.contains("<") && strVcsUsername.contains(">")) {
                    int emailStartIdx = strVcsUsername.indexOf('<');
                    int emailEndIdx = strVcsUsername.indexOf('>');
                    String email = strVcsUsername.substring(emailStartIdx + 1, emailEndIdx);
                    tcHelperUser = userStorage.findUserByEmail(email);
                }
            }


            if (tcHelperUser != null) {
                String username = tcHelperUser.username();

                String fullName = tcHelperUser.fullName();
                candidate.fullDisplayName(compactor.getStringId(fullName));
                candidate.tcHelperUserUsername(compactor.getStringId(username));
            }

            defect.addBlameCandidate(candidate);
        }
    }

    public void resolveDefect(Integer defectId, ICredentialsProv creds, Boolean forceResolve) {
        DefectCompacted defect = defectStorage.load(defectId);
        Preconditions.checkState(defect != null, "Can't find defect by ID");

        String principalId = creds.getPrincipalId();
        if(Boolean.TRUE.equals(forceResolve)) {
            boolean admin = userStorage.getUser(principalId).isAdmin();

            Preconditions.checkState(admin);
        }

        //todo if it is not forced resovle need to check blockers count for now

        int strId = compactor.getStringId(principalId);
        defect.resolvedByUsernameId(strId);

        defectStorage.save(defect);
    }
}
