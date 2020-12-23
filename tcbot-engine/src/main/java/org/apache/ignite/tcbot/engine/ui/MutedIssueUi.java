package org.apache.ignite.tcbot.engine.ui;

import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.tcbot.engine.issue.IssueType;

public class MutedIssueUi {
    public int tcSrvId;

    /** Test name */
    public String name;

    public String branchName;

    public String trackedBranchName;

    public String issueType;

    public String userName;

    public String jiraTicket;

    public String comment;

    public String webUrl;

    public int getTcSrvId() {
        return tcSrvId;
    }

    public void setTcSrvId(int tcSrvId) {
        this.tcSrvId = tcSrvId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getTrackedBranchName() {
        return trackedBranchName;
    }

    public void setTrackedBranchName(String trackedBranchName) {
        this.trackedBranchName = trackedBranchName;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getJiraTicket() {
        return jiraTicket;
    }

    public void setJiraTicket(String jiraTicket) {
        this.jiraTicket = jiraTicket;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }
}
