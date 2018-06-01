package org.apache.ignite.ci.issue;

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
        StringBuilder sb = new StringBuilder();
        sb.append(displayType).append(" ").append(issueKey.getTestOrBuildName()).append(" ");

        sb.append("Changes may led to failure were done by ");

        for (Iterator<ChangeUi> iterator = changes.iterator(); iterator.hasNext(); ) {
            ChangeUi next = iterator.next();
            sb.append(next.toHtml());

            if(iterator.hasNext())
                sb.append(", ");
        }

        return sb.toString();
    }

    public IssueKey issueKey() {
        return issueKey;
    }
}
