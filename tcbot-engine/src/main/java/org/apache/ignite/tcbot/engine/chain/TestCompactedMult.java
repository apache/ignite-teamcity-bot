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

package org.apache.ignite.tcbot.engine.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.common.TcBotConst;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.build.ITest;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.history.ISuiteRunHistory;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrence;

/**
 * Test occurrence merged from several runs.
 */
public class TestCompactedMult {
    private final List<ITest> occurrences = new ArrayList<>();
    private IStringCompactor compactor;
    private MultBuildRunCtx ctx;
    private long avgDuration = -1;

    /** Status success. */
    private static volatile int STATUS_SUCCESS_CID = -1;

    public TestCompactedMult(IStringCompactor compactor, MultBuildRunCtx ctx) {
        this.compactor = compactor;
        this.ctx = ctx;

        //Each time compactor should give same result
        if (STATUS_SUCCESS_CID == -1)
            STATUS_SUCCESS_CID = compactor.getStringId(TestOccurrence.STATUS_SUCCESS);
    }

    public static void resetCached() {
        STATUS_SUCCESS_CID = -1;
    }

    @Nullable public Integer testName() {
        return occurrences.isEmpty() ? null : occurrences.iterator().next().testName();
    }
    
    public String getName() {
        return occurrences.isEmpty() ? "" : occurrences.iterator().next().testName(compactor);
    }

    public Long getId() {
        return occurrences.isEmpty() ? 0 : occurrences.iterator().next().getTestId();
    }

    public boolean isPassed() {
        return occurrences.get(occurrences.size()-1).status() == STATUS_SUCCESS_CID;
    }

    public boolean isInvestigated() {
        return occurrences.stream().anyMatch(ITest::isInvestigated);
    }

    /** */
    private int getFailedButNotMutedCount() {
        return (int)occurrences.stream()
            .filter(Objects::nonNull)
            .filter(t -> t.isFailedButNotMuted(STATUS_SUCCESS_CID)).count();
    }

    public int failuresCount() {
        return getFailedButNotMutedCount();
    }

    public long getAvgDurationMs() {
        if (avgDuration < 0) {
            avgDuration = (long)occurrences.stream()
                .map(ITest::getDuration)
                .filter(Objects::nonNull)
                .mapToInt(i -> i)
                .average()
                .orElse(0);
        }

        return avgDuration;
    }

    /**
     *
     */
    public Stream<ITest> getInvocationsStream() {
        return occurrences.stream();
    }

    /**
      * @param baseBranchStat Base branch statistics.
      * @return non null comment in case test failure is a blocker for merge into base branch.
      */
     public String getPossibleBlockerComment(IRunHistory baseBranchStat) {
         if (failuresCount() == 0) {
             if (baseBranchStat == null) {
                 long durationMs = getAvgDurationMs();
                 if (durationMs > TcBotConst.MAX_NEW_TEST_DURATION_FOR_RUNALL_MS)
                     return "New test duration " +
                         TimeUnit.MILLISECONDS.toSeconds(durationMs) + "s" +
                         " is more that 1 minute";
             }

             return null;
         }

         if (baseBranchStat == null)
             return "History for base branch is absent.";

         boolean flaky = baseBranchStat.isFlaky();

         float failRate = baseBranchStat.getFailRate();
         boolean lowFailureRate = failRate * 100.0f < TcBotConst.NON_FLAKY_TEST_FAIL_RATE_BLOCKER_BORDER_PERCENTS;

         if (lowFailureRate && !flaky) {
             String runStatPrintable = IRunHistory.getPercentPrintable(failRate * 100.0f);

             return "Test has low fail rate in base branch "
                 + runStatPrintable
                 + "% and is not flaky";
         }

         return null;
     }

    public void add(ITest next) {
        occurrences.add(next);
    }

    public IRunHistory history(ITeamcityIgnited ignited, @Nullable Integer baseBranchId) {
         return history(ignited, baseBranchId, null);
    }

    @Nullable public IRunHistory history(ITeamcityIgnited ignited,
        @Nullable Integer baseBranchId,
        @Nullable Map<Integer, Integer> requireParameters) {
        Integer name = testName();
        if (name == null || baseBranchId == null)
            return null;

        ISuiteRunHistory suiteRunHist = ctx.suiteHist(ignited, baseBranchId, requireParameters);

        if (suiteRunHist == null)
            return null;

        return suiteRunHist.getTestRunHist(name);
    }

    /**
     */
    public boolean isFailedButNotMuted() {
        return occurrences.stream().anyMatch(o -> o.isFailedButNotMuted(STATUS_SUCCESS_CID));
    }

    /**
     *
     */
    public boolean isMutedOrIgored() {
        return occurrences.stream().anyMatch(ITest::isMutedOrIgnored);
    }

    /**
     * Filter to determine if this test execution should be shown in the report of failures.
     *  @param tcIgnited Tc ignited.
     * @param baseBranchId Base branch id.
     * @param showMuted
     * @param showIgnored
     */
    public boolean includeIntoReport(ITeamcityIgnited tcIgnited, Integer baseBranchId,
        boolean showMuted,
        boolean showIgnored) {
        if (isFailedButNotMuted())
            return true;

        if (showMuted && occurrences.stream().anyMatch(ITest::isMutedTest))
            return true;

        if (showIgnored && occurrences.stream().anyMatch(ITest::isIgnoredTest))
            return true;

        boolean longRun = getAvgDurationMs() > TcBotConst.MAX_NEW_TEST_DURATION_FOR_RUNALL_MS;

        if (longRun)
            return history(tcIgnited, baseBranchId) == null;

        return false;
    }

    public boolean hasLongRunningTest(int sec) {
        if (sec < 1)
            return false;

        return getAvgDurationMs() > TimeUnit.SECONDS.toMillis(sec);
    }
}
