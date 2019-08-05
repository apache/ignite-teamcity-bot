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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.TestCompacted;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.common.util.FutureUtil;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.defect.CommitCompacted;
import org.apache.ignite.tcbot.engine.defect.DefectCompacted;
import org.apache.ignite.tcbot.engine.defect.DefectFirstBuild;
import org.apache.ignite.tcbot.engine.defect.DefectIssue;
import org.apache.ignite.tcbot.engine.defect.DefectsStorage;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcbot.engine.issue.IssueType;
import org.apache.ignite.tcbot.engine.ui.BoardDefectSummaryUi;
import org.apache.ignite.tcbot.engine.ui.BoardSummaryUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.build.ITest;
import org.apache.ignite.tcignited.creds.ICredentialsProv;

public class BoardService {
    @Inject IIssuesStorage issuesStorage;
    @Inject FatBuildDao fatBuildDao;
    @Inject ChangeDao changeDao;
    @Inject ITeamcityIgnitedProvider tcProv;
    @Inject DefectsStorage defectStorage;
    @Inject IScheduler scheduler;
    @Inject IStringCompactor compactor;

    @Inject BuildChainProcessor buildChainProcessor;


    /**
     * @param creds Credentials.
     */
    public BoardSummaryUi summary(ICredentialsProv creds) {
        issuesToDefectsLater();

        Map<Integer, Future<FatBuildCompacted>> allBuildsMap = new HashMap<>();

        List<DefectCompacted> defects = defectStorage.loadAllDefects();

        BoardSummaryUi res = new BoardSummaryUi();
        for (DefectCompacted next : defects) {
            BoardDefectSummaryUi defectUi = new BoardDefectSummaryUi(next, compactor);

            String srvCode = next.tcSrvCode(compactor);

            if(!creds.hasAccess(srvCode))
                continue;

            ITeamcityIgnited tcIgn = tcProv.server(srvCode, creds);

            Map<Integer, DefectFirstBuild> build = next.buildsInvolved();
            for (DefectFirstBuild cause : build.values()) {
                BuildRefCompacted bref = cause.build();
                FatBuildCompacted fatBuild = fatBuildDao.getFatBuild(next.tcSrvId(), bref.id());

                List<Future<FatBuildCompacted>> futures = buildChainProcessor.replaceWithRecent(fatBuild, allBuildsMap, tcIgn);

                Stream<FatBuildCompacted> results = FutureUtil.getResults(futures);
                List<FatBuildCompacted> freshRebuild = results.collect(Collectors.toList());
                if(!freshRebuild.isEmpty()) {
                    FatBuildCompacted buildCompacted = freshRebuild.get(0);

                    Set<DefectIssue> issues = cause.issues();
                    for (DefectIssue issue : issues) {
                        Optional<ITest> any = buildCompacted.getAllTests()
                            .filter(t -> t.testName() == issue.testNameCid())
                            .findAny();

                        if(any.isPresent()) {
                            boolean failed = any.get().isFailedTest(compactor);
                            if(!failed)
                                defectUi.addFixedIssue();
                            else
                                defectUi.addNotFixedIssue();
                        }

                        String testOrBuildName = compactor.getStringFromId(issue.testNameCid());
                        defectUi.addIssue(testOrBuildName, "");
                    }
                }
            }

            defectUi.branch =  next.tcBranch(compactor);

            res.addDefect(defectUi);
        }

        return res;
    }

    public void issuesToDefectsLater() {
        scheduler.sheduleNamed("issuesToDefects", this::issuesToDefects, 15, TimeUnit.MINUTES);
    }

    @MonitoredTask(name = "Convert issues to defect")
    protected void issuesToDefects() {
        Stream<Issue> stream = issuesStorage.allIssues();

        long minIssueTs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3);

        //todo wrong to call it twice
        fatBuildDao.init();
        changeDao.init();

        stream
            .filter(issue -> {
                long detected = issue.detectedTs == null ? 0 : issue.detectedTs;

                return detected >= minIssueTs;
            })
            .filter(issue -> {
                String type = issue.type;
                return !IssueType.newContributedTestFailure.code().equals(type);
            })
            .forEach(issue -> {
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

                int[] changes = fatBuild.changes();
                Map<Long, ChangeCompacted> all = changeDao.getAll(srvId, changes);
                Stream<CommitCompacted> stream1 = all.values().stream().map(ChangeCompacted::commitVersion)
                    .map(CommitCompacted::new);

                int tcSrvCodeCid = compactor.getStringId(srvCode);
                defectStorage.merge(tcSrvCodeCid, srvId, fatBuild, stream1.collect(Collectors.toList()),
                    (k, defect) -> {
                        defect.trackedBranchCidSetIfEmpty(trackedBranchCid);

                        defect.computeIfAbsent(fatBuild).addIssue(issueTypeCid, testNameCid);

                        return defect;
                    });

            });

    }
}
