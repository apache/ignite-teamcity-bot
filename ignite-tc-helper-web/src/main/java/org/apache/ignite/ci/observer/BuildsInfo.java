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

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;

/**
 *
 */
public class BuildsInfo {
    /** */
    public static final String FINISHED_STATE = "finished";

    /** */
    public static final String RUNNING_STATE = "running";

    /** */
    public static final String FINISHED_WITH_FAILURES_STATE = "finished with failures";

    /** */
    public final String userName;

    /** Server id. */
    public final String srvId;

    /** Build type id. */
    public final String buildTypeId;

    /** Branch name. */
    public final String branchName;

    /** JIRA ticket full name. */
    public final String ticket;

    /** */
    public final Date date;

    /** Finished builds. */
    private final Map<Integer, Boolean> finishedBuilds = new HashMap<>();

    /**
     * @param srvId Server id.
     * @param prov Prov.
     * @param ticket Ticket.
     * @param builds Builds.
     */
    public BuildsInfo(String srvId, ICredentialsProv prov, String ticket, String branchName, Build... builds) {
        this.userName = prov.getUser(srvId);
        this.date = Calendar.getInstance().getTime();
        this.srvId = srvId;
        this.ticket = ticket;
        this.branchName = branchName;
        this.buildTypeId = builds.length == 1 ? builds[0].buildTypeId : "IgniteTests24Java8_RunAll";

        for (Build build : builds)
            finishedBuilds.put(build.getId(), false);
    }

    /**
     * @param teamcity Teamcity.
     */
    public String getState(IAnalyticsEnabledTeamcity teamcity) {
        for (Map.Entry<Integer, Boolean> entry : finishedBuilds.entrySet()) {
            if (entry.getValue() == null)
                return FINISHED_WITH_FAILURES_STATE;

            if (!entry.getValue()) {
                Build build = teamcity.getBuild(entry.getKey());

                if (build.isFinished()) {
                    if (build.isUnknown()) {
                        entry.setValue(null);

                        return FINISHED_WITH_FAILURES_STATE;
                    }

                    entry.setValue(true);
                }
            }
        }

        return finishedBuilds.containsValue(false) ? RUNNING_STATE : FINISHED_STATE;
    }

    /**
     * @param teamcity Teamcity.
     */
    public boolean isFinished(IAnalyticsEnabledTeamcity teamcity) {
        return FINISHED_STATE.equals(getState(teamcity));
    }

    /**
     * @param teamcity Teamcity.
     */
    public boolean isFinishedWithFailures(IAnalyticsEnabledTeamcity teamcity) {
        return FINISHED_WITH_FAILURES_STATE.equals(getState(teamcity));
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
            Objects.equals(ticket, info.ticket) &&
            Objects.equals(finishedBuilds.keySet(), info.finishedBuilds.keySet()) &&
            Objects.equals(date, info.date);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(srvId, buildTypeId, branchName, ticket, finishedBuilds.keySet(), date);
    }
}
