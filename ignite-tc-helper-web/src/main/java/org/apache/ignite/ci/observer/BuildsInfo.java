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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;

public class BuildsInfo {
    /** Finished. */
    public static final String FINISHED = "finished";

    /** Server id. */
    public final String srvId;

    /** Build type id. */
    public final String buildTypeId;

    /** Branch name. */
    public final String branchName;

    /** Credentials. */
    public final ICredentialsProv prov;

    /** JIRA ticket full name. */
    public final String ticket;

    /** Finished builds. */
    private final Map<Build, Boolean> finishedBuilds = new HashMap<>();

    /**
     * @param srvId Server id.
     * @param prov Prov.
     * @param ticket Ticket.
     * @param builds Builds.
     */
    public BuildsInfo(String srvId, ICredentialsProv prov, String ticket, Build[] builds) {
        this.srvId = srvId;
        this.prov = prov;
        this.ticket = ticket;
        this.buildTypeId = builds.length > 1 ? "IgniteTests24Java8_RunAll" : builds[0].buildTypeId;
        this.branchName = builds[0].branchName;

        for (Build build : builds)
            finishedBuilds.put(build, false);
    }

    /**
     * @param teamcity Teamcity.
     */
    public boolean isFinished(IAnalyticsEnabledTeamcity teamcity) {
        for (Map.Entry<Build, Boolean> entry : finishedBuilds.entrySet()) {
            if (!entry.getValue()) {
                Build build = teamcity.getBuild(entry.getKey().getId());
                entry.setValue(build.state.equals(FINISHED));
            }
        }

        return !finishedBuilds.containsValue(false);
    }

    /**
     * Return builds count.
     */
    public int buildsCount(){
        return finishedBuilds.size();
    }

    /**
     * Return finished builds count.
     */
    public int finishedBuildsCount(){
        return (int)finishedBuilds.values().stream().filter(v -> v).count();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildsInfo))
            return false;

        BuildsInfo info = (BuildsInfo)o;

        return Objects.equals(srvId, info.srvId) &&
            Objects.equals(buildTypeId, info.buildTypeId) &&
            Objects.equals(branchName, info.branchName) &&
            Objects.equals(prov, info.prov) &&
            Objects.equals(ticket, info.ticket) &&
            Objects.equals(finishedBuilds, info.finishedBuilds);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(srvId, buildTypeId, branchName, prov, ticket, finishedBuilds);
    }
}
