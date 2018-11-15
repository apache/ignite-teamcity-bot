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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.ContributionKey;

/**
 *
 */
public class BuildsInfo {
    /** */
    public static final String FINISHED_STATUS = "finished";

    /** */
    public static final String RUNNING_STATUS = "running";

    /** */
    public static final String CANCELLED_STATUS = "cancelled";

    /** */
    public final String userName;

    /** Server id. */
    public final String srvId;

    /** Build type id. */
    public final String buildTypeId;

    /** Branch name. */
    public final String branchForTc;

    /** JIRA ticket full name. */
    public final String ticket;

    /** */
    public final Date date;

    /** Finished builds. */
    private final List<Integer> builds = new ArrayList<>();

    /** */
    public BuildsInfo(CompactBuildsInfo compactBuildsInfo, IStringCompactor strCompactor) {
        this.userName = strCompactor.getStringFromId(compactBuildsInfo.userName());
        this.date = compactBuildsInfo.date();
        this.srvId = strCompactor.getStringFromId(compactBuildsInfo.srvId());
        this.ticket = strCompactor.getStringFromId(compactBuildsInfo.ticket());
        this.branchForTc = strCompactor.getStringFromId(compactBuildsInfo.branchForTc());
        this.buildTypeId = strCompactor.getStringFromId(compactBuildsInfo.buildTypeId());
        this.builds.addAll(compactBuildsInfo.getBuilds());
    }

    /**
     * @param srvId Server id.
     * @param prov Prov.
     * @param branchForTc Branch for TC.
     * @param ticket Ticket.
     * @param builds Builds.
     */
    public BuildsInfo(String srvId, ICredentialsProv prov, String ticket, String branchForTc, Build... builds) {
        this.userName = prov.getUser(srvId);
        this.date = Calendar.getInstance().getTime();
        this.srvId = srvId;
        this.ticket = ticket;
        this.branchForTc = branchForTc;
        this.buildTypeId = builds.length == 1 ? builds[0].buildTypeId : "IgniteTests24Java8_RunAll";

        for (Build build : builds)
            this.builds.add(build.getId());
    }

    /**
     * @param teamcity Teamcity.
     */
    public String getState(IAnalyticsEnabledTeamcity teamcity) {
        boolean isFinished = true;

        for (Integer id : builds) {
            Build build = teamcity.getBuild(id);

            if (build.isUnknown())
                return CANCELLED_STATUS;

            if (!build.isFinished())
                isFinished = false;
        }

        return isFinished ? FINISHED_STATUS : RUNNING_STATUS;
    }

    /**
     * @param teamcity Teamcity.
     */
    public boolean isFinished(IAnalyticsEnabledTeamcity teamcity) {
        return FINISHED_STATUS.equals(getState(teamcity));
    }

    /**
     * @param teamcity Teamcity.
     */
    public boolean isCancelled(IAnalyticsEnabledTeamcity teamcity) {
        return CANCELLED_STATUS.equals(getState(teamcity));
    }

    /**
     * Return builds count.
     */
    public int buildsCount(){
        return builds.size();
    }

    /**
     * Return finished builds count.
     */
    public int finishedBuildsCount(IAnalyticsEnabledTeamcity teamcity){
        int finishedCnt = 0;

        for (Integer id : builds) {
            Build build = teamcity.getBuild(id);

            if (build.isFinished())
                ++finishedCnt;
        }

        return finishedCnt;
    }

    /** */
    public ContributionKey getContributionKey() {
        return new ContributionKey(srvId, branchForTc);
    }

    /** */
    public List<Integer> getBuilds() {
        return Collections.unmodifiableList(builds);
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
            Objects.equals(branchForTc, info.branchForTc) &&
            Objects.equals(ticket, info.ticket) &&
            Objects.equals(builds, info.builds) &&
            Objects.equals(date, info.date);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(srvId, buildTypeId, branchForTc, ticket, builds, date);
    }
}
