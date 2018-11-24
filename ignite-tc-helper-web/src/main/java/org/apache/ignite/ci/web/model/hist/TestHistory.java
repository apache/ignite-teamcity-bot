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

package org.apache.ignite.ci.web.model.hist;

import com.google.common.base.Objects;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.teamcity.ignited.IRunHistory;
import org.jetbrains.annotations.NotNull;

/**
 * Summary of failures - all history and recent runs for suite or for suite.
 */
public class TestHistory {
    /** 'All the time' runs history statistic. */
    public FailureSummary allTime = new FailureSummary();

    /** Latest runs history statistic. */
    public FailureSummary recent = new FailureSummary();

    /** Latest runs, 0,1,2 values for each run. */
    @Nullable public List<Integer> latestRuns;

    /** Non null flaky comments means there is flakiness detected in the the branch. */
    @Nullable public String flakyComments;

    public void init(@Nullable IRunHistory stat) {
        if (stat == null)
            return;

        recent.failures = stat.getFailuresCount();
        recent.runs = stat.getRunsCount();
        recent.failureRate = stat.getFailPercentPrintable();

        allTime.failures = stat.getFailuresAllHist();
        allTime.runs = stat.getRunsAllHist();
        allTime.failureRate = stat.getFailPercentAllHistPrintable();

        latestRuns = stat.getLatestRunResults();

        flakyComments = stat.getFlakyComments();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TestHistory hist = (TestHistory)o;

        return Objects.equal(allTime, hist.allTime) &&
            Objects.equal(recent, hist.recent) &&
            Objects.equal(latestRuns, hist.latestRuns);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(allTime, recent, latestRuns);
    }
}
