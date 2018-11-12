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

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;

/**
 *
 */
public class CompactBuildsInfo {
    /** */
    public final int userName;

    /** Server id. */
    public final int srvId;

    /** Build type id. */
    public final int buildTypeId;

    /** Branch name. */
    public final int branchForTc;

    /** JIRA ticket full name. */
    public final int ticket;

    /** */
    public final Date date;

    /** Finished builds. */
    private final Map<Integer, Boolean> finishedBuilds = new HashMap<>();

    /** */
    public CompactBuildsInfo(BuildsInfo buildsInfo, IStringCompactor strCompactor) {
        this.userName = strCompactor.getStringId(buildsInfo.userName);
        this.date = buildsInfo.date;
        this.srvId = strCompactor.getStringId(buildsInfo.srvId);
        this.ticket = strCompactor.getStringId(buildsInfo.ticket);
        this.branchForTc = strCompactor.getStringId(buildsInfo.branchForTc);
        this.buildTypeId = strCompactor.getStringId(buildsInfo.buildTypeId);
        this.finishedBuilds.putAll(buildsInfo.getBuilds());
    }

    /** */
    public Map<Integer, Boolean> getFinishedBuilds() {
        return Collections.unmodifiableMap(finishedBuilds);
    }

    /** */
    public BuildsInfo toBuildInfo(IStringCompactor compactor) {
        return new BuildsInfo(this, compactor);
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
            Objects.equals(finishedBuilds.keySet(), info.finishedBuilds.keySet()) &&
            Objects.equals(date, info.date);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(srvId, buildTypeId, branchForTc, ticket, finishedBuilds.keySet(), date);
    }
}
