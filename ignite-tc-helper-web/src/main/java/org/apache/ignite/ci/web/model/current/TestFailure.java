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
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.analysis.IMultTestOccurrence;
import org.apache.ignite.ci.issue.EventTemplates;
import org.apache.ignite.ci.issue.ProblemRef;
import org.apache.ignite.tcignited.buildlog.LogMsgToWarn;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.ci.web.model.hist.FailureSummary;
import org.apache.ignite.ci.web.model.hist.TestHistory;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.tcignited.history.RunHistSync.normalizeBranch;
import static org.apache.ignite.tcbot.common.util.TimeUtil.millisToDurationPrintable;
import static org.apache.ignite.ci.util.UrlUtil.escape;

/**
 * UI model for test failure, probably merged with its history
 */
@SuppressWarnings({"WeakerAccess", "PublicField"}) public class TestFailure {
    /** Test full Name */
    public String name;

    /** suite (in code) short name */
    @Nullable public String suiteName;

    /** test short name with class and method */
    @Nullable public String testName;

    /**
     * Current filtered failures count, Usually 1 for get current (latest), may indicate several failures for history
     * (merged recent runs).
     */
    public Integer curFailures;

    /** Link to test history for current branch. */
    @Nullable public String webUrl;

    /** Link to mentioned issue (if any) */
    @Nullable public String webIssueUrl;

    /** Issue text (if any) */
    @Nullable public String webIssueText;

    /** Has some open investigations. */
    public boolean investigated;

    @Nullable public String durationPrintable;

    public List<String> warnings = new ArrayList<>();

    @Nullable public ProblemRef problemRef;

    /** History cur branch. If it is absent, history is to be taken from histBaseBranch. */
    @Nullable public TestHistory histCurBranch;

    /**
     * This history is created only for PR/Branch failures, it contains data from the base branch (e.g. master).
     */
    @NotNull public TestHistory histBaseBranch = new TestHistory();

    /** Link to test history for current branch. */
    @Nullable public String webUrlBaseBranch;

    /** Blocker comment: indicates test seems to be introduced failure. */
    @Nullable public String blockerComment;

    /**
     * @param failure test ocurrence (probably multiple)
     * @param tcIgn Teamcity.
     * @param projectId project ID.
     * @param branchName
     * @param baseBranchName base branch name (e.g. master).
     */
    public void initFromOccurrence(@Nonnull final IMultTestOccurrence failure,
        @Nonnull final ITeamcityIgnited tcIgn,
        @Nullable final String projectId,
        @Nullable final String branchName,
        @Nullable final String baseBranchName) {
        name = failure.getName();
        investigated = failure.isInvestigated();
        curFailures = failure.failuresCount();
        durationPrintable = millisToDurationPrintable(failure.getAvgDurationMs());

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

        failure.getOccurrences().forEach(full -> {
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
                    webUrl = buildWebLink(tcIgn, full.test.id, projectId, branchName);

            if (webUrlBaseBranch == null)
                if (full.test != null && full.test.id != null)
                    webUrlBaseBranch = buildWebLink(tcIgn, full.test.id, projectId, baseBranchName);
        });

        final IRunHistory stat = tcIgn.getTestRunHist(name, normalizeBranch(baseBranchName));

        blockerComment = failure.getPossibleBlockerComment(stat);
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

    private static String buildWebLink(ITeamcityIgnited tcIgn, String id,
        @Nullable String projectId, @Nullable String branchName) {
        if (projectId == null)
            return null;

        final String branch = normalizeBranch(branchName);

        return tcIgn.host() + "project.html"
            + "?projectId=" + projectId
            + "&testNameId=" + id
            + "&branch=" + escape(branch)
            + "&tab=testDetails";
    }

    /**
     * @param tcIgnited TC service as Run stat supplier.
     * @param failRateNormalizedBranch Base branch: Fail rate and flakyness detection normalized branch.
     * @param curBranchNormalized Cur branch normalized.
     */
    public void initStat(ITeamcityIgnited tcIgnited,
        String failRateNormalizedBranch,
        String curBranchNormalized) {

        final IRunHistory stat = tcIgnited.getTestRunHist(name, failRateNormalizedBranch);

        histBaseBranch.init(stat);

        IRunHistory statForProblemsDetection = null;

        if (!curBranchNormalized.equals(failRateNormalizedBranch)) {
            statForProblemsDetection = tcIgnited.getTestRunHist(name, curBranchNormalized);

            if (statForProblemsDetection != null) {
                histCurBranch = new TestHistory();

                histCurBranch.init(statForProblemsDetection);
            }
        }
        else
            statForProblemsDetection = stat;

        if (statForProblemsDetection != null) {
            if (statForProblemsDetection.detectTemplate(EventTemplates.newFailure) != null)
                problemRef = new ProblemRef("New Failure");

            if (statForProblemsDetection.detectTemplate(EventTemplates.newContributedTestFailure) != null)
                problemRef = new ProblemRef("Recently contributed test failure");
        }
    }

    /**
     * @return {@code True} if this failure is appeared in the current branch.
     */
    @Deprecated
    public boolean isNewFailedTest() {
        if (!Strings.isNullOrEmpty(webIssueUrl))
            return false;

        if (histBaseBranch.latestRuns == null)
            return true;

        FailureSummary recent = histBaseBranch.recent;

        boolean lowFailureRate = recent != null && recent.failureRate != null &&
            Float.valueOf(recent.failureRate.replace(',', '.')) < 4.;

        //System.out.println(name + " " + recent.failureRate);

        return lowFailureRate && histBaseBranch.flakyComments == null;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TestFailure failure = (TestFailure)o;
        return investigated == failure.investigated &&
            Objects.equals(name, failure.name) &&
            Objects.equals(suiteName, failure.suiteName) &&
            Objects.equals(testName, failure.testName) &&
            Objects.equals(curFailures, failure.curFailures) &&
            Objects.equals(webUrl, failure.webUrl) &&
            Objects.equals(webIssueUrl, failure.webIssueUrl) &&
            Objects.equals(webIssueText, failure.webIssueText) &&
            Objects.equals(durationPrintable, failure.durationPrintable) &&
            Objects.equals(warnings, failure.warnings) &&
            Objects.equals(problemRef, failure.problemRef) &&
            Objects.equals(histCurBranch, failure.histCurBranch) &&
            Objects.equals(histBaseBranch, failure.histBaseBranch) &&
            Objects.equals(webUrlBaseBranch, failure.webUrlBaseBranch) &&
            Objects.equals(blockerComment, failure.blockerComment);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(name, suiteName, testName, curFailures, webUrl, webIssueUrl, webIssueText,
            investigated, durationPrintable, warnings, problemRef, histCurBranch, histBaseBranch,
            webUrlBaseBranch, blockerComment);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return "\t" + name + "\n";
    }

    /**
     *
     */
    public boolean isPossibleBlocker() {
        return !Strings.isNullOrEmpty(blockerComment);
    }
}
