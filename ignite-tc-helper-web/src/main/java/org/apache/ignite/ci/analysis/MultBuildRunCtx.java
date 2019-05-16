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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.teamcity.ignited.IRunHistory;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProblemCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.TestCompacted;
import org.apache.ignite.ci.util.CollectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Run configuration execution results loaded from different API URLs. Includes tests and problem occurrences; if logs
 * processing is done also contains last started test
 */
public class MultBuildRunCtx implements ISuiteResults {
    /** Cancelled. */
    public static final String CANCELLED = "CANCELLED";

    /** First build info. */
    @Nonnull private final BuildRef firstBuildInfo;

    private IStringCompactor compactor;

    /** Builds: Single execution. */
    private List<SingleBuildRunCtx> builds = new CopyOnWriteArrayList<>();

    public void addBuild(SingleBuildRunCtx ctx) {
        builds.add(ctx);
    }

    /** Currently running builds */
    private Integer runningBuildCount;

    /** Currently scheduled builds */
    private Integer queuedBuildCount;

    public MultBuildRunCtx(@Nonnull final BuildRef buildInfo, IStringCompactor compactor) {
        this.firstBuildInfo = buildInfo;
        this.compactor = compactor;
    }

    public Stream<String> getCriticalFailLastStartedTest() {
        return buildsStream().map(SingleBuildRunCtx::getCriticalFailLastStartedTest).filter(Objects::nonNull);
    }

    public Stream<SingleBuildRunCtx> buildsStream() {
        return builds.stream();
    }

    public Stream<Integer> getBuildsWithThreadDump() {
        return buildsStream().map(SingleBuildRunCtx::getBuildIdIfHasThreadDump).filter(Objects::nonNull);
    }

    public Stream<Map<String, TestLogCheckResult>> getLogsCheckResults() {
        return buildsStream().map(SingleBuildRunCtx::getTestLogCheckResult).filter(Objects::nonNull);
    }

    public String suiteId() {
        return firstBuildInfo.suiteId();
    }

    /** {@inheritDoc} */
    @Override public boolean hasBuildMessageProblem() {
        return getBuildMessageProblemCount() > 0;
    }

    public String buildTypeId() {
        return firstBuildInfo.buildTypeId;
    }

    public boolean hasAnyBuildProblemExceptTestOrSnapshot() {
        return allProblemsInAllBuilds()
            .anyMatch(p -> !p.isFailedTests(compactor) && !p.isSnapshotDepProblem(compactor));
    }

    public boolean onlyCancelledBuilds() {
        return buildsStream().allMatch(bCtx -> !bCtx.isComposite() && bCtx.isCancelled());
    }

    @NotNull public Stream<ProblemCompacted> allProblemsInAllBuilds() {
        return buildsStream().flatMap(SingleBuildRunCtx::getProblemsStream);
    }

    public List<SingleBuildRunCtx> getBuilds() {
        return builds;
    }

    /** {@inheritDoc} */
    @Override public boolean hasMetricProblem() {
        return getMetricProblemCount() > 0;
    }

    /** */
    public long getMetricProblemCount() {
        return buildsStream().filter(ISuiteResults::hasMetricProblem).count();
    }

    /** */
    public long getBuildMessageProblemCount() {
        return buildsStream().filter(ISuiteResults::hasBuildMessageProblem).count();
    }

    /** {@inheritDoc} */
    @Override public boolean hasCompilationProblem() {
        return getCompilationProblemCount() > 0;
    }

    /** */
    public long getCompilationProblemCount() {
        return buildsStream().filter(ISuiteResults::hasCompilationProblem).count();
    }

    public boolean hasTimeoutProblem() {
        return getExecutionTimeoutCount() > 0;
    }

    private long getExecutionTimeoutCount() {
        return buildsStream().filter(SingleBuildRunCtx::hasTimeoutProblem).count();
    }

    public boolean hasJvmCrashProblem() {
        return getJvmCrashProblemCount() > 0;
    }

    public long getJvmCrashProblemCount() {
        return buildsStream().filter(ISuiteResults::hasJvmCrashProblem).count();
    }

    public boolean hasOomeProblem() {
        return getOomeProblemCount() > 0;
    }

    @Override public boolean hasExitCodeProblem() {
        return getExitCodeProblemsCount() > 0;
    }

