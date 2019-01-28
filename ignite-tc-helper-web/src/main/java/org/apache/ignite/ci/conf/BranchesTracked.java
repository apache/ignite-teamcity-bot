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

package org.apache.ignite.ci.conf;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.ignite.ci.tcbot.conf.TcServerConfig;

/**
 * Config file for tracked branches.
 */
public class BranchesTracked {
    /** Branches. */
    private List<BranchTracked> branches = new ArrayList<>();

    /** Primary server ID. */
    @Nullable private String primaryServerId;

    /** Additional list Servers to be used for validation of PRs, but not for tracking any branches. */
    private List<TcServerConfig> servers = new ArrayList<>();

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
            servers.stream().map(TcServerConfig::getName))
            .collect(Collectors.toSet());
    }

    public List<BranchTracked> getBranches() {
        return Collections.unmodifiableList(branches);
    }

    public void addBranch(BranchTracked branch) {
        branches.add(branch);
    }

    @Nullable public String primaryServerId() {
        return primaryServerId;
    }

    public Optional<TcServerConfig> getServer(String name) {
        return servers.stream().filter(s->name.equals(s.getName())).findAny();
    }
}
