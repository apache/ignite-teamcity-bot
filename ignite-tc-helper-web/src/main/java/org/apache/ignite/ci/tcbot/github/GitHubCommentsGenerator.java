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
package org.apache.ignite.ci.tcbot.github;

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

/**
 * GitHub Pull Request comments generator for TCBot test analysis.
 */
public class GitHubCommentsGenerator {
    /** Max tests to list in a suite section. */
    private static final int MAX_TESTS = 10;

    /**
     * @param buildId TeamCity build id.
     * @return Stable hidden marker for duplicate detection.
     */
    public static String duplicateMarker(int buildId) {
        return duplicateMarker("chainBuildId=" + buildId);
    }

    /**
     * @param analysisSliceKey Analysis slice key.
     * @return Stable hidden marker for duplicate detection.
     */
    public static String duplicateMarker(String analysisSliceKey) {
        return "<!-- tcbot-analysis-comment " + analysisSliceKey + " -->";
    }

    /**
     * @param compactor String compactor.
     * @param suites Suite current status.
     * @param newTestsStatuses New tests status.
     * @param webUrl Build URL.
     * @param buildTypeId Build type ID, for which visa was ordered.
     * @param tcIgnited TC service.
     * @param blockers Count of blockers.
     * @param branchName TC branch name, which was tested.
     * @param baseBranch TC base branch used for comment.
     * @param testedCommitLink Markdown-formatted tested commit.
     * @param analysisSliceKey Analysis slice key.
     * @return GitHub markdown comment.
     */
    public static String generateGitHubComment(
        IStringCompactor compactor,
        List<ShortSuiteUi> suites,
        List<ShortSuiteNewTestsUi> newTestsStatuses,
        String webUrl,
        String buildTypeId,
        ITeamcityIgnited tcIgnited,
        int blockers,
        String branchName,
        String baseBranch,
        String testedCommitLink,
        String analysisSliceKey
    ) {
        BuildTypeRefCompacted bt = tcIgnited.getBuildTypeRef(buildTypeId);
        String suiteNameUsedForVisa = bt != null ? bt.name(compactor) : buildTypeId;
        String baseBranchDisp = Strings.isNullOrEmpty(baseBranch) || ITeamcity.DEFAULT.equals(baseBranch)
            ? "master" : baseBranch;

        StringBuilder res = new StringBuilder();

        res.append(duplicateMarker(analysisSliceKey)).append('\n');
        res.append("### TCBot Test Analysis\n\n");
        res.append("* TeamCity: [").append(escapeLinkText(suiteNameUsedForVisa)).append(" Results](")
            .append(webUrl).append(")\n");
        res.append("* Branch: `").append(escapeCode(branchName)).append("`\n");
        res.append("* Base: `").append(escapeCode(baseBranchDisp)).append("`\n");

        if (!Strings.isNullOrEmpty(testedCommitLink))
            res.append("* Tested commit: ").append(testedCommitLink).append('\n');

        res.append('\n');

        appendBlockers(res, suites, blockers);
        appendNewTests(res, newTestsStatuses);

        return res.toString();
    }

    /**
     * @param res Result.
     * @param suites Suites.
     * @param blockers Blockers count.
     */
    private static void appendBlockers(StringBuilder res, List<ShortSuiteUi> suites, int blockers) {
        res.append("#### Possible Blockers (").append(blockers).append(")\n\n");

        if (blockers == 0) {
            res.append("No blockers found.\n\n");

            return;
        }

        for (ShortSuiteUi suite : suites) {
            if (suite.totalBlockers() == 0)
                continue;

            int totalBlockerTests = suite.testFailures().size();

            res.append("* [").append(escapeLinkText(suite.name)).append("](").append(suite.webToBuild).append("): ")
                .append(totalBlockerTests).append(" tests");

            if (!Strings.isNullOrEmpty(suite.result))
                res.append(' ').append(sanitize(suite.result));

            res.append('\n');

            int cnt = 0;

            if (!Strings.isNullOrEmpty(suite.blockerComment)) {
                res.append("  * ").append(sanitize(suite.blockerComment)).append('\n');
                cnt++;
            }

            for (ShortTestFailureUi failure : suite.testFailures()) {
                if (cnt >= MAX_TESTS) {
                    res.append("  * ... and ").append(totalBlockerTests - cnt).append(" more test blockers\n");

                    break;
                }

                res.append("  * ");

                if (failure.suiteName != null && failure.testName != null) {
                    res.append('`').append(escapeCode(failure.suiteName)).append("`: `")
                        .append(escapeCode(failure.testName)).append('`');
                }
                else
                    res.append('`').append(escapeCode(failure.name)).append('`');

                if (!Strings.isNullOrEmpty(failure.blockerComment))
                    res.append(" - ").append(sanitize(failure.blockerComment));

                res.append('\n');

                cnt++;
            }
        }

        res.append('\n');
    }

    /**
     * @param res Result.
     * @param newTestsStatuses New tests statuses.
     */
    private static void appendNewTests(StringBuilder res, List<ShortSuiteNewTestsUi> newTestsStatuses) {
        int newTestsCount = 0;

        for (ShortSuiteNewTestsUi suite : newTestsStatuses)
            newTestsCount += suite.tests().size();

        res.append("#### New Tests (").append(newTestsCount).append(")\n\n");

        if (newTestsCount == 0) {
            res.append("No new tests found.\n");

            return;
        }

        for (ShortSuiteNewTestsUi suite : newTestsStatuses) {
            if (suite.tests().isEmpty())
                continue;

            int totalNewTests = suite.tests().size();
            res.append("* [").append(escapeLinkText(suite.name)).append("](").append(suite.webToBuild).append("): ")
                .append(totalNewTests).append(" tests\n");

            int cnt = 0;

            for (ShortTestUi test : suite.tests()) {
                if (cnt >= MAX_TESTS) {
                    res.append("  * ... and ").append(totalNewTests - cnt).append(" more new tests\n");

                    break;
                }

                res.append("  * ");

                if (test.suiteName != null && test.testName != null) {
                    res.append('`').append(escapeCode(test.suiteName)).append("`: `")
                        .append(escapeCode(test.testName)).append('`');
                }
                else
                    res.append('`').append(escapeCode(test.name)).append('`');

                res.append(" - ").append(test.status ? "PASSED" : "FAILED").append('\n');

                cnt++;
            }
        }
    }

    /**
     * @param val Value.
     */
    private static String sanitize(String val) {
        return Strings.nullToEmpty(val).replace('\n', ' ').replace('\r', ' ');
    }

    /**
     * @param val Value.
     */
    private static String escapeCode(String val) {
        return sanitize(val).replace("`", "\\`");
    }

    /**
     * @param val Value.
     */
    private static String escapeLinkText(String val) {
        return sanitize(val).replace("[", "\\[").replace("]", "\\]");
    }
}
