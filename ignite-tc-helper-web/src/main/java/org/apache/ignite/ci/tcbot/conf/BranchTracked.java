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
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
import org.apache.ignite.ci.issue.IssueType;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedChain;
import org.jetbrains.annotations.Nullable;

/**
 * One tracked branch, probably on several servers
 */
public class BranchTracked implements ITrackedBranch {
    /** ID for internal REST and for config file. */
    public String id;

    /** */
    public List<ChainAtServerTracked> chains = new ArrayList<>();

    /** Disable notifications for the following issue types. See {@link IssueType#code()}  */
    @Nullable private List<String> disableIssueTypes = new ArrayList<>();


    /**
     * @return internal identifier of the branch.
     */
    public String name() {
        return id;
    }

    /** */
    public List<ChainAtServerTracked> getChains() {
        return Collections.unmodifiableList(chains);
    }

    /** */
    public Stream<ITrackedChain> chainsStream() {
        return chains.stream().map(t->t);
    }

    /**
     * @
     */
    @NotNull public List<String> disableIssueTypes() {
        return disableIssueTypes == null || disableIssueTypes.isEmpty()
            ? Collections.emptyList()
            : Collections.unmodifiableList(disableIssueTypes);
    }

}
