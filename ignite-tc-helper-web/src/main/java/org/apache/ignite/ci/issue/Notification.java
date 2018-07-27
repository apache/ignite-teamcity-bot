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

package org.apache.ignite.ci.issue;

import java.util.*;

public class Notification {
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
        return "Hi Ignite Developer,<br><br>" +
            "I am MTCGA.Bot, and I've detected some issue on TeamCity to be addressed. I hope you can help.<br><br>";
    }

    private String messageHeaderPlainText() {
        return "Hi Ignite Developer,\n\n" +
            "I am MTCGA.Bot, and I've detected some issue on TeamCity to be addressed. I hope you can help.\n\n";
    }


    private String messageTailPlainText() {
        return "\t- If your changes can led to this failure(s), please create issue with label MakeTeamCityGreenAgain and assign it to you.\n" +
            "\t-- If you have fix, please set ticket to PA state and write to dev list fix is ready \n" +
            "\t-- For case fix will require some time please mute test and set label Muted_Test to issue \n" +
            "\t- If you know which change caused failure please contact change author directly\n" +
            "\t- If you don't know which change caused failure please send message to dev list to find out\n" +
            "Should you have any questions please contact dpavlov@apache.org or write to dev.list \n" +
            "Best Regards,\nMTCGA.Bot \n" +
            "Notification generated at " + new Date(ts).toString() + " \n";
    }


    private String messageTailHtml() {
        return "<ul><li>If your changes can led to this failure(s), please create issue with label MakeTeamCityGreenAgain and assign it to you." +
            "<ul><li>If you have fix, please set ticket to PA state and write to dev list fix is ready</li>" +
            "<li>For case fix will require some time please mute test and set label Muted_Test to issue</li>" +
            "</ul></li>" +
            "<li>If you know which change caused failure please contact change author directly</li>" +
            "<li>If you don't know which change caused failure please send message to dev list to find out</li></ul><br>" +
            "Should you have any questions please contact dpavlov@apache.org or write to dev.list<br><br>" +
            "Best Regards,<br>MTCGA.Bot<br>" +
            "Notification generated at " + new Date(ts).toString() + "<br>";
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

}
