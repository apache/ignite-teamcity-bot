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

package org.apache.ignite.tcservice.model.hist;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import org.apache.ignite.tcservice.model.result.AbstractRef;

/**
 * Actual result of build execution from build history,
 * short version involved in snapshot dependencies and into build history list
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class BuildRef extends AbstractRef {
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";

    @XmlAttribute private Integer id;

    @XmlAttribute public String buildTypeId;

    @XmlAttribute public String branchName;

    @XmlAttribute public String status;

    public static final String STATE_FINISHED = "finished";

    public static final String STATE_RUNNING = "running";

    public static final String STATE_QUEUED = "queued";

    /** Current state of build. */
    @XmlAttribute public String state;

    @XmlAttribute(name = "number") public String buildNumber;

    @XmlAttribute public Boolean defaultBranch;

    @XmlAttribute public Boolean composite;

    @XmlAttribute public Boolean failedToStart;

    /** Build page URL. */
    @XmlAttribute public String webUrl;

    /**
     * @return Build ID
     */
    public Integer getId() {
        return id;
    }

    public boolean isNotCancelled() {
        return !hasUnknownStatus();
    }

    public boolean hasUnknownStatus() {
        return STATUS_UNKNOWN.equals(status);
    }

    public void setCancelled() {
        status = STATUS_UNKNOWN;
        state = STATE_FINISHED;
    }


    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    /**
     * @return
     */
    public String suiteId() {
        return buildTypeId;
    }

    /**
     * @return
     */
    public boolean isFakeStub() {
        return getId() == null;
    }

    /**
     * @param id
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * @return true if build is composite.
     */
    public boolean isComposite() {
        return composite != null && composite;
    }

    public boolean isFailedToStart() {
        return failedToStart != null && failedToStart;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        BuildRef ref = (BuildRef)o;

        return Objects.equals(id, ref.id) &&
            Objects.equals(buildTypeId, ref.buildTypeId) &&
            Objects.equals(branchName, ref.branchName) &&
            Objects.equals(status, ref.status) &&
            Objects.equals(state, ref.state) &&
            Objects.equals(buildNumber, ref.buildNumber) &&
            Objects.equals(defaultBranch, ref.defaultBranch) &&
            Objects.equals(composite, ref.composite) &&
            Objects.equals(failedToStart, ref.failedToStart) &&
            Objects.equals(webUrl, ref.webUrl);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(id, buildTypeId, branchName, status, state, buildNumber, defaultBranch, composite,
            failedToStart, webUrl);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("buildTypeId", buildTypeId)
            .add("branchName", branchName)
            .add("status", status)
            .add("state", state)
            .add("buildNumber", buildNumber)
            .add("defaultBranch", defaultBranch)
            .add("composite", composite)
            .add("webUrl", webUrl)
            .add("href", href)
            .toString();
    }

    /**
     *
     */
    public String branchName() {
        return branchName;
    }

    /**
     *
     */
    public String buildTypeId() {
        return buildTypeId;
    }

    /**
     *
     */
    public String status() {
        return status;
    }

    /**
     *
     */
    public String state() {
        return state;
    }

    public boolean isFinished() {
        return STATE_FINISHED.equals(state());
    }

    public boolean isQueued() {
        return STATE_QUEUED.equals(state());
    }

    public boolean isRunning() {
        return STATE_RUNNING.equals(state());
    }

    /** */
    public boolean isUnknown() {
        return STATUS_UNKNOWN.equals(status());
    }

    /** */
    public void setBranchName(String branch) {
        this.branchName = branch;
    }
}
