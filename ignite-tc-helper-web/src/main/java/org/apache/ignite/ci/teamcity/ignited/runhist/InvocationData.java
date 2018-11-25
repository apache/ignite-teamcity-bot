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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.TestCompacted;

import java.util.Map;
import java.util.TreeMap;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.jetbrains.annotations.NotNull;

public class InvocationData {
    public static final int MAX_DAYS = 30;
    /** Muted. */
    public static final int MUTED = RunStat.RunStatus.RES_MUTED_FAILURE.getCode();
    /** Failure. */
    public static final int FAILURE = RunStat.RunStatus.RES_FAILURE.getCode();
    /** Ok. */
    public static final int OK = RunStat.RunStatus.RES_OK.getCode();

    /**
     * Runs registered all the times.
     */
    private int allHistRuns;

    /**
     * Failures registered all the times.
     */
    private int allHistFailures;

    private Map<Integer, Invocation> invocationMap = new TreeMap<>();

    public int allHistRuns() {
        return allHistRuns;
    }

    public void add(int testSuccess, TestCompacted testCompacted, int build, long startDate) {
        final boolean failedTest = testCompacted.status()!=testSuccess;

        if (invocationMap.containsKey(build))
            return;

        final Invocation invocation = new Invocation(build);
        Invocation prevVal = invocationMap.putIfAbsent(build, invocation);

        final int failCode = failedTest
            ? (testCompacted.isIgnoredTest() || testCompacted.isMutedTest())
            ? MUTED
            : FAILURE
            : OK;

        invocation.status = (byte)failCode;
        invocation.startDate = startDate;

        if (prevVal == null) {
            allHistRuns++;
            if (failedTest)
                allHistFailures++;
        }

        removeEldiest();
    }

    void removeEldiest() {
        invocationMap.entrySet().removeIf(entries -> isExpired(entries.getValue().startDate));
    }

    public boolean isExpired(long startDate) {
        return (U.currentTimeMillis() - startDate) > Duration.ofDays(MAX_DAYS).toMillis();
    }

    public int allHistFailures() {
        return allHistFailures;
    }

    public int notMutedRunsCount() {
        return (int)
                invocations()
                        .filter(invocation -> invocation.status != MUTED)
                        .count();
    }

    @NotNull public Stream<Invocation> invocations() {
        return invocationMap.values()
            .stream()
            .filter(this::isActual);
    }

    private boolean isActual(Invocation invocation) {
        return !isExpired(invocation.startDate);
    }

    public int failuresCount() {
        return (int)
                invocations()
                        .filter(invocation -> invocation.status == FAILURE)
                        .count();
    }

    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("allHistRuns", allHistRuns)
                .add("allHistFailures", allHistFailures)
                .add("invocationMap", invocationMap)
                .toString();
    }

    /**
     *
     */
    public List<Integer> getLatestRuns() {
        return invocations()
            .map(i->(int)i.status)
            .collect(Collectors.toList());
    }
}
