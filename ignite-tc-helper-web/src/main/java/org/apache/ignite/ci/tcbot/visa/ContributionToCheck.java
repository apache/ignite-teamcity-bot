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
package org.apache.ignite.ci.tcbot.visa;

/**
 * Short version of contribution status. Full version is placed in {@link ContributionCheckStatus}.
 */
@SuppressWarnings("PublicField") public class ContributionToCheck {
    /**
     * Pr number. Negative value imples branch number (with appropriate prefix from GH config), value from {@link
     * #tcBranchName}.
     */
    public Integer prNumber;

    /** Pr title. */
    public String prTitle;

    /** Pr author. */
    public String prAuthor;

    /** Pr author avatar url. */
    public String prAuthorAvatarUrl;

    /** Pr html url. */
    public String prHtmlUrl;

    /** Pr head commit hash (first 7 hexes). */
    public String prHeadCommit;

    /**
     * Branch Name for Team City. Always a branch for PR_less contribution,
     * for PRs filled only if there are builds found for TC.
     */
    public String tcBranchName;

    /** JIRA issue without server URL, but with project name */
    public String jiraIssueId;

    /** JIRA issue statusName */
    public String jiraStatusName;

    /** Pr time update. */
    public String prTimeUpdate;

    /** JIRA ticket url */
    public String jiraIssueUrl;
}
