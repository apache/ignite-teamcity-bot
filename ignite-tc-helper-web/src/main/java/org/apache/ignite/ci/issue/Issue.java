package org.apache.ignite.ci.issue;

import java.util.*;
import org.jetbrains.annotations.Nullable;

public class Issue {
    public String displayType;

    @Nullable
    public String trackedBranchName;

    public IssueKey issueKey;

    public List<ChangeUi> changes = new ArrayList<>();

    public Set<String> addressNotified = new TreeSet<>();

    @Nullable
    public String webUrl;

    @Nullable
    public String displayName;

    public Issue(IssueKey issueKey) {
        this.issueKey = issueKey;
    }

    public void addChange(String username, String webUrl) {
        changes.add(new ChangeUi(username, webUrl));
    }

    public String toHtml(boolean includeChangesInfo) {
        StringBuilder sb = new StringBuilder();

        sb.append(displayType);

        if (trackedBranchName != null)
            sb.append(" in ").append(trackedBranchName);

        sb.append(" ");

        if (webUrl != null)
            sb.append("<a href='").append(webUrl).append("'>").append(getDisplayName()).append("</a>");
        else
            sb.append(getDisplayName());


        if(includeChangesInfo) {
            if (changes.isEmpty())
                sb.append(" No changes in build");
            else {

                sb.append(" Changes may led to failure were done by ");

                for (Iterator<ChangeUi> iter = changes.iterator(); iter.hasNext(); ) {
                    ChangeUi next = iter.next();
                    sb.append(next.toHtml());

                    if (iter.hasNext())
                        sb.append(", ");
                }
            }
        }

        return sb.toString();
    }

    public IssueKey issueKey() {
        return issueKey;
    }

    public String toSlackMarkup(boolean includeChangesInfo) {
        StringBuilder sb = new StringBuilder();

        sb.append(displayType);

        if (trackedBranchName != null)
            sb.append(" in ").append(trackedBranchName);

        sb.append(" ");

        String name = getDisplayName();

        if (webUrl != null)
            sb.append("<").append(webUrl).append("|").append(name).append(">");
        else
            sb.append(name);

        if(includeChangesInfo) {
            if (changes.isEmpty())
                sb.append("\n No changes in build");
            else {

                sb.append("\n Changes may led to failure were done by ");

                for (Iterator<ChangeUi> iter = changes.iterator(); iter.hasNext(); ) {
                    ChangeUi next = iter.next();
                    sb.append(next.toSlackMarkup());

                    if (iter.hasNext())
                        sb.append(", ");
                }
            }
        }

        return sb.toString();
    }

    public String getDisplayName() {
        if(displayName==null)
            return issueKey.getTestOrBuildName();

        return displayName;
    }
}
