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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.ITcAnalytics;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.*;
import org.apache.ignite.ci.issue.EventTemplates;
import org.apache.ignite.ci.issue.ProblemRef;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.web.model.hist.FailureSummary;
import org.apache.ignite.ci.web.rest.GetBuildLog;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.ci.tcbot.chain.BuildChainProcessor.normalizeBranch;
import static org.apache.ignite.ci.util.TimeUtil.millisToDurationPrintable;
import static org.apache.ignite.ci.util.UrlUtil.escape;

/**
 * Represent Suite result
 */
@SuppressWarnings("WeakerAccess") public class SuiteCurrentStatus extends FailureSummary {
    /** Suite Name */
    public String name;

    /** Suite Run Result (filled if failed): Summary of build problems, count of tests, etc. */
    public String result;

    /** Has critical problem: Timeout or JMV Crash */
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

    public String durationPrintable;

    /**
     * Advisory mark there is problem in this suite.
     */
    @Nullable public ProblemRef problemRef;

    /** Possible blocker: filled for PR and builds checks, mean there was stable execution in master, but */
    public Boolean possibleBlocker;

    public void initFromContext(@Nonnull final ITeamcity teamcity,
        @Nonnull final MultBuildRunCtx suite,
        @NotNull final ITcAnalytics tcAnalytics,
        @Nullable final String baseBranch) {

        name = suite.suiteName();

        String failRateNormalizedBranch = normalizeBranch(baseBranch);
        String curBranchNormalized = normalizeBranch(suite.branchName());

        String suiteId = suite.suiteId();
        initStat(tcAnalytics, failRateNormalizedBranch, curBranchNormalized, suiteId);

        Set<String> collect = suite.lastChangeUsers().collect(Collectors.toSet());

        if(!collect.isEmpty())
            userCommits = collect.toString();

        result = suite.getResult();
        hasCriticalProblem = suite.hasCriticalProblem();
        failedTests = suite.failedTests();
        durationPrintable = millisToDurationPrintable(suite.getBuildDuration());
        webToHist = buildWebLink(teamcity, suite);
        webToHistBaseBranch = buildWebLink(teamcity, suite, baseBranch);
        webToBuild = buildWebLinkToBuild(teamcity, suite);

        List<ITestFailures> tests = suite.getFailedTests();
        Function<ITestFailures, Float> function = foccur -> {
            TestInBranch testInBranch = new TestInBranch(foccur.getName(), failRateNormalizedBranch);

            RunStat apply = tcAnalytics.getTestRunStatProvider().apply(testInBranch);

            return apply == null ? 0f : apply.getFailRate();
        };

        tests.sort(Comparator.comparing(function).reversed());

        tests.forEach(occurrence -> {
            final TestFailure failure = new TestFailure();
            failure.initFromOccurrence(occurrence, teamcity, suite.projectId(), suite.branchName(), baseBranch);
            failure.initStat(tcAnalytics.getTestRunStatProvider(), failRateNormalizedBranch, curBranchNormalized);

            testFailures.add(failure);
        });

        suite.getTopLongRunning().forEach(occurrence -> {
            final TestFailure failure = createOrrucForLongRun(teamcity, suite, tcAnalytics, occurrence, baseBranch);

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

        Stream<Map.Entry<String, Long>> stream = suite.getTopLogConsumers();

        stream.forEach(
            (entry) -> {
                TestFailure failure = createOccurForLogConsumer(entry);
                logConsumers.add(failure);
            }
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

    private void initStat(@Nullable ITcAnalytics tcAnalytics, String failRateNormalizedBranch, String curBranchNormalized, String suiteId) {
        if (Strings.isNullOrEmpty(suiteId) || tcAnalytics == null)
            return;

        SuiteInBranch key = new SuiteInBranch(suiteId, failRateNormalizedBranch);

        final RunStat stat = tcAnalytics.getBuildFailureRunStatProvider().apply(key);

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

        RunStat latestRunsSrc = null;
        if (!failRateNormalizedBranch.equals(curBranchNormalized)) {
            SuiteInBranch keyForStripe = new SuiteInBranch(suiteId, curBranchNormalized);

            final RunStat statForStripe = tcAnalytics.getBuildFailureRunStatProvider().apply(keyForStripe);

            latestRunsSrc = statForStripe;
            latestRuns = statForStripe != null ? statForStripe.getLatestRunResults() : null;
        } else
            latestRunsSrc = stat;

        if (latestRunsSrc != null) {
            RunStat.TestId testId = latestRunsSrc.detectTemplate(EventTemplates.newFailureForFlakyTest); //extended runs required for suite

            if (testId != null)
                problemRef = new ProblemRef("New Failure");

            RunStat.TestId buildIdCritical = latestRunsSrc.detectTemplate(EventTemplates.newCriticalFailure);

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

    @NotNull public static TestFailure createOrrucForLongRun(@Nonnull ITeamcity teamcity,
        @Nonnull MultBuildRunCtx suite,
        @Nullable final ITcAnalytics tcAnalytics,
        final ITestFailures occurrence,
        @Nullable final String failRateBranch) {
        final TestFailure failure = new TestFailure();

        failure.initFromOccurrence(occurrence, teamcity, suite.projectId(), suite.branchName(), failRateBranch);

        if (tcAnalytics != null) {
            failure.initStat(tcAnalytics.getTestRunStatProvider(),
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
            Objects.equal(durationPrintable, status.durationPrintable)&&
            Objects.equal(warnOnly, status.warnOnly);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(name, result, hasCriticalProblem, webToHist, webToHistBaseBranch, webToBuild, testFailures,
            topLongRunning, webUrlThreadDump, runningBuildCount, queuedBuildCount, serverId,
            suiteId, branchName, failures, runs, failureRate,
            failsAllHist, criticalFails, userCommits, failedTests, durationPrintable,
            warnOnly);
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
