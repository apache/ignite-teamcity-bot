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

package org.apache.ignite.ci.web.model.current;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.analysis.IMultTestOccurrence;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.analysis.TestLogCheckResult;
import org.apache.ignite.ci.issue.EventTemplates;
import org.apache.ignite.ci.issue.ProblemRef;
import org.apache.ignite.ci.teamcity.ignited.IRunHistory;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.web.model.hist.FailureSummary;
import org.apache.ignite.ci.web.rest.GetBuildLog;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.ci.teamcity.ignited.runhist.RunHistSync.normalizeBranch;
import static org.apache.ignite.tcbot.common.util.TimeUtil.millisToDurationPrintable;
import static org.apache.ignite.ci.util.UrlUtil.escape;

/**
 * Represent Suite result
 */
@SuppressWarnings("WeakerAccess") public class SuiteCurrentStatus extends FailureSummary {
    /** Suite Name */
    public String name;

    /** Suite Run Result (filled if failed): Summary of build problems, count of tests, etc. */
    public String result;

    /** Has critical problem: Timeout, JMV Crash, Compilation Error or Failure on Metric */
    @Nullable public Boolean hasCriticalProblem;

    /** Web Href. to suite runs history */
    public String webToHist = "";

    /** Web Href. to suite runs history in base branch */
    public String webToHistBaseBranch = "";

    /** Web Href. to suite particular run */
    public String webToBuild = "";

    public List<TestFailure> testFailures = new ArrayList<>();
    public List<TestFailure> topLongRunning = new ArrayList<>();
    public List<TestFailure> warnOnly = new ArrayList<>();
    public List<TestFailure> logConsumers = new ArrayList<>();

    /** Web Href. to thread dump display */
    @Nullable public String webUrlThreadDump;

    @Nullable public Integer runningBuildCount;
    @Nullable public Integer queuedBuildCount;

    /** TC getOrCreateCreds id. */
    public String serverId;

    /** Suite ID in teamcity identification. */
    public String suiteId;

    /** Branch name in teamcity identification. */
    public String branchName;

    /** Failure summary in tracked branch according to all runs history. */
    @Nonnull public FailureSummary failsAllHist = new FailureSummary();

    /** Failure summary in tracked branch according to all runs history. */
    @Nonnull public FailureSummary criticalFails = new FailureSummary();

    /** Latest runs, 0,1,3 values for each run. */
    @Nullable public List<Integer> latestRuns;

    /** TcHelperUser commits, comma separated string. */
    public String userCommits = "";

    public Integer failedTests;

    /** Duration printable. */
    public String durationPrintable;

    /** Duration net time printable. */
    public String durationNetTimePrintable;

    public String sourceUpdateDurationPrintable;

    public String artifcactPublishingDurationPrintable;

    public String dependeciesResolvingDurationPrintable;

    /** Tests duration printable. */
    public String testsDurationPrintable;

    /** Timed out builds average time. */
    public String lostInTimeouts;

    /**
     * Advisory mark there is problem in this suite.
     */
    @Nullable public ProblemRef problemRef;

    public Set<String> tags = new HashSet<>();

    /**
     * Possible blocker comment: filled for PR and builds checks, non null value contains problem explanation
     * displayable.
     */
    @Nullable public String blockerComment;