    private long getExitCodeProblemsCount() {
        return buildsStream().filter(ISuiteResults::hasExitCodeProblem).count();
    }

    private long getOomeProblemCount() {
        return buildsStream().filter(ISuiteResults::hasOomeProblem).count();
    }

    public int failedTests() {
        return (int)getFailedTestsNames().count();
    }

    @NotNull public Stream<String> getFailedTestsNames() {
        return buildsStream().flatMap(SingleBuildRunCtx::getFailedNotMutedTestNames).distinct();
    }

    /**
     * Suite Run Result (filled if failed)
     *
     * @return printable result
     */
    public String getResult() {
        StringBuilder res = new StringBuilder();

        long cancelledCnt = getCancelledBuildsCount();

        if (cancelledCnt > 0) {
            res.append(CANCELLED);

            if (cancelledCnt > 1)
                res.append(" ").append(cancelledCnt);
        }

        addKnownProblemCnt(res, "TIMEOUT", getExecutionTimeoutCount());
        addKnownProblemCnt(res, "JVM CRASH", getJvmCrashProblemCount());
        addKnownProblemCnt(res, "Out Of Memory Error", getOomeProblemCount());
        addKnownProblemCnt(res, "Exit Code", getExitCodeProblemsCount());
        addKnownProblemCnt(res, "Compilation Error", getCompilationProblemCount());
        addKnownProblemCnt(res, "Failure on metric", getMetricProblemCount());

        {
            Stream<ProblemCompacted> stream =
                allProblemsInAllBuilds().filter(p ->
                    !p.isFailedTests(compactor)
                        && !p.isBuildFailureOnMetric(compactor)
                        && !p.isCompilationError(compactor)
                        && !p.isSnapshotDepProblem(compactor)
                        && !p.isExecutionTimeout(compactor)
                        && !p.isJvmCrash(compactor)
                        && !p.isExitCode(compactor)
                        //&& !p.isJavaLevelDeadlock(compactor)
                        && !p.isOome(compactor));
            Optional<ProblemCompacted> bpOpt = stream.findAny();
            if (bpOpt.isPresent()) {
                if (res.length() > 0)
                    res.append(", ");

                res.append(bpOpt.get().type(compactor)).append(" ");
            }
        }

        addKnownProblemCnt(res, ProblemOccurrence.JAVA_LEVEL_DEADLOCK, getJavaLevelDeadlocksCount());

        return res.toString();
    }

    public long getJavaLevelDeadlocksCount() {
        List<LogCheckResult> collect = getLogChecksIfFinished().collect(Collectors.toList());

        return collect.stream().map(LogCheckResult::getCustomProblems)
            .filter(set -> set.contains(ProblemOccurrence.JAVA_LEVEL_DEADLOCK))
            .count();
    }

    public long getCancelledBuildsCount() {
        return buildsStream().filter(bCtx -> !bCtx.isComposite() && bCtx.isCancelled()).count();
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

        return CollectionUtil.top(logSizeBytes.entrySet().stream(), 3, comparing).stream();
    }

    public Stream<? extends IMultTestOccurrence> getTopLongRunning() {
        Comparator<IMultTestOccurrence> comparing = Comparator.comparing(IMultTestOccurrence::getAvgDurationMs);

        Map<Integer, TestCompactedMult> res = new HashMap<>();

        builds.forEach(singleBuildRunCtx -> {
            saveToMap(res, singleBuildRunCtx.getAllTests());
        });

        return CollectionUtil.top(res.values().stream(), 3, comparing).stream();
    }

    public List<IMultTestOccurrence> getFailedTests() {
        Map<Integer, TestCompactedMult> res = new HashMap<>();

        builds.forEach(singleBuildRunCtx -> {
            saveToMap(res, singleBuildRunCtx.getFailedNotMutedTests());
        });

        return new ArrayList<>(res.values());
    }

    public void saveToMap(Map<Integer, TestCompactedMult> res, Stream<TestCompacted> tests) {
        tests.forEach(testCompacted -> {
            res.computeIfAbsent(testCompacted.testName(), k -> new TestCompactedMult(compactor))
                .add(testCompacted);
        });
    }

    public int getBuildId() {
        return firstBuildInfo.getId();
    }

    boolean isFailed() {
        return failedTests() != 0 || hasAnyBuildProblemExceptTestOrSnapshot() || onlyCancelledBuilds();
    }

