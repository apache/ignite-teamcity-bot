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

package org.apache.ignite.ci.tcbot.jira;

import com.google.common.base.Strings;
import java.util.List;
import org.apache.ignite.ci.tcbot.jira.v2.JiraCommentsGeneratorV2;
import org.apache.ignite.ci.tcbot.jira.v3.JiraCommentsGeneratorV3;
import org.apache.ignite.tcbot.common.conf.JiraApiVersion;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteNewTestsUi;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;

public class JiraCommentsGenerator {
    public static final String FAILED_TEST_SUITE_COLOR = "#d04437";

    public static final String NEW_TEST_SUITE_COLOR = "#00008b";

    public static final String FAILED_TEST_COLOR = "#8b0000";

    public static final String PASSED_TEST_COLOR = "#013220";

    /**
     * @param apiVer Jira API version.
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
        JiraApiVersion apiVer,
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
        switch (apiVer) {
            case V2: return JiraCommentsGeneratorV2.generateJiraComment(
                compactor,
                suites,
                newTestsStatuses,
                webUrl,
                buildTypeId,
                tcIgnited,
                blockers,
                branchName,
                baseBranch);

            case V3: return JiraCommentsGeneratorV3.generateJiraComment(
                compactor,
                suites,
                newTestsStatuses,
                webUrl,
                buildTypeId,
                tcIgnited,
                blockers,
                branchName,
                baseBranch);

            default:
                throw new IllegalArgumentException("Unsupported jira api version [version=" + apiVer + ']');
        }
    }

    /**
     * Escapes text for JIRA.
     * @param txt Txt.
     */
    public static String jiraEscText(String txt) {
        if(Strings.isNullOrEmpty(txt))
            return "";

        return txt.replace('|', '/');
    }
}
