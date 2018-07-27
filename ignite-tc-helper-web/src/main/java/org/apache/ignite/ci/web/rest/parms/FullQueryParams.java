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

package org.apache.ignite.ci.web.rest.parms;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import javax.annotation.Nonnull;
import javax.ws.rs.QueryParam;
import org.jetbrains.annotations.Nullable;

/**
 * Contains maximum combination of query parameters
 */
public class FullQueryParams {
    public static final String DEFAULT_BRANCH_NAME = "master";

    //see definitions in Index.html javascript
    public static final String HISTORY = "History";
    public static final String LATEST = "Latest";
    public static final String CHAIN = "Chain";
    public static final int DEFAULT_COUNT = 10;

    /** Tracked branch name */
    @Nullable @QueryParam("branch") String branch;

    /** Server For not tracked branches. */
    @Nullable @QueryParam("serverId") String serverId;

    /** Suite ID to check. For not tracked branches. */
    @Nonnull @QueryParam("suiteId") String suiteId;

    /** TC identified branch. For not tracked branches. */
    @Nonnull @QueryParam("branchForTc") String branchForTc;

    /** Type of rebuilds loading: */
    @Nonnull @QueryParam("action") String action;

    /** Count of suites to analyze, for multiple runs results. */
    @Nullable @QueryParam("count") Integer count;

    /** Enables all logs to be loaded locally without relation to run/failure category. */
    @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs;
    private Integer buildId;

    public FullQueryParams() {
    }

    public FullQueryParams(String serverId, String suiteId, String branchForTc, String action, Integer count) {
        this.serverId = serverId;
        this.suiteId = suiteId;
        this.branchForTc = branchForTc;
        this.action = action;
        this.count = count;
    }

    @Nullable public String getBranch() {
        return branch;
    }

    @Nullable public String getServerId() {
        return serverId;
    }

    @Nonnull public String getSuiteId() {
        return suiteId;
    }

    @Nonnull public String getBranchForTc() {
        return branchForTc;
    }

    @Nonnull public String getAction() {
        return action;
    }

    @Nullable public Integer getCount() {
        return count;
    }

    @Nullable public Boolean getCheckAllLogs() {
        return checkAllLogs;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FullQueryParams param = (FullQueryParams)o;
        return Objects.equal(branch, param.branch) &&
            Objects.equal(serverId, param.serverId) &&
            Objects.equal(suiteId, param.suiteId) &&
            Objects.equal(branchForTc, param.branchForTc) &&
            Objects.equal(action, param.action) &&
            Objects.equal(count, param.count) &&
            Objects.equal(checkAllLogs, param.checkAllLogs) &&
            Objects.equal(buildId, param.buildId);
    }

    @Override public int hashCode() {
        return Objects.hashCode(branch, serverId, suiteId, branchForTc, action, count, checkAllLogs, buildId);
    }

    public void setBranch(@Nullable String branch) {
        this.branch = branch;
    }

    public void setCheckAllLogs(@Nullable Boolean checkAllLogs) {
        this.checkAllLogs = checkAllLogs;
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("branch", branch)
            .add("serverId", serverId)
            .add("suiteId", suiteId)
            .add("branchForTc", branchForTc)
            .add("action", action)
            .add("count", count)
            .add("checkAllLogs", checkAllLogs)
            .add("buildId", buildId)
            .toString();
    }

    public void setCount(@Nullable int count) {
        this.count = count;
    }

    public void setBuildId(Integer buildId) {
        this.buildId = buildId;
    }

    public void setServerId(@Nullable String serverId) {
        this.serverId = serverId;
    }

    public Integer getBuildId() {
        return buildId;
    }
}
