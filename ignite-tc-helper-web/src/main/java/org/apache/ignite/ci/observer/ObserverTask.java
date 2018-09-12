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

package org.apache.ignite.ci.observer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.model.hist.FailureSummary;
import org.apache.ignite.ci.web.rest.pr.GetPrTestFailures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.ci.analysis.RunStat.MAX_LATEST_RUNS;
import static org.apache.ignite.ci.util.XmlUtil.xmlEscapeText;

/**
 * Checks observed builds for finished status and comments JIRA ticket.
 */
public class ObserverTask extends TimerTask {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(ObserverTask.class);

    /** Helper. */
    private final ITcHelper helper;

    /** Builds. */
    final Queue<BuildInfo> builds;

    /**
     * @param helper Helper.
     */
    ObserverTask(ITcHelper helper) {
        this.helper = helper;
        builds = new ConcurrentLinkedQueue<>();
    }

    /** {@inheritDoc} */
    @Override public void run() {
        for (BuildInfo info : builds) {
            IAnalyticsEnabledTeamcity teamcity = helper.server(info.srvId, info.prov);
            Build build = teamcity.getBuild(info.build.getId());
            String comment;

            try {
                comment = generateComment(build, info);
            }
            catch (RuntimeException e) {
                logger.error("Exception happened during generating comment for JIRA " +
                    "[build=" + build.getId() + ", errMsg=" + e.getMessage() + ']');

                continue;
            }

            if (build.state.equals("finished")) {
                if (teamcity.commentJiraTicket(info.ticket, comment))
                    builds.remove(info);
            }
        }
    }

    /**
     * @param build Build.
     * @param info Info.
     */
    private String generateComment(Build build, BuildInfo info) {
        StringBuilder res = new StringBuilder();
        TestFailuresSummary summary = GetPrTestFailures.getTestFailuresSummary(
            helper, info.prov, info.srvId, build.getBuildType().getId(), build.branchName,
            "Latest", null, null);

        if (summary != null) {
            for (ChainAtServerCurrentStatus server : summary.servers) {
                if (!server.serverName().equals("apache"))
                    continue;

                Map<String, List<SuiteCurrentStatus>> fails = findFailures(server);

                for (List<SuiteCurrentStatus> suites : fails.values()) {
                    for (SuiteCurrentStatus suite : suites) {
                        res.append("{color:#d04437}").append(suite.name).append("{color}");
                        res.append(" [[tests ").append(suite.failedTests);

                        if (suite.result != null && !suite.result.equals(""))
                            res.append(' ').append(suite.result);

                        res.append('|').append(suite.webToBuild).append("]]\\n");

                        for (TestFailure failure : suite.testFailures) {
                            res.append("* ");

                            if (failure.suiteName != null && failure.testName != null)
                                res.append(failure.suiteName).append(": ").append(failure.testName);
                            else
                                res.append(failure.name);

                            FailureSummary recent = failure.histBaseBranch.recent;

                            if (recent != null) {
                                if (recent.failureRate != null) {
                                    res.append(" - ").append(recent.failureRate).append("% fails in last ")
                                        .append(MAX_LATEST_RUNS).append(" master runs.");
                                }
                                else if (recent.failures != null && recent.runs != null) {
                                    res.append(" - ").append(recent.failures).append(" fails / ")
                                        .append(recent.runs).append(" runs.");
                                }
                            }

                            res.append("\\n");
                        }

                        res.append("\\n");
                    }
                }

                if (res.length() > 0) {
                    res.insert(0, "{panel:title=Possible Blockers|" +
                        "borderStyle=dashed|borderColor=#ccc|titleBGColor=#F7D6C1}\\n")
                        .append("{panel}");
                }
                else {
                    res.append("{panel:title=No blockers found!|" +
                        "borderStyle=dashed|borderColor=#ccc|titleBGColor=#D6F7C1}{panel}");
                }
            }
        }

        res.append("\\n").append("[TeamCity Run All|").append(build.webUrl).append(']');

        return xmlEscapeText(res.toString());
    }

    /**
     * @param srv Server.
     * @return Failures for given server.
     */
    private Map<String, List<SuiteCurrentStatus>> findFailures(ChainAtServerCurrentStatus srv) {
        Map<String, List<SuiteCurrentStatus>> fails = new LinkedHashMap<>();

        fails.put("compilation", new ArrayList<>());
        fails.put("timeout", new ArrayList<>());
        fails.put("exit code", new ArrayList<>());
        fails.put("failed tests", new ArrayList<>());

        for (SuiteCurrentStatus suite : srv.suites) {
            String suiteRes = suite.result.toLowerCase();
            String failType = null;

            if (suiteRes.contains("compilation"))
                failType = "compilation";

            if (suiteRes.contains("timeout"))
                failType = "timeout";

            if (suiteRes.contains("exit code"))
                failType = "exit code";

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
                fails.get(failType).add(suite);
        }

        return fails;
    }
}