    public String branchName() {
        return firstBuildInfo.branchName;
    }

    /**
     * @return average build duration, see {@link Statistics#BUILD_DURATION}.
     */
    @Nullable public Long buildDuration() {
        return calcAverage(buildsStream().map(SingleBuildRunCtx::buildDuration));
    }

    /**
     * @return average build duration, see {@link Statistics#BUILD_DURATION_NET_TIME}.
     */
    @Nullable public Long buildDurationNetTime() {
        return calcAverage(buildsStream().map(SingleBuildRunCtx::buildDurationNetTime));
    }

    /**
     * @return average build duration, see {@link Statistics#SOURCES_UPDATE_DURATION}.
     */
    @Nullable public Long sourceUpdateDuration() {
        return calcAverage(buildsStream().map(SingleBuildRunCtx::sourceUpdateDuration));
    }

    /**
     * @return average build duration, see {@link Statistics#ARTIFACTS_PUBLISHING_DURATION}.
     */
    @Nullable public Long artifcactPublishingDuration() {
        return calcAverage(buildsStream().map(SingleBuildRunCtx::artifcactPublishingDuration));
    }

    /**
     * @return average build duration, see {@link Statistics#DEPENDECIES_RESOLVING_DURATION}.
     */
    @Nullable public Long dependeciesResolvingDuration() {
        return calcAverage(buildsStream().map(SingleBuildRunCtx::dependeciesResolvingDuration));
    }

    @Nullable public Long calcAverage(Stream<Long> stream) {
        final OptionalDouble average = stream
            .filter(Objects::nonNull)
            .mapToLong(l -> l)
            .average();

        if (average.isPresent())
            return (long)average.getAsDouble();

        return null;
    }

    /**
     * @return sum of all tests execution duration (for several builds average).
     */
    @Nullable public Long getAvgTestsDuration() {
        final OptionalDouble average = buildsStream()
            .mapToLong(SingleBuildRunCtx::testsDuration)
            .average();

        if (average.isPresent())
            return (long)average.getAsDouble();

        return null;
    }

    /**
     * @return sum of all tests execution duration (for several builds average).
     */
    public long getLostInTimeouts() {
        if (builds.isEmpty())
            return 0;

        long allTimeoutsDuration = buildsStream()
            .filter(SingleBuildRunCtx::hasTimeoutProblem)
            .map(SingleBuildRunCtx::buildDuration)
            .filter(Objects::nonNull)
            .mapToLong(l -> l)
            .sum();

        return allTimeoutsDuration / builds.size();
    }

    @Nullable public String suiteName() {
        return buildsStream().findFirst().map(SingleBuildRunCtx::suiteName).orElse(null);
    }

    /**
     * @return aggregation project ID, such as "Ignite_Tests_20"
     */
    @Nullable public String projectId() {
        return buildsStream().findFirst().map(SingleBuildRunCtx::projectId).orElse(null);
    }

    public void setRunningBuildCount(int runningBuildCount) {
        this.runningBuildCount = runningBuildCount;
    }

    public void setQueuedBuildCount(int queuedBuildCount) {
        this.queuedBuildCount = queuedBuildCount;
    }

    public boolean hasScheduledBuildsInfo() {
        return runningBuildCount != null && queuedBuildCount != null;
    }

    public Integer queuedBuildCount() {
        return queuedBuildCount == null ? Integer.valueOf(0) : queuedBuildCount;
    }

    public Integer runningBuildCount() {
        return runningBuildCount == null ? Integer.valueOf(0) : runningBuildCount;
    }

    /**
     * @return Username's stream for users introduced changes in this commit
     */
    public Stream<String> lastChangeUsers() {
        return buildsStream()
            .flatMap(k -> k.getChanges().stream())
            .filter(ChangeCompacted::hasUsername)
            .map(change -> {
                final String tcUserFullName = change.tcUserFullName(compactor);

                final String vcsUsername = change.vcsUsername(compactor);

                if (Strings.isNullOrEmpty(tcUserFullName))
                    return vcsUsername;

                return vcsUsername + " [" + tcUserFullName + "]";
            });
    }

    Stream<? extends Future<?>> getFutures() {
        return buildsStream().flatMap(SingleBuildRunCtx::getFutures);
    }

