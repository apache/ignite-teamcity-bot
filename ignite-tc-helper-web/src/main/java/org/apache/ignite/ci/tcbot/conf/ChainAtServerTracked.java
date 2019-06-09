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

import com.google.common.base.Strings;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.ignite.tcbot.engine.conf.ITrackedChain;
import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Chain on particular TC server, which is tracked by the Bot.
 */
@SuppressWarnings("PublicField")
public class ChainAtServerTracked extends ChainAtServer implements ITrackedChain {
    /** Branch identifier by TC identification for REST API. */
    @Nonnull public String branchForRest;

    /** TC identified base branch: null means the same as &lt;default>, master. For not tracked branches. */
    @Nullable public String baseBranchForTc;

    /** Automatic build triggering. */
    @Nullable private Boolean triggerBuild;

    /** Automatic build triggering quiet period in minutes. */
    @Nullable private Integer triggerBuildQuietPeriod;

    /** Build parameters for Triggering. */
    @Nullable private List<BuildParameterSpec> triggerParameters;

    /** @return {@link #suiteId} */
    @Nonnull public String getSuiteIdMandatory() {
        checkState(!isNullOrEmpty(suiteId), "Invalid config: suiteId should be filled " + this);
        return suiteId;
    }

    /**
     * @return branch in TC indentification to queue build results.
     */
    @Nonnull public String getBranchForRestMandatory() {
        checkState(!isNullOrEmpty(branchForRest), "Invalid config: branchForRest should be filled " + this);

        return branchForRest;
    }

    /**
     * @return base (etalon) branch in TC identification t builds
     */
    @Nonnull public Optional<String> tcBaseBranch() {
        if (Strings.isNullOrEmpty(baseBranchForTc))
            return Optional.empty();

        return Optional.of(baseBranchForTc);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        ChainAtServerTracked tracked = (ChainAtServerTracked)o;
        return Objects.equals(branchForRest, tracked.branchForRest) &&
            Objects.equals(baseBranchForTc, tracked.baseBranchForTc) &&
            Objects.equals(triggerBuild, tracked.triggerBuild) &&
            Objects.equals(triggerBuildQuietPeriod, tracked.triggerBuildQuietPeriod) &&
            Objects.equals(triggerParameters, tracked.triggerParameters);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(super.hashCode(), branchForRest, baseBranchForTc, triggerBuild, triggerBuildQuietPeriod, triggerParameters);
    }

    /**
     * @return {@code True} If automatic build triggering enabled.
     */
    public boolean triggerBuild() {
        return triggerBuild == null ? false : triggerBuild;
    }

    /**
     * @return Quiet period in minutes between triggering builds or zero if period is not set and should be ignored.
     */
    public int triggerBuildQuietPeriod() {
        return triggerBuildQuietPeriod == null ? 0 : triggerBuildQuietPeriod;
    }

    @Override
    public String tcSuiteId() {
        return suiteId;
    }

    /**
     * @return Map with parameter values for current run.
     */
    @Nonnull public Map<String, Object> generateBuildParameters() {
        if (triggerParameters == null || triggerParameters.isEmpty())
            return Collections.emptyMap();

        Map<String, Object> values = new HashMap<>();

        triggerParameters.forEach(
            p -> {
                String name = p.name();
                Object val = p.generateValue();

                if (!Strings.isNullOrEmpty(name) && val != null)
                    values.put(name, val);
            }
        );

        return values;
    }

    public Stream<String> buildParametersKeys() {
        if (triggerParameters == null || triggerParameters.isEmpty())
            return Stream.empty();

        return triggerParameters.stream().map(BuildParameterSpec::name);
    }

    @Override
    public String tcBranch() {
        return branchForRest;
    }

    /**
     * @return Server ID to access configs within IDataSourceCfgSupplier.
     */
    @Override
    public String serverCode() {
        return serverId;
    }

}
