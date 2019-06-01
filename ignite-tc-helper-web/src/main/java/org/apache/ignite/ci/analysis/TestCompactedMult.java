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

package org.apache.ignite.ci.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.teamcity.ignited.IRunHistory;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.TestCompacted;

public class TestCompactedMult implements IMultTestOccurrence {
    private final List<TestCompacted> occurrences = new ArrayList<>();
    private IStringCompactor compactor;
    private long avgDuration = -1;

    public TestCompactedMult(IStringCompactor compactor) {
        this.compactor = compactor;
    }

    /** {@inheritDoc} */
    @Override public String getName() {
        return occurrences.isEmpty() ? "" : occurrences.iterator().next().testName(compactor);
    }

    /** {@inheritDoc} */
    @Override public boolean isInvestigated() {
        return occurrences.stream().anyMatch(TestCompacted::isInvestigated);
    }

    /** */
    private int getFailedButNotMutedCount() {
        return (int)occurrences.stream()
            .filter(Objects::nonNull)
            .filter(t -> t.isFailedButNotMuted(compactor)).count();
    }

    /** {@inheritDoc} */
    @Override public int failuresCount() {
        return getFailedButNotMutedCount();
    }

    /** {@inheritDoc} */
    @Override public long getAvgDurationMs() {
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

    /** {@inheritDoc} */
    @Override public Iterable<TestOccurrenceFull> getOccurrences() {
        return occurrences.stream()
            .map(testCompacted -> testCompacted.toTestOccurrence(compactor, 0))
            .collect(Collectors.toList());
    }

    @Override public String getPossibleBlockerComment(IRunHistory baseBranchStat) {
        if (baseBranchStat == null)
            return "History for base branch is absent.";

        String flakyComments = baseBranchStat.getFlakyComments();

        boolean lowFailureRate = baseBranchStat.getFailRate() * 100.0f < 4.;

        if (lowFailureRate && flakyComments == null) {
            return "Test has low fail rate in base branch "
                + baseBranchStat.getFailPercentPrintable()
                + "% and is not flaky";
        }

        return null;
    }

    public void add(TestCompacted next) {
        occurrences.add(next);
    }
}
