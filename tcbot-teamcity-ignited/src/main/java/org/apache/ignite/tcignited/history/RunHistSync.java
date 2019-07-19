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

package org.apache.ignite.tcignited.history;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistKey;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;
import org.apache.ignite.tcservice.ITeamcity;
import org.apache.ignite.tcbot.common.conf.IBuildParameterSpec;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

/**
 * Calculate required statistic for build if was not already calculated.
 */
public class RunHistSync {
    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /** Config. */
    @Inject private IDataSourcesConfigSupplier cfg;

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

    @Nonnull public Set<Integer> getFilteringParameters(String srvCode) {
        Set<String> importantParameters = new HashSet<>();

        cfg.getTeamcityConfig(srvCode)
            .filteringParameters()
            .stream()
            .map(IBuildParameterSpec::name)
            .forEach(importantParameters::add);

        return importantParameters.stream().map(k -> compactor.getStringId(k)).collect(Collectors.toSet());
    }
}
