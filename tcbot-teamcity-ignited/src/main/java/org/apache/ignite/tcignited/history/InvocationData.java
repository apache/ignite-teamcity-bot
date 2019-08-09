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

package org.apache.ignite.tcignited.history;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;

/**
 *
 */
public class InvocationData {
    /** Muted. */
    public static final int MUTED = RunStatus.RES_MUTED_FAILURE.getCode();
    /** Failure. */
    public static final int FAILURE = RunStatus.RES_FAILURE.getCode();
    /** Ok. */
    public static final int OK = RunStatus.RES_OK.getCode();
    /** Ok. */
    public static final int CRITICAL_FAILURE = RunStatus.RES_CRITICAL_FAILURE.getCode();
    /** Test is missing in suite run. */
    public static final int MISSING = RunStatus.RES_MISSING.getCode();

    /** Failure muted. */
    public static final int FAILURE_MUTED = RunStatus.RES_FAILURE_MUTED.getCode();

    /** Ok muted. */
    public static final int OK_MUTED = RunStatus.RES_OK_MUTED.getCode();

    /** Test Ignored. */
    public static final int IGNORED = RunStatus.RES_IGNORED.getCode();

    /** Invocations map from build ID to invocation data. */
    private final List<Invocation> invocationList = new ArrayList<>();

    public void add(Invocation inv) {
        invocationList.add(inv);
    }

    /**
     *
     */
    @Nonnull
    Stream<Invocation> invocations() {
        return invocationList.stream();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("invocationList", invocationList)
            .toString();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        InvocationData data = (InvocationData)o;
        return
            Objects.equals(invocationList, data.invocationList);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(invocationList);
    }

    /**
     *
     */
    public List<Integer> getLatestRuns() {
        return invocations()
            .map(i -> (int)i.status())
            .collect(Collectors.toList());
    }

    public void sort() {
        invocationList.sort(Comparator.comparing(Invocation::buildId));
    }

    /**
     * Build ID - index mapping should be always called after sort.
     */
    public Map<Integer, Integer> buildIdsMapping() {
        Map<Integer, Integer> buildIdToIdx = new HashMap<>();
        int idx = 0;

        for (Invocation next : invocationList) {
            buildIdToIdx.put(next.buildId(), idx);
            idx++;
        }

        return buildIdToIdx;
    }

    public Iterable<Invocation> invocationsIterable() {
        return Collections.unmodifiableList(invocationList);
    }

    public Invocation getInvocationAt(int idx) {
        int size = invocationList.size();
        Preconditions.checkState(idx < size,
            "Requested invocation outside suite history [" + idx + "] size [" + size + "]");
        return invocationList.get(idx);
    }
}
