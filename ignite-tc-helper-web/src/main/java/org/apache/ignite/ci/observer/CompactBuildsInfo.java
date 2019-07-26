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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

/**
 * Representation of {@link BuildsInfo} with compacted properties for more effective cache storing.
 */
public class CompactBuildsInfo {
    /** */
    private int userName;

    /** Server id. */
    private int srvId;

    /** Build type id. */
    private int buildTypeId;

    /** Branch name. */
    private int branchForTc;

    /** JIRA ticket full name. */
    private int ticket;

    /** Base branch ID. By default represents null */
    private int baseBranchForTc = -1;

    /** */
    private Date date;

    /** Builds. */
    private final List<Integer> builds = new ArrayList<>();

    /** */
    public CompactBuildsInfo() {

    }

    /** */
    public CompactBuildsInfo(BuildsInfo buildsInfo, IStringCompactor strCompactor) {
        this.userName = strCompactor.getStringId(buildsInfo.userName);
        this.date = buildsInfo.date;
        this.srvId = strCompactor.getStringId(buildsInfo.srvId);
        this.ticket = strCompactor.getStringId(buildsInfo.ticket);
        this.branchForTc = strCompactor.getStringId(buildsInfo.branchForTc);
        this.buildTypeId = strCompactor.getStringId(buildsInfo.buildTypeId);
        this.baseBranchForTc = strCompactor.getStringId(buildsInfo.baseBranchForTc);
        this.builds.addAll(buildsInfo.getBuilds());
    }

    /** */
    public List<Integer> getBuilds() {
        return Collections.unmodifiableList(builds);
    }

    /** */
    public BuildsInfo toBuildInfo(IStringCompactor compactor) {
        return new BuildsInfo(this, compactor);
    }

    /**
     * @return Server id.
     */
    public int serverId() {
        return srvId;
    }

    /**
     * @param srvId New server id.
     */
    public void serverId(int srvId) {
        this.srvId = srvId;
    }

    /**
     * @return Build type id.
     */
    public int buildTypeId() {
        return buildTypeId;
    }

    /**
     * @param buildTypeId New build type id.
     */
    public void buildTypeId(int buildTypeId) {
        this.buildTypeId = buildTypeId;
    }

    /**
     * @return Branch name.
     */
    public int branchForTc() {
        return branchForTc;
    }

    /**
     * @param branchForTc New branch name.
     */
    public void branchForTc(int branchForTc) {
        this.branchForTc = branchForTc;
    }

    /**
     * @return JIRA ticket full name.
     */
    public int ticket() {
        return ticket;
    }

    /**
     * @param ticket New jIRA ticket full name.
     */
    public void ticket(int ticket) {
        this.ticket = ticket;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof CompactBuildsInfo))
            return false;

        CompactBuildsInfo info = (CompactBuildsInfo)o;

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

    /** */
    public void userName(int val) {
        this.userName = val;
    }

    /** */
    public void date(long ts) {
        this.date = new Date(ts);
    }

    /** */
    public int userName() {
        return userName;
    }

    /** */
    public Date date() {
        return date;
    }

    /** */
    public int srvId() {
        return srvId;
    }

    /** */
    public void srvId(int srvId) {
        this.srvId = srvId;
    }

    /** */
    public int baseBranchForTc() {
        return baseBranchForTc;
    }

    /** */
    public void addBuild(int... arr) {
        for (int i = 0; i < arr.length; i++)
            builds.add(arr[i]);
    }
}
