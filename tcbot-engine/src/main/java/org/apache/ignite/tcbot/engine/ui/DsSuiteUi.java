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

package org.apache.ignite.tcbot.engine.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.common.util.UrlUtil;
import org.apache.ignite.tcbot.engine.chain.MultBuildRunCtx;
import org.apache.ignite.tcbot.engine.chain.TestCompactedMult;
import org.apache.ignite.tcbot.engine.issue.EventTemplates;
import org.apache.ignite.tcbot.engine.ui.BotUrls.GetBuildLog;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.buildlog.ITestLogCheckResult;
import org.apache.ignite.tcignited.history.IRunHistory;

import static org.apache.ignite.tcbot.common.util.TimeUtil.millisToDurationPrintable;
import static org.apache.ignite.tcignited.buildref.BranchEquivalence.normalizeBranch;


/**
 * Detailed status of failures: Suite failures.
 *
 * Represent Suite result, UI class for REST responses, so it contains public fields
 */
@SuppressWarnings({"WeakerAccess", "PublicField"})
public class DsSuiteUi extends ShortSuiteUi {
    /** Has critical problem: Timeout, JMV Crash, Compilation Error or Failure on Metric */
    @Nullable public Boolean hasCriticalProblem;

    /** Web Href. to suite runs history */
    public String webToHist = "";

    /** Web Href. to suite runs history in base branch */
    public String webToHistBaseBranch = "";

    public List<DsTestFailureUi> testFailures = new ArrayList<>();
    public List<DsTestFailureUi> topLongRunning = new ArrayList<>();
    public List<DsTestFailureUi> warnOnly = new ArrayList<>();
    public List<DsTestFailureUi> logConsumers = new ArrayList<>();

    /** Web Href. to thread dump display */
    @Nullable public String webUrlThreadDump;

    @Nullable public Integer runningBuildCount;
    @Nullable public Integer queuedBuildCount;

    /** TC server id. To be replaced by separated services IDs */
    @Deprecated
    public String serverId;

    /** Suite ID in teamcity identification. */
    public String suiteId;

    /** Branch name in teamcity identification. */
    public String branchName;

    /** Failure summary in tracked branch according to all runs history. */
    @Nonnull public DsHistoryStatUi criticalFails = new DsHistoryStatUi();

    /** Latest runs, 0,1,3 values for each run. */
    @Nullable public List<Integer> latestRuns;

    /** TcHelperUser commits, comma separated string. */
    public String userCommits = "";

    /** Count of failed tests not muted tests. In case several runs are used, overall by all runs. */
    public Integer failedTests;

    /** Count of executed tests during current run. In case several runs are used, average number. */
    public Integer totalTests;

    /** Tests which will not considered as a blocker and not filtered out. */
    public Integer trustedTests;

    /** Duration printable. */
    public String durationPrintable;

    /** Duration net time printable. */
    public String durationNetTimePrintable;

    /** Source update duration printable. */
    public String sourceUpdateDurationPrintable;

    /** Artifcact publishing duration printable. */
    public String artifcactPublishingDurationPrintable;

    /** Dependecies resolving duration printable. */
    public String dependeciesResolvingDurationPrintable;

    /** Tests duration printable. */
    public String testsDurationPrintable;

    /** Timed out builds average time. */
    public String lostInTimeouts;

    /**
     * Advisory mark there is problem in this suite.
     */
    @Nullable public DsProblemRef problemRef;

    /** Tags for build. */
    @Nonnull public Set<String> tags = new HashSet<>();


    public boolean success = false;

