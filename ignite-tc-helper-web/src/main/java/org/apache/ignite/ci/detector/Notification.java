package org.apache.ignite.ci.detector;

import java.util.*;

public class Notification {
    String addr;
    Long ts;

    List<Issue> issues = new ArrayList<>();

    public void addIssue(Issue issueKey) {
        issues.add(issueKey);
    }

    public String toHtml() {
        StringBuilder stringBuilder = new StringBuilder();

        for (Issue next : issues) {
            stringBuilder.append(next.toHtml());
        }

        stringBuilder.append(new Date(ts).toString());

        return stringBuilder.toString();
    }
}