    /**
     * @return true if all builds are composite
     */
    public boolean isComposite() {
        return !builds.isEmpty()
            && buildsStream().allMatch(SingleBuildRunCtx::isComposite);
    }

    /**
     * @return Set of tests (both for composite and regular)
     */
    public Set<String> tests() {
        return buildsStream().flatMap(SingleBuildRunCtx::getAllTestNames).collect(Collectors.toSet());
    }

    public Stream<LogCheckResult> getLogChecksIfFinished() {
        return buildsStream().map(SingleBuildRunCtx::getLogCheckIfFinished).filter(Objects::nonNull);
    }

    public Set<String> tags() {
        return buildsStream().flatMap(b -> b.tags().stream()).collect(Collectors.toSet());
    }

    /**
     * Classify suite if it is blossible blocker or not.
     * @param tcIgnited Tc ignited.
     * @param compactor Compactor.
     * @param baseBranchHist Base branch history for suite.
     */
    @NotNull public String getPossibleBlockerComment(ITeamcityIgnited tcIgnited,
        @Nonnull IStringCompactor compactor,
        @Nullable IRunHistory baseBranchHist) {
        StringBuilder res = new StringBuilder();

        long cancelledCnt = getCancelledBuildsCount();

        if (cancelledCnt > 0) {
            res.append("Suite was cancelled, cancellation is always a blocker ");

            if (cancelledCnt > 1)
                res.append(" [").append(cancelledCnt).append("] \n");
        }

        addKnownProblemAsPossibleBlocker(res, "Timeout", getExecutionTimeoutCount(), baseBranchHist, true);
        addKnownProblemAsPossibleBlocker(res, "Jvm Crash", getJvmCrashProblemCount(), baseBranchHist, true);
        addKnownProblemAsPossibleBlocker(res, "Failure on metric", getMetricProblemCount(), baseBranchHist, true);
        addKnownProblemAsPossibleBlocker(res, "Compilation Error", getCompilationProblemCount(), baseBranchHist, true);

        addKnownProblemAsPossibleBlocker(res, "Out Of Memory Error", getBuildMessageProblemCount(), baseBranchHist, false);
        addKnownProblemAsPossibleBlocker(res, "Out Of Memory Error", getOomeProblemCount(), baseBranchHist, false);
        addKnownProblemAsPossibleBlocker(res, "Exit Code", getExitCodeProblemsCount(), baseBranchHist, false);

        if(hasAnyBuildProblemExceptTestOrSnapshot() && tcIgnited.config().trustedSuites().contains(suiteId())) {
            res.append("Suite is trusted but has build problems");

            res.append("[");
            res.append(allProblemsInAllBuilds()
                .filter(p -> !p.isFailedTests(compactor) && !p.isSnapshotDepProblem(compactor))
                .map(p -> p.type(compactor)).distinct().collect(Collectors.joining(", ")));
            res.append("] ");

            appendFailRate(baseBranchHist, res).append(" \n");
        }

        long jldl = getJavaLevelDeadlocksCount();
        if (jldl > 0) {
            res.append("Java Level Deadlock detected, it is unconditional blocker")
                .append(" ")
                .append(jldl > 1 ? "[" + jldl + "]" : "")
                .append(" \n");
        }

        return res.toString();
    }

    public void addKnownProblemAsPossibleBlocker(StringBuilder res, String nme, long execToCnt,
        @Nullable IRunHistory baseBranchHist, boolean critical) {
        if (execToCnt <= 0)
            return;

        res.append(nme).append(" is a blocker. ");

        if (critical)
            appendCriticalFailRate(baseBranchHist, res);
        else
            appendFailRate(baseBranchHist, res);

        res.append(execToCnt > 1 ? "[" + execToCnt + "]" : "").append(" \n");
    }

    public StringBuilder appendFailRate(IRunHistory baseBranchHist, StringBuilder res) {
        if (baseBranchHist == null)
            return res;

        res.append("Base branch fail rate is ");
        res.append(baseBranchHist.getFailPercentPrintable());
        res.append("% ");

        return res;
    }

    public StringBuilder appendCriticalFailRate(IRunHistory baseBranchHist, StringBuilder res) {
        if (baseBranchHist == null)
            return res;

        res.append("Base branch critical fail rate is ");
        res.append(baseBranchHist.getCriticalFailPercentPrintable());
        res.append("% ");

        return res;
    }
}