    /**
     * @param tcIgnited Tc ignited.
     * @param suite Suite.
     * @param baseBranch Base branch.
     * @param compactor String Compactor.
     * @param calcTrustedTests
     * @param maxDurationSec 0 or negative means don't indclude. has no effect if tests not included
     * @param requireParamVal filtering for runs history based on parameter value selected.
     * @param showMuted
     * @param showIgnored
     */
    public DsSuiteUi initFromContext(ITeamcityIgnited tcIgnited,
        @Nonnull final MultBuildRunCtx suite,
        @Nullable final String baseBranch,
        @Nonnull IStringCompactor compactor,
        boolean calcTrustedTests,
        int maxDurationSec,
        @Nullable Map<Integer, Integer> requireParamVal,
        boolean showMuted,
        boolean showIgnored) {

        String failRateNormalizedBranch = normalizeBranch(baseBranch);
        Integer baseBranchId = compactor.getStringIdIfPresent(failRateNormalizedBranch);
        IRunHistory baseBranchHist = suite.history(tcIgnited, baseBranchId, requireParamVal);
        initFrom(suite, tcIgnited, compactor, baseBranchHist);

        String curBranchNormalized = normalizeBranch(suite.branchName());
        Integer curBranchId = compactor.getStringIdIfPresent(curBranchNormalized);

        initSuiteStat(tcIgnited, baseBranchId, curBranchId, suite, baseBranchHist, requireParamVal);

        Set<String> collect = suite.lastChangeUsers().collect(Collectors.toSet());

        if (!collect.isEmpty())
            userCommits = collect.toString();

        hasCriticalProblem = suite.hasCriticalProblem();
        failedTests = suite.failedTests();
        durationPrintable = millisToDurationPrintable(suite.buildDuration());
        durationNetTimePrintable = millisToDurationPrintable(suite.buildDurationNetTime());
        sourceUpdateDurationPrintable = millisToDurationPrintable(suite.sourceUpdateDuration());
        artifcactPublishingDurationPrintable = millisToDurationPrintable(suite.artifcactPublishingDuration());
        dependeciesResolvingDurationPrintable = millisToDurationPrintable(suite.dependeciesResolvingDuration());
        testsDurationPrintable = millisToDurationPrintable(suite.getAvgTestsDuration());
        webToHist = buildWebLinkToHist(tcIgnited, suite, suite.branchName());
        webToHistBaseBranch = buildWebLinkToHist(tcIgnited, suite, baseBranch);

        if (true) {
            List<TestCompactedMult> tests = suite.getFilteredTests(test ->
                test.hasLongRunningTest(maxDurationSec)
                    || test.includeIntoReport(tcIgnited, baseBranchId, showMuted, showIgnored));

            Function<TestCompactedMult, Float> function = testCompactedMult -> {
                IRunHistory res = testCompactedMult.history(tcIgnited, baseBranchId);

                return res == null ? 0f : res.getFailRate();
            };

            tests.sort(Comparator.comparing(function).reversed());

            tests.stream()
                .map(occurrence -> new DsTestFailureUi()
                    .initFromOccurrence(occurrence,
                        tcIgnited,
                        suite.projectId(),
                        suite.branchName(),
                        baseBranch,
                        baseBranchId,
                        curBranchId,
                        requireParamVal))
                .forEach(testFailureUi -> testFailures.add(testFailureUi));

            suite.getTopLongRunning().forEach(occurrence -> {
                if (occurrence.getAvgDurationMs() > TimeUnit.SECONDS.toMillis(15)) {
                    final DsTestFailureUi failure = createOrrucForLongRun(tcIgnited, compactor, suite, occurrence, baseBranch, requireParamVal);

                    topLongRunning.add(failure);
                }
            });

            suite.getCriticalFailLastStartedTest().forEach(
                lastTest -> {
                    final DsTestFailureUi failure = new DsTestFailureUi();
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


            totalTests = suite.totalTests();

            if(calcTrustedTests)
                trustedTests = suite.trustedTests(tcIgnited, baseBranchId);
        }

        suite.getBuildsWithThreadDump().forEach(buildId -> {
            webUrlThreadDump = "/rest/" + GetBuildLog.GET_BUILD_LOG + "/" + GetBuildLog.THREAD_DUMP
                + "?" + GetBuildLog.SERVER_ID + "=" + tcIgnited.serverCode()
                + "&" + GetBuildLog.BUILD_NO + "=" + buildId
                + "&" + GetBuildLog.FILE_IDX + "=" + -1;
        });

        runningBuildCount = suite.runningBuildCount();
        queuedBuildCount = suite.queuedBuildCount();
        serverId = tcIgnited.serverCode();
        suiteId = suite.suiteId();
        branchName = normalizeBranch(suite.branchName());

        tags = suite.tags();

        success = !suite.isFailed();

        return this;
    }

    private IRunHistory initSuiteStat(ITeamcityIgnited tcIgnited,
        Integer failRateNormalizedBranch,
        Integer curBranchNormalized,
        MultBuildRunCtx suite,
        IRunHistory referenceStat,
        @Nullable Map<Integer, Integer> requireParamVal) {
        IRunHistory statInBaseBranch = referenceStat;

        if (statInBaseBranch != null) {
            failures = statInBaseBranch.getFailuresCount();
            runs = statInBaseBranch.getRunsCount();
            failureRate = statInBaseBranch.getFailPercentPrintable();

            criticalFails.failures = statInBaseBranch.getCriticalFailuresCount();
            criticalFails.runs = runs;
            criticalFails.failureRate = statInBaseBranch.getCriticalFailPercentPrintable();

            latestRuns = statInBaseBranch.getLatestRunResults();
        }

        IRunHistory latestRunsSrc = null;
        if (!Objects.equals(failRateNormalizedBranch, curBranchNormalized)) {
            IRunHistory statForStripe = suite.history(tcIgnited, curBranchNormalized, requireParamVal);

            latestRunsSrc = statForStripe;
            latestRuns = statForStripe != null ? statForStripe.getLatestRunResults() : null;
        }
        else
            latestRunsSrc = statInBaseBranch;

        if (latestRunsSrc != null) {
            if (latestRunsSrc.detectTemplate(EventTemplates.newFailureForFlakyTest) != null)
                problemRef = new DsProblemRef("New Failure");

            if (latestRunsSrc.detectTemplate(EventTemplates.newCriticalFailure) != null)
                problemRef = new DsProblemRef("New Critical Failure");
        }

        return statInBaseBranch;
    }

    @Nonnull
    public static DsTestFailureUi createOccurForLogConsumer(Map.Entry<String, Long> entry) {
        DsTestFailureUi failure = new DsTestFailureUi();
        long sizeMb = entry.getValue() / 1024 / 1024;
        failure.name = entry.getKey() + " " + sizeMb + " Mbytes";
        return failure;
    }

    @Nonnull
    public static DsTestFailureUi createOrrucForLongRun(ITeamcityIgnited tcIgnited,
        IStringCompactor compactor,
        @Nonnull MultBuildRunCtx suite,
        TestCompactedMult occurrence,
        @Nullable String failRateBranch,
        @Nullable Map<Integer, Integer> requireParamVal) {
        Integer baseBranchId = compactor.getStringIdIfPresent(normalizeBranch(failRateBranch));
        Integer curBranchId = compactor.getStringIdIfPresent(normalizeBranch(suite.branchName()));

        return new DsTestFailureUi().initFromOccurrence(occurrence, tcIgnited, suite.projectId(), suite.branchName(),
                failRateBranch, baseBranchId, curBranchId, requireParamVal);
    }

    public void findFailureAndAddWarning(String testName, ITestLogCheckResult logCheckRes) {
        DsTestFailureUi failure = testFailures.stream().filter(f -> f.name.contains(testName)).findAny().orElseGet(
            () -> {
                return warnOnly.stream().filter(f -> f.name.contains(testName)).findAny().orElseGet(
                    () -> {
                        DsTestFailureUi f = new DsTestFailureUi();
                        f.name = testName + " (warning)";
                        warnOnly.add(f);

                        return f;
                    });
            });

        failure.warnings.addAll(logCheckRes.getWarns());
    }

    public static String buildWebLinkToBuild(ITeamcityIgnited teamcity, MultBuildRunCtx suite) {
        return teamcity.host() + "viewLog.html?buildId=" + suite.getBuildId();
    }

    @Nonnull private static String buildWebLinkToHist(ITeamcityIgnited teamcity, MultBuildRunCtx suite, String branchName) {
        return buildWebLinkToHist(teamcity, suite.suiteId(), branchName);
    }

    public static String buildWebLinkToHist(ITeamcityIgnited teamcity, String suiteId, String branchName) {
        final String branch = normalizeBranch(branchName);
        return teamcity.host() + "buildConfiguration/" + suiteId  + "?branch=" + UrlUtil.escape(branch);
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        DsSuiteUi status = (DsSuiteUi)o;
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
            runningBuildCount, queuedBuildCount, serverId, suiteId, branchName, criticalFails, latestRuns,
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

    public Collection<? extends ShortTestFailureUi> testFailures() {
        return testFailures;
    }
}
