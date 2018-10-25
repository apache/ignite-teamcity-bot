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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.util.CollectionUtil;
import org.apache.ignite.ci.util.FutureUtil;
import org.apache.ignite.ci.util.TimeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.stream.Stream.concat;

/**
 * Run configuration execution results loaded from different API URLs.
 * Includes tests and problem occurrences; if logs processing is done also contains last started test
 */
public class MultBuildRunCtx implements ISuiteResults {
    @Nonnull private final Build firstBuildInfo;

    /** Builds: Single execution. */
    private List<SingleBuildRunCtx> builds = new CopyOnWriteArrayList<>();

    /** Tests: Map from full test name to multiple test occurrence. */
    @Deprecated
    private final Map<String, MultTestFailureOccurrences> tests = new ConcurrentSkipListMap<>();

    /**
     * Mapping for building test occurrence reference to test full results:
     * Map from "Occurrence in build id" test detailed info.
     * Note: only failed tests are loaded here
     */
    @Deprecated
    private Map<String, CompletableFuture<TestOccurrenceFull>> testFullMap = new HashMap<>();

    /**
     * Statistics for last build.
     */
    @Nullable private Statistics stat;

    public void addBuild(SingleBuildRunCtx ctx) {
        builds.add(ctx);
    }

    /** Currently running builds */
    @Nullable private CompletableFuture<Long> runningBuildCount;

    /** Currently scheduled builds */
    @Nullable private CompletableFuture<Long> queuedBuildCount;

    public MultBuildRunCtx(@Nonnull final Build buildInfo) {
        this.firstBuildInfo = buildInfo;
    }

    public Stream<String> getCriticalFailLastStartedTest() {
        return builds.stream().map(SingleBuildRunCtx::getCriticalFailLastStartedTest).filter(Objects::nonNull);
    }

    public Stream<Integer> getBuildsWithThreadDump() {
        return builds.stream().map(SingleBuildRunCtx::getBuildIdIfHasThreadDump).filter(Objects::nonNull);
    }


    public Stream<Map<String, TestLogCheckResult>> getLogsCheckResults() {
        return builds.stream().map(SingleBuildRunCtx::getTestLogCheckResult).filter(Objects::nonNull);
    }

    public String suiteId() {
        return firstBuildInfo.suiteId();
    }

    public String suiteName() {
        return firstBuildInfo.suiteName();
    }

    public String buildTypeId() {
        return firstBuildInfo.buildTypeId;
    }


    @Deprecated
    //currently used only in old metrics
    public boolean hasNontestBuildProblem() {
        return allProblemsInAllBuilds().anyMatch(problem ->
            !problem.isFailedTests()
                && !problem.isSnapshotDepProblem()
                && !ProblemOccurrence.BUILD_FAILURE_ON_MESSAGE.equals(problem.type));
        //todo what to do with BuildFailureOnMessage, now it is ignored
    }

    public boolean hasAnyBuildProblemExceptTestOrSnapshot() {
        return allProblemsInAllBuilds()
            .anyMatch(p -> !p.isFailedTests() && !p.isSnapshotDepProblem());
    }

    @NotNull public Stream<ProblemOccurrence> allProblemsInAllBuilds() {
        return builds.stream().flatMap(SingleBuildRunCtx::getProblemsStream);
    }

    public List<SingleBuildRunCtx> getBuilds() {
        return builds;
    }

    public boolean hasTimeoutProblem() {
        return getExecutionTimeoutCount() > 0;
    }

    private long getExecutionTimeoutCount() {
        return builds.stream().filter(ISuiteResults::hasTimeoutProblem).count();
    }

    public boolean hasJvmCrashProblem() {
        return getJvmCrashProblemCount() > 0;
    }

    public long getJvmCrashProblemCount() {
        return builds.stream().filter(ISuiteResults::hasJvmCrashProblem).count();
    }

    public boolean hasOomeProblem() {
        return getOomeProblemCount() > 0;
    }

    @Override public boolean hasExitCodeProblem() {
        return getExitCodeProblemsCount() > 0;
    }

    private long getExitCodeProblemsCount() {
        return builds.stream().filter(ISuiteResults::hasExitCodeProblem).count();
    }

    private long getOomeProblemCount() {
        return builds.stream().filter(ISuiteResults::hasOomeProblem).count();
    }

    public int failedTests() {
        return (int)tests.values().stream().mapToInt(MultTestFailureOccurrences::failuresCount)
            .filter(cnt -> cnt > 0).count();
    }

