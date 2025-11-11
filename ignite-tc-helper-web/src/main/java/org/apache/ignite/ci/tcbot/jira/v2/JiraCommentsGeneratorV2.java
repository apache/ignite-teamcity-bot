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

package org.apache.ignite.ci.tcbot.jira.v2;

import com.google.common.base.Strings;
import java.util.List;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteNewTestsUi;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteUi;
import org.apache.ignite.tcbot.engine.ui.ShortTestFailureUi;
import org.apache.ignite.tcbot.engine.ui.ShortTestUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcservice.ITeamcity;

import static org.apache.ignite.ci.tcbot.jira.JiraCommentsGenerator.FAILED_TEST_COLOR;
import static org.apache.ignite.ci.tcbot.jira.JiraCommentsGenerator.FAILED_TEST_SUITE_COLOR;
import static org.apache.ignite.ci.tcbot.jira.JiraCommentsGenerator.NEW_TEST_SUITE_COLOR;
import static org.apache.ignite.ci.tcbot.jira.JiraCommentsGenerator.PASSED_TEST_COLOR;
import static org.apache.ignite.ci.tcbot.jira.JiraCommentsGenerator.jiraEscText;
import static org.apache.ignite.tcservice.util.XmlUtil.xmlEscapeText;

public class JiraCommentsGeneratorV2 {
    /**
     * @param compactor String compactor.
     * @param suites Suite Current Status.
     * @param webUrl Build URL.
     * @param buildTypeId Build type ID, for which visa was ordered.
     * @param tcIgnited TC service.
     * @param blockers Count of blockers.
     * @param branchName TC Branch name, which was tested.
     * @param baseBranch TC Base branch used for comment
     * @return Comment, which should be sent to the JIRA ticket.
     */
    public static String generateJiraComment(
        IStringCompactor compactor,
        List<ShortSuiteUi> suites,
        List<ShortSuiteNewTestsUi> newTestsStatuses,
        String webUrl,
        String buildTypeId,
        ITeamcityIgnited tcIgnited,
        int blockers,
        String branchName,
        String baseBranch
    ) {
        BuildTypeRefCompacted bt = tcIgnited.getBuildTypeRef(buildTypeId);

        String suiteNameUsedForVisa = (bt != null ? bt.name(compactor) : buildTypeId);

        StringBuilder res = new StringBuilder();

        String baseBranchDisp = (Strings.isNullOrEmpty(baseBranch) || ITeamcity.DEFAULT.equals(baseBranch))
            ? "master" :  baseBranch ;
        for (ShortSuiteUi suite : suites) {
            res.append("{color:" + FAILED_TEST_SUITE_COLOR + "}");

            res.append(jiraEscText(suite.name)).append("{color}");

            int totalBlockerTests = suite.testFailures().size();
            res.append(" [[tests ").append(totalBlockerTests);

            if (suite.result != null && !suite.result.isEmpty())
                res.append(' ').append(suite.result);

            res.append('|').append(suite.webToBuild).append("]]\\n");

            int cnt = 0;

            for (ShortTestFailureUi failure : suite.testFailures()) {
                res.append("* ");

                if (failure.suiteName != null && failure.testName != null)
                    res.append(jiraEscText(failure.suiteName)).append(": ").append(jiraEscText(failure.testName));
                else
                    res.append(jiraEscText(failure.name));

                res.append(" - ").append(jiraEscText(failure.blockerComment));

                res.append("\\n");

                cnt++;
                if (cnt > 10) {
                    res.append("... and ").append(totalBlockerTests - cnt).append(" tests blockers\\n");

                    break;
                }
            }

            res.append("\\n");
        }

        StringBuilder newTests = new StringBuilder();

        int newTestsCount = 0;

        int failedNewTestsCount = 0;

        for (ShortSuiteNewTestsUi suite : newTestsStatuses) {
            newTests.append("{color:" + NEW_TEST_SUITE_COLOR + "}");

            newTests.append(jiraEscText(suite.name)).append("{color}");

            int totalNewTests = suite.tests.size();
            newTests.append(" [[tests ").append(totalNewTests);

            int cnt = 0;

            newTestsCount += suite.tests().size();

            newTests.append('|').append(suite.webToBuild).append("]]\\n");

            for (ShortTestUi test : suite.tests()) {
                String testColor;
                if (test.status)
                    testColor = PASSED_TEST_COLOR;
                else {
                    testColor = FAILED_TEST_COLOR;
                    failedNewTestsCount++;
                }

                newTests.append("* ");

                newTests.append(String.format("{color:%s}", testColor));

                if (test.suiteName != null && test.testName != null)
                    newTests.append(jiraEscText(test.suiteName)).append(": ").append(jiraEscText(test.testName));
                else
                    newTests.append(jiraEscText(test.name));

                newTests.append(" - ").append(jiraEscText(test.status ? "PASSED" : "FAILED"));

                newTests.append("{color}");

                newTests.append("\\n");

                cnt++;
                if (cnt > 10) {
                    newTests.append("... and ").append(totalNewTests - cnt).append(" new tests\\n");

                    break;
                }
            }

            newTests.append("\\n");
        }

        String suiteNameForComment = jiraEscText(suiteNameUsedForVisa);

        String branchNameForComment = jiraEscText("Branch: [" + branchName + "] ");

        String baseBranchForComment = jiraEscText("Base: [" + baseBranchDisp + "] ");
        String branchVsBaseComment = branchNameForComment + baseBranchForComment;

        if (res.length() > 0) {
            String hdrPanel = "{panel:title=" + branchVsBaseComment + ": Possible Blockers (" + blockers + ")|" +
                "borderStyle=dashed|borderColor=#ccc|titleBGColor=#F7D6C1}\\n";

            res.insert(0, hdrPanel)
                .append("{panel}");
        }
        else {
            res.append("{panel:title=").append(branchVsBaseComment).append(": No blockers found!|")
                .append("borderStyle=dashed|borderColor=#ccc|titleBGColor=#D6F7C1}{panel}");
        }

        if (newTests.length() > 0) {
            String bgColor;
            if (failedNewTestsCount > 0)
                bgColor = "#F7D6C1";
            else
                bgColor = "#D6F7C1";
            String hdrPanel = "{panel:title=" + branchVsBaseComment + ": New Tests (" + newTestsCount + ")|" +
                "borderStyle=dashed|borderColor=#ccc|titleBGColor=" + bgColor + "}\\n";

            newTests.insert(0, hdrPanel)
                .append("{panel}");
        }
        else {
            newTests.append("{panel:title=").append(branchVsBaseComment).append(": No new tests found!|")
                .append("borderStyle=dashed|borderColor=#ccc|titleBGColor=#F7D6C1}{panel}");
        }

        res.append("\\n").append(newTests).append("\\n").append("[TeamCity *").append(suiteNameForComment).append("* Results|").append(webUrl).append(']');

        return xmlEscapeText(res.toString());
    }
}
