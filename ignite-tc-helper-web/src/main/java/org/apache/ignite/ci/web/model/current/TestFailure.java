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
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.ITestFailureOccurrences;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.issue.EventTemplates;
import org.apache.ignite.ci.issue.IssueRef;
import org.apache.ignite.ci.logs.LogMsgToWarn;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;

import static org.apache.ignite.ci.util.TimeUtil.getDurationPrintable;
import static org.apache.ignite.ci.util.UrlUtil.escape;
import static org.apache.ignite.ci.web.model.current.SuiteCurrentStatus.branchForLink;

/**
 * UI model for test failure, probably merged with its history
 */
@SuppressWarnings("WeakerAccess") public class TestFailure extends FailureSummary {
    /** Test full Name */
    public String name;

    /** suite (in code) short name */
    @Nullable public String suiteName;

    /** test short name with class and method */
    @Nullable public String testName;

    /** Current filtered failures count, Usually 1 for get current */
    public Integer curFailures;

    /** Latest runs, 0,1,2 values for each run. */
    @Nullable public List<Integer> latestRuns;

    /** Failure summary in tracked branch according to all runs history. */
    @Nonnull public FailureSummary failsAllHist = new FailureSummary();

    /** Link to test history */
    @Nullable public String webUrl;

    /** Link to mentioned issue (if any) */
    @Nullable public String webIssueUrl;

    /** Issue text (if any) */
    @Nullable public String webIssueText;

    /** Has some open investigations. */
    public boolean investigated;

    @Nullable public String durationPrintable;

    public List<String> warnings = new ArrayList<>();

    @Nullable public IssueRef problemRef;

    @Nullable public String flakyComments;

    /**
     * @param failure
     * @param testFullOpt all related full test ocurrences
     * @param teamcity
     * @param projectId
     * @param branchName
     */
    public void initFromOccurrence(@Nonnull final ITestFailureOccurrences failure,
        @Nonnull final Stream<TestOccurrenceFull> testFullOpt,
        @Nonnull final ITeamcity teamcity,
        @Nullable final String projectId,
        @Nullable final String branchName) {
        name = failure.getName();
        investigated = failure.isInvestigated();
        curFailures = failure.failuresCount();
        durationPrintable = getDurationPrintable(failure.getAvgDurationMs());

        String[] split = Strings.nullToEmpty(name).split("\\:");
        if (split.length >= 2) {
            String suiteShort = split[0].trim();
            String[] suiteComps = suiteShort.split("\\.");
            if (suiteComps.length > 1)
                suiteName = suiteComps[suiteComps.length - 1];

            String testShort = split[1].trim();
            String[] testComps = testShort.split("\\.");
            if (testComps.length > 2)
                testName = testComps[testComps.length - 2] + "." + testComps[testComps.length - 1];
        }

        testFullOpt.forEach(full -> {
            String details = full.details;

            if (details != null) {
                if (webIssueUrl == null)
                    checkAndFillByPrefix(details, "https://issues.apache.org/jira/browse/");

                if (webIssueUrl == null)
                    checkAndFillByPrefix(details, "http://issues.apache.org/jira/browse/");

                for (String s : details.split("\n")) {
                    if (LogMsgToWarn.needWarn(s))
                        warnings.add(s);
                }
            }
            if (webUrl == null)
                if (full.test != null && full.test.id != null)
                    webUrl = buildWebLink(teamcity, full.test.id, projectId, branchName);
        });

    }

    /**
     * @param details Details full text with error.
     * @param issueLinkPrefix Issue link prefix.
     */
    public void checkAndFillByPrefix(String details, String issueLinkPrefix) {
        int prefixFoundIdx = details.indexOf(issueLinkPrefix);

        if (prefixFoundIdx < 0)
            return;

        String issueMention = details.substring(prefixFoundIdx);

        if (issueMention.length() < issueLinkPrefix.length())
            return;

        String issueIdStart = issueMention.substring(issueLinkPrefix.length());

        Matcher m = Pattern.compile("IGNITE-[0-9]*").matcher(issueIdStart);

        if (m.find()) {
            String issueId = m.group(0);

            webIssueText = issueId;
            webIssueUrl = issueLinkPrefix + issueId;
        }
    }

    private static String buildWebLink(ITeamcity teamcity, Long id,
        @Nullable String projectId, @Nullable String branchName) {
        if (projectId == null)
            return null;
        final String branch = branchForLink(branchName);
        return teamcity.host() + "project.html"
            + "?projectId=" + projectId
            + "&testNameId=" + id
            + "&branch=" + escape(branch)
            + "&tab=testDetails";
    }

    public void initStat(@Nullable final Function<TestInBranch, RunStat> runStatSupplier,
        String failRateNormalizedBranch,
        String curBranchNormalized) {
        if (runStatSupplier == null)
            return;

        TestInBranch testInBranch = new TestInBranch(name, failRateNormalizedBranch);

        final RunStat stat = runStatSupplier.apply(testInBranch);

        if (stat != null) {
            failures = stat.getFailuresCount();
            runs = stat.getRunsCount();
            failureRate = stat.getFailPercentPrintable();

            failsAllHist.failures = stat.getFailuresAllHist();
            failsAllHist.runs = stat.getRunsAllHist();
            failsAllHist.failureRate = stat.getFailPercentAllHistPrintable();

        }

        RunStat latestRunsSrcInBranch = null;

        if (!curBranchNormalized.equals(failRateNormalizedBranch)) {
            TestInBranch testInBranchS = new TestInBranch(name, curBranchNormalized);

            latestRunsSrcInBranch = runStatSupplier.apply(testInBranchS);
        } else
            latestRunsSrcInBranch = stat;

        latestRuns = latestRunsSrcInBranch != null ? latestRunsSrcInBranch.getLatestRunResults() : null;

        if (latestRunsSrcInBranch != null) {
            RunStat.TestId testId = latestRunsSrcInBranch.detectTemplate(EventTemplates.newFailure);

            if (testId != null)
                problemRef = new IssueRef("New Failure");

            RunStat.TestId recentContributedTestId = latestRunsSrcInBranch.detectTemplate(EventTemplates.newContributedTestFailure);

            if (recentContributedTestId != null)
                problemRef = new IssueRef("Recently contributed test failure");

            flakyComments = latestRunsSrcInBranch.getFlakyComments();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TestFailure failure = (TestFailure)o;
        return investigated == failure.investigated &&
            Objects.equal(name, failure.name) &&
            Objects.equal(suiteName, failure.suiteName) &&
            Objects.equal(testName, failure.testName) &&
            Objects.equal(curFailures, failure.curFailures) &&
            Objects.equal(failures, failure.failures) &&
            Objects.equal(runs, failure.runs) &&
            Objects.equal(failureRate, failure.failureRate) &&
            Objects.equal(failsAllHist, failure.failsAllHist) &&
            Objects.equal(webUrl, failure.webUrl) &&
            Objects.equal(webIssueUrl, failure.webIssueUrl) &&
            Objects.equal(webIssueText, failure.webIssueText) &&
            Objects.equal(durationPrintable, failure.durationPrintable)&&
            Objects.equal(warnings, failure.warnings);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(name, suiteName, testName, curFailures, failures, runs, failureRate, failsAllHist,
            webUrl, webIssueUrl, webIssueText, investigated, durationPrintable, warnings);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return "\t" +  name + "\n";
    }
}
