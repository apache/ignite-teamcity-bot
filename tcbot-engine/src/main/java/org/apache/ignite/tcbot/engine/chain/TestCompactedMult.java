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
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.TestCompacted;
import org.apache.ignite.tcbot.common.TcBotConst;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.history.IRunHistSummary;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.history.IRunStat;
import org.apache.ignite.tcignited.history.ISuiteRunHistory;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrenceFull;

/**
 * Test occurrence merged from several runs.
 */
public class TestCompactedMult {
    private final List<TestCompacted> occurrences = new ArrayList<>();
    private IStringCompactor compactor;
    private MultBuildRunCtx ctx;
    private long avgDuration = -1;

    public TestCompactedMult(IStringCompactor compactor, MultBuildRunCtx ctx) {
        this.compactor = compactor;
        this.ctx = ctx;
    }

    @Nullable public Integer testName() {
        return occurrences.isEmpty() ? null : occurrences.iterator().next().testName();
    }
    
    public String getName() {
        return occurrences.isEmpty() ? "" : occurrences.iterator().next().testName(compactor);
    }
 
    public boolean isInvestigated() {
        return occurrences.stream().anyMatch(TestCompacted::isInvestigated);
    }

    /** */
    private int getFailedButNotMutedCount() {
        return (int)occurrences.stream()
            .filter(Objects::nonNull)
            .filter(t -> t.isFailedButNotMuted(compactor)).count();
    }

    public int failuresCount() {
        return getFailedButNotMutedCount();
    }

    public long getAvgDurationMs() {
        if (avgDuration < 0) {
            avgDuration = (long)occurrences.stream()
                .map(TestCompacted::getDuration)
                .filter(Objects::nonNull)
                .mapToInt(i -> i)
                .average()
                .orElse(0);
        }

        return avgDuration;
    }


    public Iterable<TestOccurrenceFull> getOccurrences() {
        return occurrences.stream()
            .map(testCompacted -> testCompacted.toTestOccurrence(compactor, 0))
            .collect(Collectors.toList());
    }

     /**
      * @param baseBranchStat Base branch statistics.
      * @return non null comment in case test failure is a blocker for merge into base branch.
      */
     public String getPossibleBlockerComment(IRunHistSummary baseBranchStat) {
         if (failuresCount() == 0) {
             if (baseBranchStat == null) {
                 if (getAvgDurationMs() > TcBotConst.MAX_NEW_TEST_DURATION_FOR_RUNALL)
                     return "Newly contributed test duration is more that 1 minute";
             }

             return null;
         }

         if (baseBranchStat == null)
             return "History for base branch is absent.";

         boolean flaky = baseBranchStat.isFlaky();

         float failRate = baseBranchStat.getFailRate();
         boolean lowFailureRate = failRate * 100.0f < TcBotConst.NON_FLAKY_TEST_FAIL_RATE_BLOCKER_BORDER_PERCENTS;

         if (lowFailureRate && !flaky) {
             String runStatPrintable = IRunStat.getPercentPrintable(failRate * 100.0f);

             return "Test has low fail rate in base branch "
                 + runStatPrintable
                 + "% and is not flaky";
         }

         return null;
     }

    public void add(TestCompacted next) {
        occurrences.add(next);
    }

    public IRunHistory history(ITeamcityIgnited ignited, @Nullable Integer baseBranchId) {
        Integer name = testName();
        if (name == null || baseBranchId == null)
            return null;

        ISuiteRunHistory suiteRunHist = ctx.suiteHist(ignited, baseBranchId);

        if (suiteRunHist == null)
            return null;

        return suiteRunHist.getTestRunHist(name);
    }

    /**
     * @param statusSuccess Status success.
     */
    public boolean isFailedButNotMuted(int statusSuccess) {
        return occurrences.stream().anyMatch(o -> o.isFailedButNotMuted(statusSuccess));
    }
}
