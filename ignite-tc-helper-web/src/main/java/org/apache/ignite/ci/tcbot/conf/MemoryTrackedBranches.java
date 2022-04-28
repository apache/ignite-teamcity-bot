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

import org.apache.ignite.tcbot.engine.conf.BranchTracked;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranchesConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Inmem only implementation for tests
 */
public class MemoryTrackedBranches implements ITrackedBranchesConfig {
    /**
     * Branches.
     */
    private List<BranchTracked> branches = new ArrayList<>();

    @Override
    public Stream<ITrackedBranch> branchesStream() {
        return branches.stream().map(t -> t);
    }


    public void addBranch(BranchTracked branch) {
        branches.add(branch);
    }
}
