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
package org.apache.ignite.ci.teamcity.ignited;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcservice.model.hist.BuildRef;

import static org.apache.ignite.tcservice.model.hist.BuildRef.STATE_FINISHED;
import static org.apache.ignite.tcservice.model.hist.BuildRef.STATE_QUEUED;
import static org.apache.ignite.tcservice.model.hist.BuildRef.STATE_RUNNING;
import static org.apache.ignite.tcservice.model.hist.BuildRef.STATUS_SUCCESS;
import static org.apache.ignite.tcservice.model.hist.BuildRef.STATUS_UNKNOWN;

@Persisted
public class BuildRefCompacted {
    /** Build Id without modifications, -1 if it is null. */
    private int id = -1;

    /** Compacter identifier for string 'Build type id'. */
    private int buildTypeId = -1;

    /** Compacter identifier for string 'Branch name'. */
    @QuerySqlField(index = true)
    private int branchName = -1;

    /** Compacter identifier for string 'Status'. */
    private int status = -1;

    /** Compacter identifier for string 'State'. */
    private int state = -1;

    /** Status Unknown: Compactor identifier. */
    private static volatile int STATUS_UNKNOWN_CID = -1;
    /** Status success: Compactor identifier. */
    private static volatile int STATUS_SUCCESS_CID = -1;

    /** State running: Compactor identifier. */
    private static volatile int STATE_RUNNING_CID = -1;
    /** State queued: Compactor identifier. */
    private static volatile int STATE_QUEUED_CID = -1;
    /** State finished: Compactor identifier. */
    private static volatile int STATE_FINISHED_CID = -1;


    /**
     * Default constructor.
     */
    public BuildRefCompacted() {
    }

    /**
     * @param compactor Compactor.
     * @param ref Reference.
     */
    public BuildRefCompacted(IStringCompactor compactor, BuildRef ref) {
        fillFieldsFromBuildRef(compactor, ref);
    }

    /**
     * Reset cached Ids for tests
     */
    public static void resetCached() {
        STATE_FINISHED_CID = -1;
        STATE_QUEUED_CID = -1;
        STATUS_UNKNOWN_CID = -1;
        STATUS_SUCCESS_CID = -1;
        STATE_RUNNING_CID = -1;
    }

    public void fillFieldsFromBuildRef(IStringCompactor compactor, BuildRef ref) {
        setId(ref.getId());
        buildTypeId = compactor.getStringId(ref.buildTypeId());
        branchName = compactor.getStringId(ref.branchName());
        status = compactor.getStringId(ref.status());
        state = compactor.getStringId(ref.state());
    }

    public void setId(@Nullable Integer buildId) {
        this.id = buildId == null ? -1 : buildId;
    }

    /**
     * @param refCompacted Reference compacted.
     */
    public BuildRefCompacted(BuildRefCompacted refCompacted) {
        id = refCompacted.id();
        buildTypeId = refCompacted.buildTypeId();
        branchName = refCompacted.branchName();
        status = refCompacted.status();
        state = refCompacted.state();
    }


    /**
     * @param compactor Compacter.
     */
    public BuildRef toBuildRef(IStringCompactor compactor) {
        BuildRef res = new BuildRef();

        fillBuildRefFields(compactor, res);

        return res;
    }

    protected void fillBuildRefFields(IStringCompactor compactor, BuildRef res) {
        res.setId(getId());
        res.buildTypeId = buildTypeId(compactor);
        res.branchName = branchName(compactor);
        res.status = compactor.getStringFromId(status);
        res.state = compactor.getStringFromId(state);
        res.href = getHrefForId(id());
    }

    @Nullable public Integer getId() {
        return id < 0 ? null : id;
    }

    public String buildTypeId(IStringCompactor compactor) {
        return compactor.getStringFromId(buildTypeId);
    }

    public String branchName(IStringCompactor compactor) {
        return compactor.getStringFromId(branchName);
    }

    @Nonnull
    protected static String getHrefForId(int id) {
        return "/app/rest/latest/builds/id:" + id;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BuildRefCompacted compacted = (BuildRefCompacted)o;
        return id == compacted.id &&
            buildTypeId == compacted.buildTypeId &&
            branchName == compacted.branchName &&
            status == compacted.status &&
            state == compacted.state;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(id, buildTypeId, branchName, status, state);
    }

    /** */
    public int id() {
        return id;
    }

    /** */
    public int buildTypeId() {
        return buildTypeId;
    }

    /** */
    public int branchName() {
        return branchName;
    }

    /** */
    public int status() {
        return status;
    }

    /** */
    public BuildRefCompacted status(int status) {
        this.status = status;

        return this;
    }

    /** */
    public int state() {
        return state;
    }

    /** */
    public BuildRefCompacted state(int state) {
        this.state = state;

        return this;
    }

    /** */
    public boolean isFakeStub() {
        return id() < 0;
    }


    private int statusUnkown(IStringCompactor compactor) {
        if (STATUS_UNKNOWN_CID == -1) //Each time compactor should give same result, so no locking applied
            STATUS_UNKNOWN_CID = compactor.getStringId(STATUS_UNKNOWN);

        return STATUS_UNKNOWN_CID;
    }

    public int statusSuccess(IStringCompactor compactor) {
        if (STATUS_SUCCESS_CID == -1) //Each time compactor should give same result, so no locking applied
            STATUS_SUCCESS_CID =  compactor.getStringId(STATUS_SUCCESS);

        return STATUS_SUCCESS_CID;
    }

    public int stateRunning(IStringCompactor compactor) {
        if (STATE_RUNNING_CID == -1) //Each time compactor should give same result, so no locking applied
            STATE_RUNNING_CID = compactor.getStringId(STATE_RUNNING);

        return STATE_RUNNING_CID;
    }

    public int stateFinished(IStringCompactor compactor) {
        if (STATE_FINISHED_CID == -1) //Each time compactor should give same result, so no locking applied
            STATE_FINISHED_CID =  compactor.getStringId(STATE_FINISHED);

        return STATE_FINISHED_CID;
    }

    public int stateQueued(IStringCompactor compactor) {
        if (STATE_QUEUED_CID == -1) //Each time compactor should give same result, so no locking applied
            STATE_QUEUED_CID = compactor.getStringId(STATE_QUEUED);

        return STATE_QUEUED_CID;
    }

    public boolean isNotCancelled(IStringCompactor compactor) {
        return !isCancelled(compactor);
    }

    public boolean isCancelled(IStringCompactor compactor) {
        return hasUnknownStatus(compactor);
    }

    private boolean hasUnknownStatus(IStringCompactor compactor) {
        return statusUnkown(compactor) == status();
    }

    public boolean isRunning(IStringCompactor compactor) {
        return stateRunning(compactor) == state();
    }

    public boolean isFinished(IStringCompactor compactor) {
        return stateFinished(compactor) == state();
    }

    public boolean isQueued(IStringCompactor compactor) {
        return stateQueued(compactor) == state();
    }

    public boolean isSuccess(IStringCompactor compactor) {
        return statusSuccess(compactor) == status();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("buildTypeId", buildTypeId)
            .add("branchName", branchName)
            .add("status", status)
            .add("state", state)
            .toString();
    }

    public String state(IStringCompactor compactor) {
        return compactor.getStringFromId(state());
    }

    public BuildRefCompacted branchName(int branchName) {
        this.branchName = branchName;

        return this;
    }

    public BuildRefCompacted buildTypeId(int buildTypeId) {
        this.buildTypeId = buildTypeId;

        return this;
    }

    public BuildRefCompacted withId(int id) {
        this.id = id;

        return this;
    }
}
