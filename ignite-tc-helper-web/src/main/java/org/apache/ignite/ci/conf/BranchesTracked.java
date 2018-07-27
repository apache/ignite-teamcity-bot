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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by Дмитрий on 05.11.2017.
 */
public class BranchesTracked {
    public List<BranchTracked> branches = new ArrayList<>();

    public List<String> getIds() {
        return branches.stream().map(BranchTracked::getId).collect(Collectors.toList());
    }

    public Set<ChainAtServer> chainAtServers() {
        return branches.stream().flatMap(BranchTracked::getChainsStream).collect(Collectors.toSet());
    }

    public Optional<BranchTracked> get(String branch) {
        return branches.stream().filter(b -> branch.equals(b.getId())).findAny();
    }

    public BranchTracked getBranchMandatory(String branch) {
        return get(branch).orElseThrow(() -> new RuntimeException("Branch not found: " + branch));
    }

    public Set<String> getServerIds() {
        return branches.stream().flatMap(BranchTracked::getChainsStream).map(ChainAtServer::getServerId).collect(Collectors.toSet());
    }
}
