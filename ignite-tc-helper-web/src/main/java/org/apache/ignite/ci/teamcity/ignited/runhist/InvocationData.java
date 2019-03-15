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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class InvocationData {
    /** Max days to keep test invocatoin data in run statistics: affects Bot Visa. */
    public static final int MAX_DAYS = 21;
    /** Muted. */
    public static final int MUTED = RunStat.RunStatus.RES_MUTED_FAILURE.getCode();
    /** Failure. */
    public static final int FAILURE = RunStat.RunStatus.RES_FAILURE.getCode();
    /** Ok. */
    public static final int OK = RunStat.RunStatus.RES_OK.getCode();
    /** Ok. */
    public static final int CRITICAL_FAILURE = RunStat.RunStatus.RES_CRITICAL_FAILURE.getCode();

    /**
     * Runs registered all the times.
     */
    private int allHistRuns;

    /**
     * Failures registered all the times.
     */
    private int allHistFailures;

    /** Invocations map from build ID to invocation data. */
    private Map<Integer, Invocation> invocationMap = new TreeMap<>();

    public int allHistRuns() {
        return allHistRuns;
    }

    public boolean addInvocation(Invocation inv) {
        try {
            return innerAdd(inv);
        }
        finally {
            removeEldiest();
        }
    }

    public boolean innerAdd(Invocation inv) {
        int build = inv.buildId();
        if (build < 0)
            return false;

        if (invocationMap.containsKey(build))
            return false;

        if (isExpired(inv.startDate()))
            return false;

        Invocation prevVal = invocationMap.putIfAbsent(build, inv);

        final boolean newVal = prevVal == null;

        if (newVal) {
            allHistRuns++;
            if (inv.isFailure())
                allHistFailures++;
        }

        return newVal;
    }

    void removeEldiest() {
        invocationMap.entrySet().removeIf(entries -> isExpired(entries.getValue().startDate()));
    }

    /**
     * @param startDate Start date.
     */
    public static boolean isExpired(long startDate) {
        return (U.currentTimeMillis() - startDate) > Duration.ofDays(MAX_DAYS).toMillis();
    }

    /**
     *
     */
    public int allHistFailures() {
        return allHistFailures;
    }

    /**
     *
     */
    public int notMutedRunsCount() {
        return (int)
            invocations()
                .filter(invocation -> invocation.status() != MUTED)
                .count();
    }

    /**
     *
     */
    @NotNull public Stream<Invocation> invocations() {
        return invocationMap.values()
            .stream()
            .filter(this::isActual);
    }

    /**
     * @param invocation Invocation.
     */
    private boolean isActual(Invocation invocation) {
        return !isExpired(invocation.startDate());
    }

    /**
     *
     */
    public int failuresCount() {
        return (int)invocations().filter(inv -> inv.status() == FAILURE || inv.status() == CRITICAL_FAILURE).count();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("allHistRuns", allHistRuns)
            .add("allHistFailures", allHistFailures)
            .add("invocationMap", invocationMap)
            .toString();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        InvocationData data = (InvocationData)o;
        return allHistRuns == data.allHistRuns &&
            allHistFailures == data.allHistFailures &&
            Objects.equals(invocationMap, data.invocationMap);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(allHistRuns, allHistFailures, invocationMap);
    }

    /**
     *
     */
    public List<Integer> getLatestRuns() {
        return invocations()
            .map(i -> (int)i.status())
            .collect(Collectors.toList());
    }

    /**
     *
     */
    public int criticalFailuresCount() {
        return (int)invocations().filter(inv -> inv.status() == CRITICAL_FAILURE).count();
    }
}
