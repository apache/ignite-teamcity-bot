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

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.ITcAnalytics;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.IMultTestOccurrence;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.analysis.TestLogCheckResult;
import org.apache.ignite.ci.issue.EventTemplates;
import org.apache.ignite.ci.issue.ProblemRef;
import org.apache.ignite.ci.teamcity.ignited.IRunHistory;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.web.model.hist.FailureSummary;
import org.apache.ignite.ci.web.rest.GetBuildLog;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.ci.teamcity.ignited.runhist.RunHistSync.normalizeBranch;
import static org.apache.ignite.ci.util.TimeUtil.millisToDurationPrintable;
import static org.apache.ignite.ci.util.UrlUtil.escape;

/**
 * Represent Suite result
 */
@SuppressWarnings("WeakerAccess") public class SuiteCurrentStatus extends FailureSummary {
    /** Use New run stat in PR analysis. */
    public static final boolean NEW_RUN_STAT = false;

    /** Suite Name */
    public String name;

    /** Suite Run Result (filled if failed): Summary of build problems, count of tests, etc. */
    public String result;

    /** Has critical problem: Timeout, JMV Crash or Compilation Error*/
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

    /** Tests duration printable. */
    public String testsDurationPrintable;

    /** Timed out builds average time. */
    public String lostInTimeouts;


    /**
     * Advisory mark there is problem in this suite.
     */
    @Nullable public ProblemRef problemRef;

    /** Possible blocker: filled for PR and builds checks, mean there was stable execution in master, but */
    public Boolean possibleBlocker;

