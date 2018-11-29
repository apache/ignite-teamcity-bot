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

package org.apache.ignite.ci.teamcity.ignited.runhist;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class Invocation {
    private int buildId;
    byte status;
    private byte changePresent;
    long startDate;

    public Invocation(Integer buildId) {
        this.buildId = buildId;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("buildId", buildId)
            .add("status", status)
            .add("changePresent", changePresent)
            .toString();
    }

    public void status(int failCode) {
        Preconditions.checkState(failCode < 128);
        this.status = (byte) failCode;
    }

    public void startDate(long startDateTs) {
        this.startDate = startDateTs;
    }

    public void changesPresent(int changesPresent) {
        Preconditions.checkState(changesPresent < 128);
        this.changePresent = (byte) changesPresent;
    }

    public boolean isFailure() {
        return status == InvocationData.FAILURE || status == InvocationData.MUTED;
    }

    public int buildId() {
        return buildId;
    }

    public long startDate() {
        return startDate;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Invocation that = (Invocation)o;
        return buildId == that.buildId &&
            status == that.status &&
            changePresent == that.changePresent &&
            startDate == that.startDate;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(buildId, status, changePresent, startDate);
    }
}
