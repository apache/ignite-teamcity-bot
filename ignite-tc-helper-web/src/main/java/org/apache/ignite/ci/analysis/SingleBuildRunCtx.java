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

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.util.FutureUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Single build ocurrence,
 */
public class SingleBuildRunCtx implements ISuiteResults {
    private Build build;
    private CompletableFuture<LogCheckResult> logCheckResultsFut;

    /** Build problems occurred during single build run. */
    @Nullable private List<ProblemOccurrence> problems;

    private List<Change> changes = new ArrayList<>();

    private List<TestOccurrence> tests = new ArrayList<>();

    public SingleBuildRunCtx(Build build) {
        this.build = build;
    }

    public Build getBuild() {
        return build;
    }

    public Integer buildId() {
        return build.getId();
    }

    public boolean hasTimeoutProblem() {
        return getExecutionTimeoutCount() > 0;
    }

    private long getExecutionTimeoutCount() {
        return getProblemsStream().filter(ProblemOccurrence::isExecutionTimeout).count();
    }

    private Stream<ProblemOccurrence> getProblemsStream() {
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
        return build.suiteId();
    }

    public void setLogCheckResultsFut(CompletableFuture<LogCheckResult> logCheckResultsFut) {
        this.logCheckResultsFut = logCheckResultsFut;
    }

    @Nullable
    public String getCriticalFailLastStartedTest() {
        LogCheckResult logCheckResult = getLogCheckIfFinished();
        if (logCheckResult == null)
            return null;

        return logCheckResult.getLastStartedTest();
    }

    @Nullable
    public Map<String, TestLogCheckResult> getTestLogCheckResult() {
        LogCheckResult logCheckRes = getLogCheckIfFinished();

        if (logCheckRes == null)
            return null;

        return logCheckRes.getTestLogCheckResult();
    }

    @Nullable
    public Integer getBuildIdIfHasThreadDump() {
        LogCheckResult logCheckRes = getLogCheckIfFinished();

        if (logCheckRes == null)
            return null;

        return !Strings.isNullOrEmpty(logCheckRes.getLastThreadDump()) ? buildId() : null;
    }

    @Nullable public LogCheckResult getLogCheckIfFinished() {
        if (logCheckResultsFut == null)
            return null;

        if (!logCheckResultsFut.isDone() || logCheckResultsFut.isCancelled())
            return null;

        LogCheckResult logCheckRes = FutureUtil.getResultSilent(logCheckResultsFut);

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

    public void setTests(List<TestOccurrence> tests) {
        this.tests = tests;
    }

    public List<TestOccurrence> getTests() {
        return tests;
    }

    Stream<? extends Future<?>> getFutures() {
        if (logCheckResultsFut == null)
            return Stream.empty();
        else
            return Stream.of((Future<?>)logCheckResultsFut);
    }

    public boolean isComposite() {
        return build.isComposite();
    }
}
