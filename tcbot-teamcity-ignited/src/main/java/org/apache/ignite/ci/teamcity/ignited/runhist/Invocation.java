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
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.teamcity.ignited.buildtype.ParametersCompacted;
import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcignited.history.ChangesState;
import org.apache.ignite.tcignited.history.InvocationData;

/**
 * Run history element: invocation of build or test.
 */
@Persisted
public class Invocation {
    /** VCS Change not filled. */
    public static final int CHANGE_NOT_FILLED = 2;

    /** VCS Change present. */
    public static final int CHANGE_PRESENT = 1;

    /** No changes in VCS. */
    public static final int NO_CHANGES = 0;

    /** Build id. */
    private int buildId;

    /** Status: An integer (actually byte) code from RunStat.RunStatus */
    private byte status;

    /** Change present: 0 - no changes, 1 - changes present, 2- unknown */
    private byte changePresent;

    /** Additional (important) build Parameters, which can be used for filtering. */
    @Nullable private ParametersCompacted parameters;

    /**
     * Creates invocation.
     * @param buildId Build id.
     */
    public Invocation(Integer buildId) {
        this.buildId = buildId;
        this.changePresent = CHANGE_NOT_FILLED;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("buildId", buildId)
            .add("status", status)
            .add("changePresent", changePresent)
            .toString();
    }

    public Invocation withStatus(int failCode) {
        Preconditions.checkState(failCode < 128);
        Preconditions.checkState(failCode >= 0);

        this.status = (byte)failCode;

        return this;
    }

    public byte status() {
        return status;
    }

    public Invocation withChanges(int[] changes) {
        this.changePresent = (byte) (changes.length > 0 ? CHANGE_PRESENT : NO_CHANGES);

        return this;
    }

    public ChangesState changesState() {
        if (changePresent == NO_CHANGES)
            return ChangesState.NONE;
        else if (changePresent == CHANGE_PRESENT)
            return ChangesState.EXIST;
        else
            return ChangesState.UNKNOWN;
    }

    public int buildId() {
        return buildId;
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
            changePresent == that.changePresent;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(buildId, status, changePresent);
    }

    public Invocation withParameters(Map<Integer, Integer> parms) {
        if (parms == null || parms.isEmpty())
            return this;

        this.parameters = new ParametersCompacted(parms);

        return this;
    }


    public static boolean hasAnyParameterValue(ParametersCompacted parameters, @Nonnull Map<Integer, Integer> requireParamVal) {
        if (parameters == null)
            return false;

        Set<Map.Entry<Integer, Integer>> entries = requireParamVal.entrySet();
        for (Map.Entry<Integer, Integer> next : entries) {
            Integer key = next.getKey();

            int valId = parameters.findPropertyStringId(key);
            if (java.util.Objects.equals(next.getValue(), valId))
                return true;
        }

        return false;
    }

    public boolean containsParameterValue(Map<Integer, Integer> requireParameters) {
        return hasAnyParameterValue(this.parameters, requireParameters);
    }

    public Invocation withChangeState(ChangesState state) {
        if (state == ChangesState.NONE)
            changePresent = NO_CHANGES;
        else if (state == ChangesState.EXIST)
            changePresent = CHANGE_PRESENT;
        else
            changePresent = CHANGE_NOT_FILLED;

        return this;
    }

    public static boolean isMutedOrIgnored(byte s) {
        return s == InvocationData.MUTED || s == InvocationData.FAILURE_MUTED || s == InvocationData.OK_MUTED || s == InvocationData.IGNORED;
    }
}
