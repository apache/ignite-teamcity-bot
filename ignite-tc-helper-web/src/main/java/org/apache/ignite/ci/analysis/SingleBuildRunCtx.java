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
    private CompletableFuture<LogCheckResult> logCheckResFut;

    /** Build problems occurred during single build run. */
    @Nullable private List<ProblemOccurrence> problems;

    /** Changes. */
    private List<Change> changes = new ArrayList<>();

    /**
     * @param build Build.
     * @param buildCompacted Build compacted.
     * @param compactor Compactor.
     */
    public SingleBuildRunCtx(Build build,
        FatBuildCompacted buildCompacted,
        IStringCompactor compactor) {
        this.buildCompacted = buildCompacted;
        this.compactor = compactor;
    }

    /**
     *
     */
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

    public void setLogCheckResFut(CompletableFuture<LogCheckResult> logCheckResFut) {
        this.logCheckResFut = logCheckResFut;
    }

    @Nullable public String getCriticalFailLastStartedTest() {
        LogCheckResult logCheckRes = getLogCheckIfFinished();
        if (logCheckRes == null)
            return null;

        return logCheckRes.getLastStartedTest();
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
        if (logCheckResFut == null)
            return null;

        if (!logCheckResFut.isDone() || logCheckResFut.isCancelled())
            return null;

        LogCheckResult logCheckRes = FutureUtil.getResultSilent(logCheckResFut);

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

    public List<? extends TestOccurrence> getTests() {
        return buildCompacted.getTestOcurrences(compactor).getTests();
    }


    @Nonnull Stream<? extends Future<?>> getFutures() {
        return logCheckResFut == null ? Stream.empty() : Stream.of((Future<?>)logCheckResFut);
    }

    public boolean isComposite() {
        return buildCompacted.isComposite();
    }

    public String getBranch() {
        return compactor.getStringFromId(buildCompacted.branchName());
    }


    public Stream<String> getFailedNotMutedTestNames() {
        return buildCompacted.getFailedNotMutedTestNames(compactor);
    }

    public Stream<String> getAllTestNames() {
        return buildCompacted.getAllTestNames(compactor);
    }

    public String suiteName() {
        return buildCompacted.buildTypeName(compactor);
    }

    public String projectId() {
        return buildCompacted.projectId(compactor);
    }
}