    public int mutedTests() {
        TestOccurrencesRef testOccurrences = firstBuildInfo.testOccurrences;
        if (testOccurrences == null)
            return 0;
        final Integer muted = testOccurrences.muted;

        return muted == null ? 0 : muted;
    }

    public int totalTests() {
        final TestOccurrencesRef testOccurrences = firstBuildInfo.testOccurrences;

        if (testOccurrences == null)
            return 0;

        final Integer cnt = testOccurrences.count;

        return cnt == null ? 0 : cnt;
    }

    public String getPrintableStatusString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\t[").append(suiteName()).append("]\t");
        builder.append(getResult());
        builder.append(" ");
        builder.append(failedTests());

        if (stat != null) {
            final Long durationMs = stat.getBuildDuration();
            if (durationMs != null)
                builder.append(" ").append(TimeUtil.millisToDurationPrintable(durationMs));
        }

        builder.append("\n");
        getCriticalFailLastStartedTest().forEach(lastStartedTest ->
            builder.append("\t").append(lastStartedTest).append(" (Last started) \n"));

        getFailedTests().map(ITestFailureOccurrences::getName).forEach(
            name -> {
                builder.append("\t").append(name).append("\n");
            }
        );
        return builder.toString();
    }

    /**
     * Suite Run Result (filled if failed)
     *
     * @return printable result
     */
    public String getResult() {
        StringBuilder res = new StringBuilder();

        addKnownProblemCnt(res, "TIMEOUT", getExecutionTimeoutCount());
        addKnownProblemCnt(res, "JVM CRASH", getJvmCrashProblemCount());
        addKnownProblemCnt(res, "Out Of Memory Error", getOomeProblemCount());
        addKnownProblemCnt(res, "Exit Code", getExitCodeProblemsCount());

        {
            Stream<ProblemOccurrence> stream =
                allProblemsInAllBuilds().filter(p ->
                    !p.isFailedTests()
                        && !p.isSnapshotDepProblem()
                        && !p.isExecutionTimeout()
                        && !p.isJvmCrash()
                        && !p.isExitCode()
                        && !p.isJavaLevelDeadlock()
                        && !p.isOome());
            Optional<ProblemOccurrence> bpOpt = stream.findAny();
            if (bpOpt.isPresent()) {
                if (res.length() > 0)
                    res.append(", ");

                res.append(bpOpt.get().type).append(" ");
            }
        }

        List<LogCheckResult> collect = getLogChecksIfFinished().collect(Collectors.toList());

        long javaDeadlocks = collect.stream().map(LogCheckResult::getCustomProblems)
            .filter(set -> set.contains(ProblemOccurrence.JAVA_LEVEL_DEADLOCK))
            .count();

        addKnownProblemCnt(res, ProblemOccurrence.JAVA_LEVEL_DEADLOCK, javaDeadlocks);

        return res.toString();
    }

    public void addKnownProblemCnt(StringBuilder res, String nme, long execToCnt) {
        if (execToCnt > 0) {
            if (res.length() > 0)
                res.append(", ");

            res.append(nme)
                .append(" ")
                .append(execToCnt > 1 ? "[" + execToCnt + "]" : "");
        }
    }


    public Stream<Map.Entry<String, Long>> getTopLogConsumers() {
        Map<String, Long> logSizeBytes = new HashMap<>();

        getLogsCheckResults()

            .forEach(map -> {
                map.forEach(
                    (testName, logCheckResult) -> {
                        //todo may be it is better to find   avg
                        long bytes = (long)logCheckResult.getLogSizeBytes();
                        if (bytes > 1024 * 1024) {
                            logSizeBytes.merge(testName, bytes, (a, b) ->
                                Math.max(a, b));
                        }
                    }
                );
            });

        Comparator<Map.Entry<String, Long>> comparing = Comparator.comparing(Map.Entry::getValue);

        return CollectionUtil.top(logSizeBytes.entrySet().stream(), 3 ,comparing).stream();
    }

    public Stream<? extends ITestFailureOccurrences> getTopLongRunning() {
        Stream<MultTestFailureOccurrences> stream = tests.values().stream();
        Comparator<MultTestFailureOccurrences> comparing = Comparator.comparing(MultTestFailureOccurrences::getAvgDurationMs);
        return CollectionUtil.top(stream, 3, comparing).stream();
    }

    public Stream<? extends ITestFailureOccurrences> getFailedTests() {
        return tests.values().stream().filter(MultTestFailureOccurrences::hasFailedButNotMuted);
    }

    @Deprecated
    public void addTests(Iterable<TestOccurrence> tests) {
        for (TestOccurrence next : tests) {
            this.tests.computeIfAbsent(next.name,
                k -> new MultTestFailureOccurrences())
                .add(next);
        }
    }

    public int getBuildId() {
        return firstBuildInfo.getId();
    }

    boolean isFailed() {
        return failedTests() != 0 || hasAnyBuildProblemExceptTestOrSnapshot();
    }

    public String branchName() {
        return firstBuildInfo.branchName;
    }

    public void setStat(@Nullable Statistics stat) {
        this.stat = stat;
    }

    /**
     * @return last build duration.
     */
    @Nullable
    public Long getBuildDuration() {
        return stat == null ? null : stat.getBuildDuration();
    }

    @Nullable
    public Long getSourceUpdateDuration() {
        return stat == null ? null : stat.getSourceUpdateDuration();
    }

    /**
     * @param testOccurrenceInBuildId, something like: 'id:15666,build:(id:1093907)'
     * @param fullFut
     */
    public void addTestInBuildToTestFull(String testOccurrenceInBuildId,
        CompletableFuture<TestOccurrenceFull> fullFut) {
        testFullMap.put(testOccurrenceInBuildId, fullFut);
    }

    private Optional<TestOccurrenceFull> getFullTest(String testOccurrenceInBuildId) {
        return Optional.ofNullable(testFullMap.get(testOccurrenceInBuildId))
            .flatMap(fut ->
                Optional.ofNullable(fut.isDone() ? FutureUtil.getResultSilent(fut) : null));
    }

    /**
     * @return aggregation project ID, such as "Ignite_Tests_20"
     */
    @Nullable public String projectId() {
        final BuildType type = firstBuildInfo.getBuildType();

        if (type == null)
            return null;

        return type.getProjectId();
    }


    public void setRunningBuildCount(CompletableFuture<Long> runningBuildCount) {
        this.runningBuildCount = runningBuildCount;
    }

    public void setQueuedBuildCount(CompletableFuture<Long> queuedBuildCount) {
        this.queuedBuildCount = queuedBuildCount;
    }

    public boolean hasScheduledBuildsInfo() {
        return runningBuildCount != null && queuedBuildCount != null;
    }

    public Integer queuedBuildCount() {
        if (queuedBuildCount == null)
            return 0;

        Long val = FutureUtil.getResultSilent(queuedBuildCount);

        return val == null ? 0 : val.intValue();

    }

    public Integer runningBuildCount() {
        if (runningBuildCount == null)
            return 0;

        Long val = FutureUtil.getResultSilent(runningBuildCount);

        return val == null ? 0 : val.intValue();
    }

    public Stream<TestOccurrenceFull> getFullTests(ITestFailureOccurrences occurrence) {
        return occurrence.getOccurrenceIds()
            .map(this::getFullTest)
            .filter(Optional::isPresent)
            .map(Optional::get);
    }

    /**
     * @return Username's stream for users introduced changes in this commit
     */
    public Stream<String> lastChangeUsers() {
        return builds.stream()
            .flatMap(k -> k.getChanges().stream())
            .map(change -> change.username)
            .filter(Objects::nonNull);
    }

    Stream<? extends Future<?>> getFutures() {
        Stream<CompletableFuture<?>> stream1 = queuedBuildCount != null ? Stream.of(queuedBuildCount) : Stream.empty();
        Stream<CompletableFuture<?>> stream2 = runningBuildCount != null ? Stream.of(runningBuildCount) : Stream.empty();

        Stream<? extends Future<?>> stream3 = testFullMap.values().stream();

        Stream<? extends Future<?>> stream4 = builds.stream().flatMap(SingleBuildRunCtx::getFutures);

        return
            concat(
                concat(stream1, stream2),
                concat(stream4, stream3));
    }

    /**
     * @return true if all builds are composite
     */
    public boolean isComposite() {
        return !builds.isEmpty()
            && builds.stream().allMatch(SingleBuildRunCtx::isComposite);
    }

    /**
     * @return Set of tests.
     */
    public Set<String> tests() {
        return tests.keySet();
    }

    public Stream<LogCheckResult> getLogChecksIfFinished() {
        return builds.stream().map(SingleBuildRunCtx::getLogCheckIfFinished).filter(Objects::nonNull);
    }
}
