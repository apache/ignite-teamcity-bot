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
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;

/**
 *
 */
public interface IGitHubConnIgnited {
    /** Cache name for storing GitHub Prs. */
    public static final String GIT_HUB_PR = "gitHubPr";

    /** Cache name for storing GitHub Branches. */
    public static final String GIT_HUB_BRANCHES = "gitHubBranch";

    /**
     * @return Config of GH project.
     */
    public IGitHubConfig config();

    /**
     * @return list of open pull requests
     */
    public List<PullRequest> getPullRequests();

    /** */
    public PullRequest getPullRequest(int prNum);

    /** {@inheritDoc} */
    public List<String> getBranches();

    /**
     * Send POST request with given body.
     *
     * @param url Url.
     * @param body Request body.
     */
    public void notifyGit(String url, String body);

    /**
     * Prefix to be added to git branch instead of {@link IJiraServerConfig#branchNumPrefix()}. Usually it is a lower
     * case of JIRA branch mention, e.. JIRA branch num is 'IGNITE-', and git is 'ignite-'
     */
    public String gitBranchPrefix();
}
