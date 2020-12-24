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

package org.apache.ignite.tcbot.engine.boardmute;

public class MutedIssueInfo {
    private int trackedBranchId;

    private String userName;

    private String jiraTicket;

    private String comment;

    private String webUrl;

    public MutedIssueInfo(int trackedBranchId, String userName, String jiraTicket, String comment, String webUrl) {
        this.trackedBranchId = trackedBranchId;
        this.userName = userName;
        this.jiraTicket = jiraTicket;
        this.comment = comment;
        this.webUrl = webUrl;
    }

    public int getTrackedBranchId() {
        return trackedBranchId;
    }

    public void setTrackedBranchId(int trackedBranchId) {
        this.trackedBranchId = trackedBranchId;
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
