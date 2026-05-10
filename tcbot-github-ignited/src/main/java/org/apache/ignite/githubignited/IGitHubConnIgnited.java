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
package org.apache.ignite.githubignited;

import java.util.List;
import org.apache.ignite.ci.github.GitHubIssueComment;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public interface IGitHubConnIgnited {
    /** Cache name for storing GitHub Prs. */
    public static final String GIT_HUB_PR = "gitHubPr";

    /** Cache name for storing GitHub Branches. */
    public static final String GIT_HUB_BRANCHES = "gitHubBranch";

    /** Cache name for storing GitHub users. */
    public static final String GIT_HUB_USERS = "gitHubUsers";

    /**
     * @return Config of GH project.
     */
    public IGitHubConfig config();

    /**
     * @return list of open pull requests
     */
    public List<PullRequest> getPullRequests();

    /**
     * Reloads recently updated pull requests from GitHub immediately.
     *
     * @return Reload summary.
     */
    public String refreshPullRequests();

    /**
     * Loads recently updated pull requests, including closed/merged PRs.
     *
     * @param lookbackDays Number of recent days.
     * @return Recent pull requests.
     */
    public List<PullRequest> getRecentPullRequests(int lookbackDays);

    /**
     * Reloads branches from GitHub immediately.
     *
     * @return Reload summary.
     */
    public String refreshBranches();

    /** */
    public PullRequest getPullRequest(int prNum);

    /**
     * @param prNum Pull request number.
     * @return Pull request issue comments.
     */
    public List<GitHubIssueComment> getIssueComments(int prNum);

    /**
     * Publishes pull request issue comment.
     *
     * @param prNum Pull request number.
     * @param body Comment markdown.
     * @return {@code True} if comment was posted.
     */
    public default boolean postIssueComment(int prNum, String body) {
        return postIssueCommentError(prNum, body) == null;
    }

    /**
     * Publishes pull request issue comment.
     *
     * @param prNum Pull request number.
     * @param body Comment markdown.
     * @return {@code null} if comment was posted, otherwise detailed error.
     */
    @Nullable public String postIssueCommentError(int prNum, String body);

    /** {@inheritDoc} */
    public List<String> getBranches();

    /**
     * Prefix to be added to git branch instead of {@link IJiraServerConfig#branchNumPrefix()}. Usually it is a lower
     * case of JIRA branch mention, e.. JIRA branch num is 'IGNITE-', and git is 'ignite-'
     */
    public String gitBranchPrefix();
}
