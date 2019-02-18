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

package org.apache.ignite.ci.tcbot.conf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServer;

/**
 * Config file for tracked branches.
 */
public class BranchesTracked {
    /** Branches. */
    private List<BranchTracked> branches = new ArrayList<>();

    /** Primary server ID. */
    @Nullable private String primaryServerCode;

    /** Additional list Servers to be used for validation of PRs, but not for tracking any branches. */
    private List<TcServerConfig> tcServers = new ArrayList<>();

    /** JIRA config to be used . */
    private List<JiraServerConfig> jiraServers = new ArrayList<>();

    /** JIRA config to be used . */
    private List<GitHubConfig> gitHubConfigs = new ArrayList<>();

    /**
     * @return list of internal identifiers of branch.
     */
    public List<String> getIds() {
        return branches.stream().map(BranchTracked::getId).collect(Collectors.toList());
    }

    /**
     * Get Unique suites involved into tracked branches
     */
    public Set<ChainAtServer> getSuitesUnique() {
        return branches.stream()
            .flatMap(BranchTracked::getChainsStream)
            .map(ChainAtServer::new) // to produce object with another equals
            .collect(Collectors.toSet());
    }

    public Optional<BranchTracked> get(String branch) {
        return branches.stream().filter(b -> branch.equals(b.getId())).findAny();
    }

    public BranchTracked getBranchMandatory(String branch) {
        return get(branch).orElseThrow(() -> new RuntimeException("Branch not found: " + branch));
    }

    /**
     *
     */
    public Set<String> getServerIds() {
        Stream<String> srvsInTracked = branches.stream()
            .flatMap(BranchTracked::getChainsStream)
            .map(ChainAtServer::getServerId);

        return Stream.concat(srvsInTracked,
            tcServers.stream().map(TcServerConfig::getCode))
            .collect(Collectors.toSet());
    }

    public List<BranchTracked> getBranches() {
        return Collections.unmodifiableList(branches);
    }

    public void addBranch(BranchTracked branch) {
        branches.add(branch);
    }

    /**
     * @return Primary server code.
     */
    @Nullable public String primaryServerCode() {
        return primaryServerCode;
    }

    Optional<TcServerConfig> getTcConfig(String code) {
        return tcServers.stream().filter(s -> code.equals(s.getCode())).findAny();
    }

    Optional<JiraServerConfig> getJiraConfig(String code) {
        return jiraServers.stream().filter(s -> code.equals(s.getCode())).findAny();
    }

    public Optional<GitHubConfig> getGitHubConfig(String code) {
        return gitHubConfigs.stream().filter(s -> code.equals(s.getCode())).findAny();
    }
}
