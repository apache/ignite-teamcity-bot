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
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcbot.common.util.TimeUtil;

import javax.annotation.Nonnull;

/**
 *
 */
public class FullChainRunCtx {
    private final boolean fakeStub;
    private Build chainResults;
    private List<MultBuildRunCtx> buildCfgsResults = new ArrayList<>();

    public FullChainRunCtx(Build chainResults) {
        this.chainResults = chainResults;

        fakeStub = chainResults.isFakeStub();
    }

    public Stream<MultBuildRunCtx> suites() {
        return buildCfgsResults.stream();
    }

    public Integer getSuiteBuildId() {
        return chainResults.getId();
    }

    public String suiteId() {
        return chainResults.suiteId();
    }

    public String suiteName() {
        return chainResults.suiteName();
    }

    public String branchName() {
        return chainResults.branchName;
    }

    public Stream<MultBuildRunCtx> failedChildSuites() {
        Predicate<MultBuildRunCtx> filter = MultBuildRunCtx::isFailed;

        return filteredChildSuites(filter);
    }

    public Stream<MultBuildRunCtx> filteredChildSuites(Predicate<MultBuildRunCtx> filter) {
        return suites().filter(filter);
    }

    /**
     * @return may return less time than actual duration if not all statistic is provided
     * @param suiteFilter
     */
    public Long getTotalDuration(
        Predicate<MultBuildRunCtx> suiteFilter) {
        return sumOfNonNulls(getDurations(suiteFilter));
    }

    public boolean hasFullDurationInfo(
        Predicate<MultBuildRunCtx> suiteFilter) {
        return getDurations(suiteFilter).noneMatch(Objects::isNull);
    }

    /**
     * @return returns durations of all suites (last builds)
     * @param suiteFilter
     */
    private Stream<Long> getDurations(Predicate<MultBuildRunCtx> suiteFilter) {
        return suitesNonComposite().filter(suiteFilter).map(MultBuildRunCtx::buildDuration);
    }

    /**
     * @return sum of durations of all suites printable.
     * @param suiteFilter
     */
    @Nonnull public String getDurationPrintable(
        Predicate<MultBuildRunCtx> suiteFilter) {
        return (TimeUtil.millisToDurationPrintable(getTotalDuration(suiteFilter)))
            + (hasFullDurationInfo(suiteFilter) ? "" : "+");
    }

    public void addAllSuites(List<MultBuildRunCtx> suites) {
        this.buildCfgsResults.addAll(suites);
    }

    public boolean isFakeStub() {
        return fakeStub;
    }

    public String getTestsDurationPrintable(Predicate<MultBuildRunCtx> suiteFilter) {
        long tests = sumOfNonNulls(suitesNonComposite().filter(suiteFilter).map(MultBuildRunCtx::getAvgTestsDuration));

        return (TimeUtil.millisToDurationPrintable(tests));
    }

    @Nonnull public Stream<MultBuildRunCtx> suitesNonComposite() {
        return suites().filter(ctx -> !ctx.isComposite());
    }

    public String getLostInTimeoutsPrintable(Predicate<MultBuildRunCtx> suiteFilter) {
        long timeouts = suitesNonComposite().filter(suiteFilter).mapToLong(MultBuildRunCtx::getLostInTimeouts).sum();

        return TimeUtil.millisToDurationPrintable(timeouts);
    }

    public String durationNetTimePrintable(Predicate<MultBuildRunCtx> suiteFilter) {
        return TimeUtil.millisToDurationPrintable(
            sumOfNonNulls(suitesNonComposite().filter(suiteFilter).map(MultBuildRunCtx::buildDurationNetTime)));
    }

    public String sourceUpdateDurationPrintable(Predicate<MultBuildRunCtx> suiteFilter) {
        return TimeUtil.millisToDurationPrintable(
            sumOfNonNulls(suitesNonComposite().filter(suiteFilter).map(MultBuildRunCtx::sourceUpdateDuration)));
    }

    public String artifcactPublishingDurationPrintable(Predicate<MultBuildRunCtx> suiteFilter) {
        return TimeUtil.millisToDurationPrintable(
            sumOfNonNulls(suitesNonComposite().filter(suiteFilter).map(MultBuildRunCtx::artifcactPublishingDuration)));
    }

    public String dependeciesResolvingDurationPrintable(Predicate<MultBuildRunCtx> suiteFilter) {
        return TimeUtil.millisToDurationPrintable(
            sumOfNonNulls(suitesNonComposite().filter(suiteFilter).map(MultBuildRunCtx::dependeciesResolvingDuration)));
    }

    public long sumOfNonNulls(Stream<Long> st) {
        return st.filter(Objects::nonNull).mapToLong(t -> t).sum();
    }
}