    public void initFromContext(ITeamcityIgnited tcIgnited,
                                @Nonnull final ITeamcity teamcity,
                                @Nonnull final MultBuildRunCtx suite,
                                @NotNull final ITcAnalytics tcAnalytics,
                                @Nullable final String baseBranch) {

        name = suite.suiteName();

        String failRateNormalizedBranch = normalizeBranch(baseBranch);
        String curBranchNormalized = normalizeBranch(suite.branchName());

        Function<TestInBranch, ? extends IRunHistory> testStatProv = NEW_RUN_STAT
            ? tcIgnited::getTestRunHist
            : tcAnalytics.getTestRunStatProvider();

        String suiteId = suite.suiteId();

        Function<SuiteInBranch, ? extends IRunHistory> provider   =
            NEW_RUN_STAT
                ? tcIgnited::getSuiteRunHist
                : tcAnalytics.getBuildFailureRunStatProvider();

        initSuiteStat(provider, failRateNormalizedBranch, curBranchNormalized, suiteId);

        Set<String> collect = suite.lastChangeUsers().collect(Collectors.toSet());

        if(!collect.isEmpty())
            userCommits = collect.toString();

        result = suite.getResult();
        hasCriticalProblem = suite.hasCriticalProblem();
        failedTests = suite.failedTests();
        durationPrintable = millisToDurationPrintable(suite.getBuildDuration());
        testsDurationPrintable  = millisToDurationPrintable(suite.getAvgTestsDuration());
        webToHist = buildWebLink(teamcity, suite);
        webToHistBaseBranch = buildWebLink(teamcity, suite, baseBranch);
        webToBuild = buildWebLinkToBuild(teamcity, suite);

        List<IMultTestOccurrence> tests = suite.getFailedTests();
        Function<IMultTestOccurrence, Float> function = foccur -> {
            TestInBranch testInBranch = new TestInBranch(foccur.getName(), failRateNormalizedBranch);

            IRunHistory apply = testStatProv.apply(testInBranch);

            return apply == null ? 0f : apply.getFailRate();
        };

        tests.sort(Comparator.comparing(function).reversed());

        tests.forEach(occurrence -> {
            final TestFailure failure = new TestFailure();
            failure.initFromOccurrence(occurrence, tcIgnited, suite.projectId(), suite.branchName(), baseBranch);
            failure.initStat(testStatProv, failRateNormalizedBranch, curBranchNormalized);

            testFailures.add(failure);
        });

        suite.getTopLongRunning().forEach(occurrence -> {
            final TestFailure failure = createOrrucForLongRun(tcIgnited, suite, tcAnalytics,
                occurrence, baseBranch, testStatProv);

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

        suite.getBuildsWithThreadDump().forEach(buildId -> {
            webUrlThreadDump = "/rest/" + GetBuildLog.GET_BUILD_LOG + "/" + GetBuildLog.THREAD_DUMP
                + "?" + GetBuildLog.SERVER_ID + "=" + teamcity.serverId()
                + "&" + GetBuildLog.BUILD_NO + "=" + buildId
                + "&" + GetBuildLog.FILE_IDX + "=" + -1;
        });

        runningBuildCount = suite.runningBuildCount();
        queuedBuildCount = suite.queuedBuildCount();
        serverId = teamcity.serverId();
        this.suiteId = suite.suiteId();
        branchName = branchForLink(suite.branchName());
        // todo implement this logic in suite possibleBlocker = suite.hasPossibleBlocker();
    }

    private void initSuiteStat(Function<SuiteInBranch, ? extends IRunHistory> suiteFailProv,
        String failRateNormalizedBranch,
        String curBranchNormalized,
        String suiteId) {
        if (Strings.isNullOrEmpty(suiteId)  )
            return;

        SuiteInBranch key = new SuiteInBranch(suiteId, failRateNormalizedBranch);

        final IRunHistory stat = suiteFailProv.apply(key);

        if (stat != null) {
            failures = stat.getFailuresCount();
            runs = stat.getRunsCount();
            failureRate = stat.getFailPercentPrintable();

            criticalFails.failures = stat.getCriticalFailuresCount();
            criticalFails.runs = runs;
            criticalFails.failureRate = stat.getCriticalFailPercentPrintable();

            failsAllHist.failures = stat.getFailuresAllHist();
            failsAllHist.runs = stat.getRunsAllHist();
            failsAllHist.failureRate = stat.getFailPercentAllHistPrintable();

            latestRuns = stat.getLatestRunResults();
        }

        IRunHistory latestRunsSrc = null;
        if (!failRateNormalizedBranch.equals(curBranchNormalized)) {
            SuiteInBranch keyForStripe = new SuiteInBranch(suiteId, curBranchNormalized);

            final IRunHistory statForStripe = suiteFailProv.apply(keyForStripe);

            latestRunsSrc = statForStripe;
            latestRuns = statForStripe != null ? statForStripe.getLatestRunResults() : null;
        } else
            latestRunsSrc = stat;

        if (latestRunsSrc instanceof RunStat) {
            RunStat latestRunsSrcV1 = (RunStat)latestRunsSrc;
            RunStat.TestId testId = latestRunsSrcV1.detectTemplate(EventTemplates.newFailureForFlakyTest); //extended runs required for suite

            if (testId != null)
                problemRef = new ProblemRef("New Failure");

            RunStat.TestId buildIdCritical = latestRunsSrcV1.detectTemplate(EventTemplates.newCriticalFailure);

            if (buildIdCritical != null)
                problemRef = new ProblemRef("New Critical Failure");
        }
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
        @Nullable final ITcAnalytics tcAnalytics,
        final IMultTestOccurrence occurrence,
        @Nullable final String failRateBranch,
        Function<TestInBranch, ? extends IRunHistory> supplier) {
        final TestFailure failure = new TestFailure();

        failure.initFromOccurrence(occurrence, tcIgnited, suite.projectId(), suite.branchName(), failRateBranch);

        if (tcAnalytics != null) {
            failure.initStat(supplier,
                normalizeBranch(failRateBranch),
                normalizeBranch(suite.branchName()));
        }

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

    private static String buildWebLinkToBuild(ITeamcity teamcity, MultBuildRunCtx suite) {
        return teamcity.host() + "viewLog.html?buildId=" + Integer.toString(suite.getBuildId());
    }

    private static String buildWebLink(ITeamcity teamcity, MultBuildRunCtx suite) {
        String branchName = suite.branchName();

        return buildWebLink(teamcity, suite, branchName);
    }

    @NotNull private static String buildWebLink(ITeamcity teamcity, MultBuildRunCtx suite, String branchName) {
        final String branch = branchForLink(branchName);
        return teamcity.host() + "viewType.html?buildTypeId=" + suite.suiteId()
            + "&branch=" + escape(branch)
            + "&tab=buildTypeStatusDiv";
    }

    public static String branchForLink(@Nullable String branchName) {
        return branchName == null || "refs/heads/master".equals(branchName) ? ITeamcity.DEFAULT : branchName;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SuiteCurrentStatus status = (SuiteCurrentStatus)o;
        return Objects.equal(name, status.name) &&
            Objects.equal(result, status.result) &&
            Objects.equal(hasCriticalProblem, status.hasCriticalProblem) &&
            Objects.equal(webToHist, status.webToHist) &&
            Objects.equal(webToHistBaseBranch, status.webToHistBaseBranch) &&
            Objects.equal(webToBuild, status.webToBuild) &&
            Objects.equal(testFailures, status.testFailures) &&
            Objects.equal(topLongRunning, status.topLongRunning) &&
            Objects.equal(webUrlThreadDump, status.webUrlThreadDump) &&
            Objects.equal(runningBuildCount, status.runningBuildCount) &&
            Objects.equal(queuedBuildCount, status.queuedBuildCount) &&
            Objects.equal(serverId, status.serverId) &&
            Objects.equal(suiteId, status.suiteId) &&
            Objects.equal(branchName, status.branchName) &&
            Objects.equal(failures, status.failures) &&
            Objects.equal(runs, status.runs) &&
            Objects.equal(failureRate, status.failureRate) &&
            Objects.equal(failsAllHist, status.failsAllHist) &&
            Objects.equal(criticalFails, status.criticalFails) &&
            Objects.equal(userCommits, status.userCommits) &&
            Objects.equal(failedTests, status.failedTests) &&
            Objects.equal(durationPrintable, status.durationPrintable) &&
            Objects.equal(testsDurationPrintable, status.testsDurationPrintable) &&
            Objects.equal(lostInTimeouts, status.lostInTimeouts) &&
            Objects.equal(warnOnly, status.warnOnly);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(name, result, hasCriticalProblem, webToHist, webToHistBaseBranch, webToBuild, testFailures,
            topLongRunning, webUrlThreadDump, runningBuildCount, queuedBuildCount, serverId,
            suiteId, branchName, failures, runs, failureRate,
            failsAllHist, criticalFails, userCommits, failedTests, durationPrintable, testsDurationPrintable,
            lostInTimeouts, warnOnly);
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
}
