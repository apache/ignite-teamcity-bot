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
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.util.TimeUtil;
import org.jetbrains.annotations.NotNull;

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

    @Deprecated
    public int buildProblems() {
        return (int)buildCfgsResults.stream().filter(MultBuildRunCtx::hasNontestBuildProblem).count();
    }

    public int timeoutsOomeCrashBuildProblems() {
        return (int)buildCfgsResults.stream().filter(context ->
            context.hasJvmCrashProblem()
                || context.hasTimeoutProblem()
                || context.hasOomeProblem()).count();
    }

    public List<MultBuildRunCtx> suites() {
        return buildCfgsResults;
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
        return suites().stream().filter(MultBuildRunCtx::isFailed);
    }

    /**
     * @return may return less time than actual duration if not all statistic is provided
     */
    public Long getTotalDuration() {
        return getDurations().filter(Objects::nonNull).mapToLong(t -> t).sum();
    }

    public boolean hasFullDurationInfo() {
        return getDurations().noneMatch(Objects::isNull);
    }

    /**
     * @return returns durations of all suites (last builds)
     */
    private Stream<Long> getDurations() {
        return suites().stream().filter(ctx -> !ctx.isComposite()).map(MultBuildRunCtx::getBuildDuration);
    }

    /**
     * @return sum of durations of all suites printable.
     */
    @NotNull public String getDurationPrintable() {
        return (TimeUtil.millisToDurationPrintable(getTotalDuration()))
            + (hasFullDurationInfo() ? "" : "+");
    }

    public void addAllSuites(ArrayList<MultBuildRunCtx> suites) {
        this.buildCfgsResults.addAll(suites);
    }

    public Stream<Future<?>> getFutures() {
        return buildCfgsResults.stream().flatMap(MultBuildRunCtx::getFutures);
    }

    public Stream<Future<?>> getRunningUpdates() {
        return getFutures().filter(Objects::nonNull).filter(future -> !future.isDone() && !future.isCancelled());
    }

    public boolean isFakeStub() {
        return fakeStub;
    }
}
