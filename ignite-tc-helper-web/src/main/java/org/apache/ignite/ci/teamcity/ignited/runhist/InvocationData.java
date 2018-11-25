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
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.TestCompacted;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

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

    public void add(IStringCompactor c, TestCompacted testCompacted, int build, long startDate) {
        if ((System.currentTimeMillis() - startDate) > Duration.ofDays(MAX_DAYS).toMillis())
            return;

        final boolean failedTest = testCompacted.isFailedTest(c);

        final Invocation invocation = invocationMap.computeIfAbsent(build, Invocation::new);

        final int failCode = failedTest
                ? (testCompacted.isIgnoredTest() || testCompacted.isMutedTest())
                        ? MUTED
                        : FAILURE
                : OK;

        invocation.status = (byte) failCode;

        allHistRuns++;
        if (failedTest)
            allHistFailures++;
    }

    public int allHistFailures() {
        return allHistFailures;
    }

    public int notMutedRunsCount() {
        return (int)
                invocationMap.values()
                        .stream()
                        .filter(invocation -> invocation.status != MUTED)
                        .count();
    }

    public int failuresCount() {
        return (int)
                invocationMap.values()
                        .stream()
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

    public List<Integer> getLatestRuns() {
        return invocationMap.values()
            .stream()
            .map(i->(int)i.status)
            .collect(Collectors.toList());
    }
}
