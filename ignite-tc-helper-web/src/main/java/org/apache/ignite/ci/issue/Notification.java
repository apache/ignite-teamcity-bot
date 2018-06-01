package org.apache.ignite.ci.issue;

import java.util.*;

public class Notification {
    String addr;
    Long ts;

    List<Issue> issues = new ArrayList<>();

    public void addIssue(Issue issueKey) {
        issues.add(issueKey);
    }

    public String toHtml() {
        StringBuilder sb = new StringBuilder();

        sb.append(messageHeader());

        for (Issue next : issues) {
            String color = "blue";

            sb.append(
                "<span style='border-color: " + color + "; width:6px; height:6px; display: inline-block; border-width: 4px; color: black; border-style: solid;'></span>"
            );

            sb.append("    ");
            sb.append(next.toHtml());
            sb.append("<br><br>");
        }

        sb.append(messageTail());

        return sb.toString();
    }

    private String messageHeader() {
        StringBuilder sb = new StringBuilder();

        sb.append("Hi Ignite Developer,<br><br>");

        sb.append("I am MTCGA.Bot, and I've detected some issue on TeamCity to be addressed.<br>");

        sb.append("I hope you can help here because recently you've done some contributions, which may have relation to failure.<br><br>");

        return sb.toString();
    }

    private String messageTail() {

        //GetTrackedBranches getTrackedBranches = new GetTrackedBranches();
        //Version version = getTrackedBranches.version();

        StringBuilder sb = new StringBuilder();

        sb.append("<ul><li>If your changes can led to this failure(s), please create issue with label MakeTeamCityGreenAgain and assign it to you.");

        sb.append("<ul><li>If you have fix, please set ticket to PA state and write to dev list fix is ready</li>");
        sb.append("<li>For case fix will require some time please mute test and set label Muted_Test to issue</li>");
        sb.append("</ul></li>");

        sb.append("<li>If you know which change caused failure please contact change author directly</li>");
        sb.append("<li>If you don't know which change caused failure please send message to dev list to find out</li></ul><br>");

        sb.append("Should you have any questions please contact dpavlov@apache.org or write to dev.list<br><br>");

        sb.append("BR,<br> MTCGA.Bot<br>");

        sb.append("Notification generated at ").append(new Date(ts).toString()).append( "<br>");
        return sb.toString();
    }

}
