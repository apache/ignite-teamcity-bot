package org.apache.ignite.ci.detector;

import java.util.*;

public class Issue {
    public String displayType;

    public IssueKey issueKey;

    public List<ChangeUi> changes = new ArrayList<>();

    public Set<String> addressNotified = new TreeSet<>();

    public Issue(IssueKey issueKey) {
        this.issueKey = issueKey;
    }

    public void addChange(String username, String webUrl) {
        changes.add(new ChangeUi(username, webUrl));
    }

    public String toHtml() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(displayType).append(" ").append(issueKey.getTestOrBuildName()).append(" ");
        for (ChangeUi next : changes) {
            stringBuilder.append(next.toHtml()).append(", ");
        }
        stringBuilder.append("<br>");

        return stringBuilder.toString();
    }

    public IssueKey issueKey() {
        return issueKey;
    }
}
