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

package org.apache.ignite.ci.observer;

import java.util.Objects;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;

/**
 *
 */
public class BuildInfo extends Info {
    /** Build. */
    private final Build build;

    /**
     * @param srvId Server id.
     * @param prov Prov.
     * @param ticket Ticket.
     * @param build Build.
     */
    public BuildInfo(String srvId, ICredentialsProv prov, String ticket, Build build) {
        super(srvId, prov, ticket, build.buildTypeId, build.branchName);
        this.build = build;
    }

    /** {@inheritDoc} */
    @Override public boolean isFinished(IAnalyticsEnabledTeamcity teamcity) {
        return teamcity.getBuild(build.getId()).state.equals(FINISHED);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildInfo))
            return false;

        if (!super.equals(o))
            return false;

        BuildInfo info = (BuildInfo)o;

        return Objects.equals(build, info.build);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {

        return Objects.hash(super.hashCode(), build);
    }
}