    public SuiteCurrentStatus initFromContext(ITeamcityIgnited tcIgnited,
        @Nonnull final MultBuildRunCtx suite,
        @Nullable final String baseBranch,
        @Nonnull IStringCompactor compactor,
        boolean includeTests) {

        name = suite.suiteName();

        String failRateNormalizedBranch = normalizeBranch(baseBranch);
        String curBranchNormalized = normalizeBranch(suite.branchName());

        IRunHistory baseBranchHist = initSuiteStat(tcIgnited, failRateNormalizedBranch, curBranchNormalized, suite.suiteId());

        Set<String> collect = suite.lastChangeUsers().collect(Collectors.toSet());

        if (!collect.isEmpty())
            userCommits = collect.toString();

        result = suite.getResult();
        hasCriticalProblem = suite.hasCriticalProblem();
        failedTests = suite.failedTests();
        durationPrintable = millisToDurationPrintable(suite.buildDuration());
        durationNetTimePrintable = millisToDurationPrintable(suite.buildDurationNetTime());
        sourceUpdateDurationPrintable = millisToDurationPrintable(suite.sourceUpdateDuration());
        artifcactPublishingDurationPrintable = millisToDurationPrintable(suite.artifcactPublishingDuration());
        dependeciesResolvingDurationPrintable = millisToDurationPrintable(suite.dependeciesResolvingDuration());
        testsDurationPrintable = millisToDurationPrintable(suite.getAvgTestsDuration());
        webToHist = buildWebLink(tcIgnited, suite);
        webToHistBaseBranch = buildWebLink(tcIgnited, suite, baseBranch);
        webToBuild = buildWebLinkToBuild(tcIgnited, suite);

        if (includeTests) {
            List<IMultTestOccurrence> tests = suite.getFailedTests();
            Function<IMultTestOccurrence, Float> function = foccur -> {
                TestInBranch testInBranch = new TestInBranch(foccur.getName(), failRateNormalizedBranch);

                IRunHistory apply = tcIgnited.getTestRunHist(testInBranch);

                return apply == null ? 0f : apply.getFailRate();
            };

            tests.sort(Comparator.comparing(function).reversed());

            tests.forEach(occurrence -> {
                final TestFailure failure = new TestFailure();
                failure.initFromOccurrence(occurrence, tcIgnited, suite.projectId(), suite.branchName(), baseBranch);
                failure.initStat(tcIgnited, failRateNormalizedBranch, curBranchNormalized);

                testFailures.add(failure);
            });

            suite.getTopLongRunning().forEach(occurrence -> {
                final TestFailure failure = createOrrucForLongRun(tcIgnited, suite, occurrence, baseBranch);

                topLongRunning.add(failure);
            });

            suite.getCriticalFailLastStartedTest().forEach(
                lastTest -> {
                    final TestFailure failure = new TestFailure();
                    failure.name = lastTest + " (last started)";
                    testFailures.add(failure);
                }
            );

            suite.getLogsCheckResults().forEach(map -> {
                    map.forEach(
                        (testName, logCheckResult) -> {
                            if (logCheckResult.hasWarns())
                                this.findFailureAndAddWarning(testName, logCheckResult);

                        }
                    );
                }
            );

            suite.getTopLogConsumers().forEach(
                (entry) -> logConsumers.add(createOccurForLogConsumer(entry))
            );
        }

        suite.getBuildsWithThreadDump().forEach(buildId -> {
            webUrlThreadDump = "/rest/" + GetBuildLog.GET_BUILD_LOG + "/" + GetBuildLog.THREAD_DUMP
                + "?" + GetBuildLog.SERVER_ID + "=" + tcIgnited.serverId()
                + "&" + GetBuildLog.BUILD_NO + "=" + buildId
                + "&" + GetBuildLog.FILE_IDX + "=" + -1;
        });

        runningBuildCount = suite.runningBuildCount();
        queuedBuildCount = suite.queuedBuildCount();
        serverId = tcIgnited.serverId();
        this.suiteId = suite.suiteId();
        branchName = branchForLink(suite.branchName());

        tags = suite.tags();

        blockerComment = suite.getPossibleBlockerComment(compactor, baseBranchHist, tcIgnited.config());

        return this;
    }

    private IRunHistory initSuiteStat(ITeamcityIgnited tcIgnited,
        String failRateNormalizedBranch,
        String curBranchNormalized,
        String suiteId) {
        if (Strings.isNullOrEmpty(suiteId))
            return null;

        final IRunHistory statInBaseBranch = tcIgnited.getSuiteRunHist(new SuiteInBranch(suiteId, failRateNormalizedBranch));

        if (statInBaseBranch != null) {
            failures = statInBaseBranch.getFailuresCount();
            runs = statInBaseBranch.getRunsCount();
            failureRate = statInBaseBranch.getFailPercentPrintable();

            criticalFails.failures = statInBaseBranch.getCriticalFailuresCount();
            criticalFails.runs = runs;
            criticalFails.failureRate = statInBaseBranch.getCriticalFailPercentPrintable();

            failsAllHist.failures = statInBaseBranch.getFailuresAllHist();
            failsAllHist.runs = statInBaseBranch.getRunsAllHist();
            failsAllHist.failureRate = statInBaseBranch.getFailPercentAllHistPrintable();

            latestRuns = statInBaseBranch.getLatestRunResults();
        }

        IRunHistory latestRunsSrc = null;
        if (!failRateNormalizedBranch.equals(curBranchNormalized)) {
            SuiteInBranch keyForStripe = new SuiteInBranch(suiteId, curBranchNormalized);

            final IRunHistory statForStripe = tcIgnited.getSuiteRunHist(keyForStripe);

            latestRunsSrc = statForStripe;
            latestRuns = statForStripe != null ? statForStripe.getLatestRunResults() : null;
        }
        else
            latestRunsSrc = statInBaseBranch;

        if (latestRunsSrc != null) {
            if (latestRunsSrc.detectTemplate(EventTemplates.newFailureForFlakyTest) != null)
                problemRef = new ProblemRef("New Failure");

            if (latestRunsSrc.detectTemplate(EventTemplates.newCriticalFailure) != null)
                problemRef = new ProblemRef("New Critical Failure");
        }

        return statInBaseBranch;
    }

    @NotNull
    public static TestFailure createOccurForLogConsumer(Map.Entry<String, Long> entry) {
        TestFailure failure = new TestFailure();
        long sizeMb = entry.getValue() / 1024 / 1024;
        failure.name = entry.getKey() + " " + sizeMb + " Mbytes";
        return failure;
    }

