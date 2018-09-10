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

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Chain on particular TC server, which is tracked by the Bot.
 */
@SuppressWarnings("PublicField")
public class ChainAtServerTracked extends ChainAtServer {
    /** Branch identifier by TC identification for REST api */
    @Nonnull public String branchForRest;

    /** Automatic build triggering. */
    @Nullable private Boolean triggerBuild;

    /** Automatic build triggering quiet period in minutes. */
    @Nullable private Integer triggerBuildQuietPeriod;


    /** @return {@link #suiteId} */
    @Nonnull public String getSuiteIdMandatory() {
        checkState(!isNullOrEmpty(suiteId), "Invalid config: suiteId should be filled " + this);
        return suiteId;
    }

    /**
     * @return
     */
    @Nonnull public String getBranchForRestMandatory() {
        checkState(!isNullOrEmpty(branchForRest), "Invalid config: branchForRest should be filled " + this);

        return branchForRest;
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
            Objects.equals(triggerBuild, tracked.triggerBuild) &&
            Objects.equals(triggerBuildQuietPeriod, tracked.triggerBuildQuietPeriod);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(super.hashCode(), branchForRest, triggerBuild, triggerBuildQuietPeriod);
    }

    /**
     * @return {@code True} If automatic build triggering enabled.
     */
    public boolean isTriggerBuild() {
        return triggerBuild == null ? false : triggerBuild;
    }

    /**
     * @return Quiet period in minutes between triggering builds or zero if period is not set and should be ignored.
     */
    public int getTriggerBuildQuietPeriod() {
        return triggerBuildQuietPeriod == null ? 0 : triggerBuildQuietPeriod;
    }
}
