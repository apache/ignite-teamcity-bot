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

package org.apache.ignite.ci.tcbot.jira.v3;

import com.google.common.base.Strings;
import com.google.gson.GsonBuilder;
import java.util.List;
import org.apache.ignite.ci.tcbot.jira.v3.adf.BulletList;
import org.apache.ignite.ci.tcbot.jira.v3.adf.Mark;
import org.apache.ignite.ci.tcbot.jira.v3.adf.Panel;
import org.apache.ignite.ci.tcbot.jira.v3.adf.Paragraph;
import org.apache.ignite.ci.tcbot.jira.v3.adf.Root;
import org.apache.ignite.ci.tcbot.jira.v3.adf.Text;
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

public class JiraCommentsGeneratorV3 {
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

        String baseBranchDisp = (Strings.isNullOrEmpty(baseBranch) || ITeamcity.DEFAULT.equals(baseBranch))
            ? "master" :  baseBranch ;

        String suiteNameForComment = jiraEscText(suiteNameUsedForVisa);

        String branchNameForComment = jiraEscText("Branch: [" + branchName + "] ");

        String baseBranchForComment = jiraEscText("Base: [" + baseBranchDisp + "] ");
        String branchVsBaseComment = branchNameForComment + baseBranchForComment;

        // Test failures.
        String panelName = !suites.isEmpty()
            ? branchVsBaseComment + ": Possible Blockers (" + blockers + ')'
            : branchVsBaseComment + ": No blockers found!";
        Panel testFailuresPanel = new Panel(suites.isEmpty() ? Panel.Type.success : Panel.Type.warning, panelName);

        for (ShortSuiteUi suite : suites) {
            Paragraph p = new Paragraph();
            Text suiteName = new Text(jiraEscText(suite.name), Mark.textColor(FAILED_TEST_SUITE_COLOR));

            int suiteBlockers = suite.testFailures().size();
            String suiteRes = "tests " + suiteBlockers;
            if (suite.result != null && !suite.result.isEmpty())
                suiteRes = suiteRes + ' ' + suite.result;

            Text tests = new Text(suiteRes, Mark.textLink(suite.webToBuild));

            p.append(suiteName).append(new Text(" [")).append(tests).append(new Text("]"));

            int cnt = 0;
            BulletList list = new BulletList();
            for (ShortTestFailureUi failure : suite.testFailures()) {
                String item;
                if (failure.suiteName != null && failure.testName != null)
                    item = jiraEscText(failure.suiteName) + ": " + jiraEscText(failure.testName);
                else
                    item = jiraEscText(failure.name);

                item = item + " - " + jiraEscText(failure.blockerComment);

                list.append(item);

                cnt++;
                if (cnt >= 10 && (suite.testFailures().size() - cnt) > 0) {
                    if (suite.testFailures().size() - cnt == 1)
                        list.append("... and 1 new test");
                    else
                        list.append("... and " + (suite.testShortFailures.size() - cnt) + " new tests");
                    break;
                }
            }

            testFailuresPanel.append(p);

            if (!list.isEmpty())
                testFailuresPanel.append(list);
        }

        // New tests
        int newTestsCount = newTestsStatuses.stream().mapToInt(s -> s.tests.size()).sum();
        boolean failedNewTests = newTestsStatuses.stream().flatMap(s -> s.tests().stream()).anyMatch(t -> !t.status);
        String newTestsPanelName = newTestsCount > 0
            ? branchVsBaseComment + ": New tests (" + newTestsCount + ')'
            : branchVsBaseComment + ": No new tests found!";
        Panel newTestsPanel = new Panel(failedNewTests ? Panel.Type.warning : Panel.Type.success, newTestsPanelName);

        for (ShortSuiteNewTestsUi suite : newTestsStatuses) {
            Paragraph p = new Paragraph();
            Text suiteName = new Text(jiraEscText(suite.name), Mark.textColor(NEW_TEST_SUITE_COLOR));

            Text tests = new Text("tests " + suite.tests.size(), Mark.textLink(suite.webToBuild));

            p.append(suiteName).append(new Text(" [")).append(tests).append(new Text("]"));

            int cnt = 0;
            BulletList list = new BulletList();
            for (ShortTestUi test : suite.tests()) {
                String testColor = test.status ? PASSED_TEST_COLOR : FAILED_TEST_COLOR;

                String testName;
                if (test.suiteName != null && test.testName != null)
                    testName = jiraEscText(test.suiteName) + ": " + jiraEscText(test.testName);
                else
                    testName = jiraEscText(test.name);

                testName = testName + " - " + jiraEscText(test.status ? "PASSED" : "FAILED");

                list.append(testName, Mark.textColor(testColor));

                cnt++;
                if (cnt >= 10 && (suite.tests.size() - cnt) > 0) {
                    if (suite.tests.size() - cnt == 1)
                        list.append("... and 1 new test");
                    else
                        list.append("... and " + (suite.tests.size() - cnt) + " new tests");
                    break;
                }
            }

            newTestsPanel.append(p);

            if (!list.isEmpty())
                newTestsPanel.append(list);
        }

        Paragraph linkToBuild = new Paragraph()
            .append(new Text("TeamCity " + suiteNameForComment + " Results", Mark.textLink(webUrl)));

        Root root = new Root();
        root.append(testFailuresPanel);
        root.append(newTestsPanel);
        root.append(linkToBuild);

        return new GsonBuilder().create().toJson(root);
    }
}