    @NotNull public static TestFailure createOrrucForLongRun(ITeamcityIgnited tcIgnited,
        @Nonnull MultBuildRunCtx suite,
        final IMultTestOccurrence occurrence,
        @Nullable final String failRateBranch) {
        final TestFailure failure = new TestFailure();

        failure.initFromOccurrence(occurrence, tcIgnited, suite.projectId(), suite.branchName(), failRateBranch);

        failure.initStat(tcIgnited,
            normalizeBranch(failRateBranch),
            normalizeBranch(suite.branchName()));

        return failure;
    }

    public void findFailureAndAddWarning(String testName, TestLogCheckResult logCheckRes) {
        TestFailure failure = testFailures.stream().filter(f -> f.name.contains(testName)).findAny().orElseGet(
            () -> {
                return warnOnly.stream().filter(f -> f.name.contains(testName)).findAny().orElseGet(
                    () -> {
                        TestFailure f = new TestFailure();
                        f.name = testName + " (warning)";
                        warnOnly.add(f);

                        return f;
                    });
            });

        failure.warnings.addAll(logCheckRes.getWarns());
    }

    private static String buildWebLinkToBuild(ITeamcityIgnited teamcity, MultBuildRunCtx suite) {
        return teamcity.host() + "viewLog.html?buildId=" + Integer.toString(suite.getBuildId());
    }

    private static String buildWebLink(ITeamcityIgnited teamcity, MultBuildRunCtx suite) {
        String branchName = suite.branchName();

        return buildWebLink(teamcity, suite, branchName);
    }

    @NotNull private static String buildWebLink(ITeamcityIgnited teamcity, MultBuildRunCtx suite, String branchName) {
        final String branch = branchForLink(branchName);
        return teamcity.host() + "viewType.html?buildTypeId=" + suite.suiteId()
            + "&branch=" + escape(branch)
            + "&tab=buildTypeStatusDiv";
    }

    public static String branchForLink(@Nullable String branchName) {
        return normalizeBranch(branchName);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        SuiteCurrentStatus status = (SuiteCurrentStatus)o;
        return Objects.equals(name, status.name) &&
            Objects.equals(result, status.result) &&
            Objects.equals(hasCriticalProblem, status.hasCriticalProblem) &&
            Objects.equals(webToHist, status.webToHist) &&
            Objects.equals(webToHistBaseBranch, status.webToHistBaseBranch) &&
            Objects.equals(webToBuild, status.webToBuild) &&
            Objects.equals(testFailures, status.testFailures) &&
            Objects.equals(topLongRunning, status.topLongRunning) &&
            Objects.equals(warnOnly, status.warnOnly) &&
            Objects.equals(logConsumers, status.logConsumers) &&
            Objects.equals(webUrlThreadDump, status.webUrlThreadDump) &&
            Objects.equals(runningBuildCount, status.runningBuildCount) &&
            Objects.equals(queuedBuildCount, status.queuedBuildCount) &&
            Objects.equals(serverId, status.serverId) &&
            Objects.equals(suiteId, status.suiteId) &&
            Objects.equals(branchName, status.branchName) &&
            Objects.equals(failsAllHist, status.failsAllHist) &&
            Objects.equals(criticalFails, status.criticalFails) &&
            Objects.equals(latestRuns, status.latestRuns) &&
            Objects.equals(userCommits, status.userCommits) &&
            Objects.equals(failedTests, status.failedTests) &&
            Objects.equals(durationPrintable, status.durationPrintable) &&
            Objects.equals(durationNetTimePrintable, status.durationNetTimePrintable) &&
            Objects.equals(sourceUpdateDurationPrintable, status.sourceUpdateDurationPrintable) &&
            Objects.equals(artifcactPublishingDurationPrintable, status.artifcactPublishingDurationPrintable) &&
            Objects.equals(dependeciesResolvingDurationPrintable, status.dependeciesResolvingDurationPrintable) &&
            Objects.equals(testsDurationPrintable, status.testsDurationPrintable) &&
            Objects.equals(lostInTimeouts, status.lostInTimeouts) &&
            Objects.equals(problemRef, status.problemRef) &&
            Objects.equals(blockerComment, status.blockerComment);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(super.hashCode(), name, result, hasCriticalProblem, webToHist,
            webToHistBaseBranch, webToBuild, testFailures, topLongRunning, warnOnly, logConsumers, webUrlThreadDump,
            runningBuildCount, queuedBuildCount, serverId, suiteId, branchName, failsAllHist, criticalFails, latestRuns,
            userCommits, failedTests, durationPrintable, durationNetTimePrintable, sourceUpdateDurationPrintable,
            artifcactPublishingDurationPrintable, dependeciesResolvingDurationPrintable, testsDurationPrintable,
            lostInTimeouts, problemRef, blockerComment);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("[").append(name).append("]").append("\n");

        testFailures.forEach(
            tf -> builder.append(tf.toString())
        );
        builder.append("\n");

        return builder.toString();
    }

    public String branchName() {
        return branchName;
    }

    public int totalBlockers() {
        int res = 0;
        if (!Strings.isNullOrEmpty(blockerComment))
            res++;

        res += (int)testFailures.stream().filter(TestFailure::isPossibleBlocker).count();

        return res;
    }
}
