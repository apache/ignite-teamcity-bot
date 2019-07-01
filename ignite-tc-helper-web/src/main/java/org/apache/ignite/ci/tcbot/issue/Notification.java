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

package org.apache.ignite.ci.tcbot.issue;

import java.util.stream.Stream;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.web.model.Version;
import org.apache.ignite.tcbot.common.util.TimeUtil;

import java.util.*;

import static org.apache.ignite.ci.web.model.Version.GITHUB_REF;

public class Notification {
    private static final String DETECTED_ISSUE = "I've detected some new issue on TeamCity to be handled. " +
        "You are more than welcomed to help.";

    private static final String IF_YOUR_CHANGES = "If your changes can lead to this failure(s): " +
        "We're grateful that you were a volunteer to make the contribution to this project, " +
        "but things change and you may no longer be able to finalize your contribution.";

    private static final String YOUR_ACTION = "Could you respond to this email and indicate if you wish to continue and fix test failures" +
        " or step down and some committer may revert you commit. ";

    private static final String HTC_REF = "https://cwiki.apache.org/confluence/display/IGNITE/How+to+Contribute";

    String addr;
    Long ts;

    Map<Integer, List<Issue>> buildIdToIssue = new TreeMap<>(Comparator.reverseOrder());

    public void addIssue(Issue issue) {
        Integer buildId = issue.issueKey.buildId;

        buildIdToIssue.computeIfAbsent(buildId, b -> new ArrayList<>()).add(issue);
    }

    public String toHtml() {
        StringBuilder sb = new StringBuilder();

        sb.append(messageHeaderHtml());

        for (Map.Entry<Integer, List<Issue>> nextEntry : buildIdToIssue.entrySet()) {
            List<Issue> issues = nextEntry.getValue();

            for (Iterator<Issue> iter = issues.iterator(); iter.hasNext(); ) {
                Issue next = iter.next();
                String color = "blue";

                sb.append("<span style='border-color: ")
                    .append(color)
                    .append("; width:0px; height:0px; display: inline-block; border-width: 4px; color: black; border-style: solid;'></span>");

                sb.append("    ");
                sb.append(next.toHtml(!iter.hasNext()));
                sb.append("<br>");
            }

            sb.append("<br>");
        }

        sb.append(messageTailHtml());

        return sb.toString();
    }

    public boolean involvesChange() {
        return buildIdToIssue.values().stream().flatMap(Collection::stream)
                .anyMatch(issue -> !issue.changes.isEmpty());
    }

    public String toPlainText() {
        StringBuilder sb = new StringBuilder();

        sb.append(messageHeaderPlainText());

        for (Map.Entry<Integer, List<Issue>> nextEntry : buildIdToIssue.entrySet()) {
            List<Issue> issues = nextEntry.getValue();

            for (Iterator<Issue> iter = issues.iterator(); iter.hasNext(); ) {
                Issue next = iter.next();

                sb.append(" *    ");
                sb.append(next.toPlainText(!iter.hasNext()));
                sb.append("\n");
            }

            sb.append("\n");
        }

        sb.append(messageTailPlainText());

        return sb.toString();
    }

    private String messageHeaderHtml() {
        StringBuilder res = new StringBuilder();
        res.append("Hi Igniters,<br><br>");
        res.append(" ");
        res.append(DETECTED_ISSUE);
        res.append("<br><br>");

        if (involvesChange()) {
            res.append(" ");
            res.append(IF_YOUR_CHANGES);
            res.append("<br>");
            res.append(" ");
            res.append(YOUR_ACTION);
            res.append("<br><br>");
        }
        return res.toString();
    }

    private String messageHeaderPlainText() {
        StringBuilder res = new StringBuilder();
        res.append("Hi Igniters,\n\n");
        res.append(" ");
        res.append(DETECTED_ISSUE);
        res.append("\n\n");
        if (involvesChange()) {
            res.append(" ");
            res.append(IF_YOUR_CHANGES);
            res.append("\n");
            res.append(" ");
            res.append(YOUR_ACTION);
            res.append("\n\n");
        }

        return res.toString();
    }

    private String messageTailPlainText() {
        return "\t - Here's a reminder of what contributors were agreed to do " +
            HTC_REF + " \n" +
            "\t - Should you have any questions please contact " + Version.DEFAULT_CONTACT + " \n\n" +
            "Best Regards,\n" +
            "Apache Ignite TeamCity Bot \n" +
            GITHUB_REF + "\n" +
            "Notification generated at " + TimeUtil.timestampToDateTimePrintable(ts) + " \n";
    }

    private String messageTailHtml() {
        return "<ul><li>Here's a reminder of what contributors were agreed to do " +
            "<a href='" + HTC_REF + "'>How to Contribute</a>." + "</li>" +
            "<li>Should you have any questions please contact " + Version.DEFAULT_CONTACT + " </li></ul><br>" +
            "Best Regards,<br>" +
            "<a href='" + GITHUB_REF + "'>Apache Ignite TeamCity Bot<a><br>" +
            "Notification generated at " + TimeUtil.timestampToDateTimePrintable(ts) + "<br>";
    }

    public String countIssues() {
        return "";
    }

    public List<String> toSlackMarkup() {
        List<String> res = new ArrayList<>();

        for (Map.Entry<Integer, List<Issue>> nextEntry : buildIdToIssue.entrySet()) {
            List<Issue> issues = nextEntry.getValue();

            res.add(toSlackMarkup(issues));
        }

        return res;
    }

    private String toSlackMarkup(List<Issue> issues) {
        StringBuilder sb = new StringBuilder();

        sb.append(":warning: ");

        for (Iterator<Issue> iter = issues.iterator(); iter.hasNext(); ) {
            Issue next = iter.next();
            sb.append(next.toSlackMarkup(!iter.hasNext()));

            sb.append("\n");
        }

        return sb.toString();
    }

    public Stream<Issue> allIssues() {
        return this.buildIdToIssue.values().stream().flatMap(Collection::stream);
    }
}
