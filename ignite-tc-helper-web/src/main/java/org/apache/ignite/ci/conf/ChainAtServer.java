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

/**
 * Created by Дмитрий on 09.11.2017.
 */
public class ChainAtServer {
    /** Server ID to access config files within helper. */
    @Nullable public String serverId;

    /** Suite identifier by teamcity identification for root chain. */
    @Nonnull public String suiteId;

    /** Automatic build triggering. */
    @Nullable private Boolean triggerBuild;

    /** Automatic build triggering quiet period in minutes. */
    @Nullable private Integer triggerBuildQuietPeriod;

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ChainAtServer server = (ChainAtServer)o;
        return Objects.equals(serverId, server.serverId) &&
            Objects.equals(suiteId, server.suiteId) &&
            Objects.equals(triggerBuild, server.triggerBuild) &&
            Objects.equals(triggerBuildQuietPeriod, server.triggerBuildQuietPeriod);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(serverId, suiteId, triggerBuild, triggerBuildQuietPeriod);
    }

    /**
     * @return Server ID to access config files within helper.
     */
    @Nullable public String getServerId() {
        return serverId;
    }

    /**
     * @return {@code True} If automatic build triggering enabled.
     */
    @Nonnull public boolean isTriggerBuild() {
        return triggerBuild == null ? false : triggerBuild;
    }

    /**
     * @return Quiet period in minutes between triggering builds or zero if period is not set and should be ignored.
     */
    @Nonnull public int getTriggerBuildQuietPeriod() {
        return triggerBuildQuietPeriod == null ? 0 : triggerBuildQuietPeriod;
    }
}
