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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.util.FutureUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Single build ocurrence,
 */
public class SingleBuildRunCtx implements ISuiteResults {
    /** Build compacted. */
    private FatBuildCompacted buildCompacted;
    /** Compactor. */
    private IStringCompactor compactor;

    /** Logger check result future. */
    private CompletableFuture<LogCheckResult> logCheckResultFut;

    /** Build problems occurred during single build run. */
    @Nullable private List<ProblemOccurrence> problems;

    private List<Change> changes = new ArrayList<>();

    @Deprecated
    private List<TestOccurrence> tests = new ArrayList<>();

    public SingleBuildRunCtx(Build build,
        FatBuildCompacted buildCompacted,
        IStringCompactor compactor) {
        this.buildCompacted = buildCompacted;
        this.compactor = compactor;
    }

    public Integer buildId() {
        Preconditions.checkNotNull(buildCompacted);
        return buildCompacted.id() < 0 ? null : buildCompacted.id();
    }

    public boolean hasTimeoutProblem() {
        return getExecutionTimeoutCount() > 0;
    }

    private long getExecutionTimeoutCount() {
        return getProblemsStream().filter(ProblemOccurrence::isExecutionTimeout).count();
    }

    Stream<ProblemOccurrence> getProblemsStream() {
        if (problems == null)
            return Stream.empty();

        return problems.stream().filter(Objects::nonNull);
    }

    @Override public boolean hasJvmCrashProblem() {
        return getProblemsStream().anyMatch(ProblemOccurrence::isJvmCrash);
    }

    @Override public boolean hasOomeProblem() {
        return getProblemsStream().anyMatch(ProblemOccurrence::isOome);
    }

    @Override public boolean hasExitCodeProblem() {
        return getProblemsStream().anyMatch(ProblemOccurrence::isExitCode);
    }

    @Override public String suiteId() {
        return compactor.getStringFromId(buildCompacted.buildTypeId());
    }

    public void setLogCheckResultFut(CompletableFuture<LogCheckResult> logCheckResultFut) {
        this.logCheckResultFut = logCheckResultFut;
    }

    @Nullable public String getCriticalFailLastStartedTest() {
        LogCheckResult logCheckResult = getLogCheckIfFinished();
        if (logCheckResult == null)
            return null;

        return logCheckResult.getLastStartedTest();
    }

    @Nullable public Map<String, TestLogCheckResult> getTestLogCheckResult() {
        LogCheckResult logCheckRes = getLogCheckIfFinished();

        if (logCheckRes == null)
            return null;

        return logCheckRes.getTestLogCheckResult();
    }

    @Nullable public Integer getBuildIdIfHasThreadDump() {
        LogCheckResult logCheckRes = getLogCheckIfFinished();

        if (logCheckRes == null)
            return null;

        return !Strings.isNullOrEmpty(logCheckRes.getLastThreadDump()) ? buildId() : null;
    }

    @Nullable public LogCheckResult getLogCheckIfFinished() {
        if (logCheckResultFut == null)
            return null;

        if (!logCheckResultFut.isDone() || logCheckResultFut.isCancelled())
            return null;

        LogCheckResult logCheckRes = FutureUtil.getResultSilent(logCheckResultFut);

        if (logCheckRes == null)
            return null;

        return logCheckRes;
    }

    public void setProblems(@Nullable List<ProblemOccurrence> problems) {
        this.problems = problems;
    }

    public void addChange(Change change) {
        if (change.isFakeStub())
            return;

        this.changes.add(change);
    }

    public List<Change> getChanges() {
        return changes;
    }

    @Deprecated
    public void setTests(List<TestOccurrence> tests) {
        this.tests = tests;
    }

    public List<? extends TestOccurrence> getTests() {
        return buildCompacted.getTestOcurrences(compactor).getTests();
    }


    @Nonnull Stream<? extends Future<?>> getFutures() {
        return logCheckResultFut == null ? Stream.empty() : Stream.of((Future<?>)logCheckResultFut);
    }

    public boolean isComposite() {
        return buildCompacted.isComposite();
    }

    public String getBranch() {
        return compactor.getStringFromId(buildCompacted.branchName());
    }


    public List<String> getFailedNotMutedTestNames() {
        return buildCompacted.getFailedNotMutedTestNames(compactor);
    }
}
