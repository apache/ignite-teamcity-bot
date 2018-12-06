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
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents parameters to determine context of observed builds, list of build IDs
 * which were requested for observing and provides methods to check status of
 * observed builds.
 */
public class BuildsInfo {
    /** Shows that all rerunned builds finished successfully. */
    public static final String FINISHED_STATUS = "finished";

    /** Shows that some rerunned builds is not finished. */
    public static final String RUNNING_STATUS = "running";

    /**
     * Shows that one or more rerunned builds were cancelled or have UNKNOWN
     * status on TC for some other reasons.
     */
    public static final String CANCELLED_STATUS = "cancelled";

    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(BuildsInfo.class);

    /** */
    public final String userName;

    /** Server id. */
    public final String srvId;

    /** Build type id. */
    public String buildTypeId;

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
     *
     * @return One of {@link #FINISHED_STATUS}, {@link #CANCELLED_STATUS} or
     * {@link #RUNNING_STATUS} statuses.
     */
    public String getStatus(ITeamcityIgnited teamcity) {
        boolean isFinished = true;

        for (Integer id : builds) {
            FatBuildCompacted build = teamcity.getFatBuild(id);

            if (build.isFakeStub() || build.isCancelled(teamcity.compactor()))
                return CANCELLED_STATUS;

            if (!build.isFinished(teamcity.compactor()))
                isFinished = false;
        }

        return isFinished ? FINISHED_STATUS : RUNNING_STATUS;
    }

    /**
     * @param teamcity Teamcity.
     */
    public boolean isFinished(ITeamcityIgnited teamcity) {
        return FINISHED_STATUS.equals(getStatus(teamcity));
    }

    /**
     * @param teamcity Teamcity.
     */
    public boolean isCancelled(ITeamcityIgnited teamcity) {
        return CANCELLED_STATUS.equals(getStatus(teamcity));
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
    public int finishedBuildsCount(ITeamcityIgnited teamcity){
        int finishedCnt = 0;

        for (Integer id : builds) {
            FatBuildCompacted build = teamcity.getFatBuild(id);

            if (!build.isFakeStub() && build.isFinished(teamcity.compactor()))
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
