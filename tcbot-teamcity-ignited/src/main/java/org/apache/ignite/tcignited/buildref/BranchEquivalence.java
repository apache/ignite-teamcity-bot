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
package org.apache.ignite.tcignited.buildref;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcservice.ITeamcity;

public class BranchEquivalence {
    /** Default synonyms. */
    private static final List<String> DEFAULT_SYNONYMS
        = Collections.unmodifiableList(
        Lists.newArrayList(ITeamcity.DEFAULT, ITeamcity.REFS_HEADS_MASTER, ITeamcity.MASTER));

    /**
     * @param branchName Branch name.
     */
    @Nonnull
    public static String normalizeBranch(@Nullable String branchName) {
        String branch = branchName == null ? ITeamcity.DEFAULT : branchName;

        if (ITeamcity.REFS_HEADS_MASTER.equals(branch))
            return ITeamcity.DEFAULT;

        if (ITeamcity.MASTER.equals(branch))
            return ITeamcity.DEFAULT;

        return branch;
    }


    public List<String> branchForQuery(@Nullable String branchName) {
        if (ITeamcity.DEFAULT.equals(branchName))
            return DEFAULT_SYNONYMS;
        else
            return Collections.singletonList(branchName);
    }

    public Set<Integer> branchIdsForQuery(@Nullable String branchName, IStringCompactor compactor) {
        List<String> bracnhNameQry = branchForQuery(branchName);
        return bracnhNameQry.stream()
            .map(compactor::getStringIdIfPresent)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
}
