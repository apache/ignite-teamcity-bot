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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.common.util.UrlUtil;
import org.apache.ignite.tcbot.engine.chain.TestCompactedMult;
import org.apache.ignite.tcbot.engine.issue.EventTemplates;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.build.ITest;
import org.apache.ignite.tcignited.buildlog.LogMsgToWarn;
import org.apache.ignite.tcignited.history.IRunHistory;

import static org.apache.ignite.tcbot.common.util.TimeUtil.millisToDurationPrintable;
import static org.apache.ignite.tcignited.buildref.BranchEquivalence.normalizeBranch;


/**
 * Detailed status of failures: UI model for test failure, probably merged with its history
 */
@SuppressWarnings({"WeakerAccess", "PublicField"})
public class DsTestFailureUi extends ShortTestFailureUi {
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

    @Nullable public DsProblemRef problemRef;

    /** History cur branch. If it is absent, history is to be taken from histBaseBranch. */
    @Nullable public DsTestHistoryUi histCurBranch;

    /**
     * This history is created only for PR/Branch failures, it contains data from the base branch (e.g. master).
     */
    @Nonnull public DsTestHistoryUi histBaseBranch = new DsTestHistoryUi();

    /** Link to test history for current branch. */
    @Nullable public String webUrlBaseBranch;

    public boolean success = false;

    /**
     * @param failure test ocurrence (probably multiple)
     * @param tcIgn Teamcity.
     * @param projectId project ID.
     * @param branchName current branch name.
     * @param baseBranchName base branch name (e.g. master), without normalization.
     * @param baseBranchId Normalized base branch ID (from compactor).
     * @param curBranchId Current branch id to inin statistics.
     * @param requireParameters Filter suite and test history by parameter value.
     */
    public DsTestFailureUi initFromOccurrence(@Nonnull final TestCompactedMult failure,
        @Nonnull final ITeamcityIgnited tcIgn,
        @Nullable final String projectId,
        @Nullable final String branchName,
        @Nullable final String baseBranchName,
        Integer baseBranchId,
        @Nullable Integer curBranchId,
        @Nullable Map<Integer, Integer> requireParameters) {
        success = !failure.isFailedButNotMuted();

        investigated = failure.isInvestigated();
        curFailures = failure.failuresCount();
        durationPrintable = millisToDurationPrintable(failure.getAvgDurationMs());

        initFrom(failure, tcIgn, baseBranchId);

        failure.getInvocationsStream()
            .map(ITest::getDetailsText)
            .filter(Objects::nonNull)
            .forEach(details -> {
                //todo check integration with JIRA
                if (webIssueUrl == null)
                    checkAndFillByPrefix(details, "https://issues.apache.org/jira/browse/");

                if (webIssueUrl == null)
                    checkAndFillByPrefix(details, "http://issues.apache.org/jira/browse/");

                for (String s : details.split("\n")) {
                    if (LogMsgToWarn.needWarn(s))
                        warnings.add(s);
                }
            });

        failure.getInvocationsStream()
            .map(ITest::getTestId)
            .filter(Objects::nonNull)
            .forEach(testNameId -> {
                if (webUrl == null)
                    webUrl = buildTestWebLink(tcIgn, testNameId, projectId, branchName);

                if (webUrlBaseBranch == null)
                    webUrlBaseBranch = buildTestWebLink(tcIgn, testNameId, projectId, baseBranchName);
            });


        initStat(failure, tcIgn, baseBranchId, curBranchId, requireParameters);

        return this;
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

    public static String buildTestWebLink(ITeamcityIgnited tcIgn, Long testNameId,
                                          @Nullable String projectId, @Nullable String branchName) {
        if (projectId == null || testNameId == null)
            return null;

        final String branch = normalizeBranch(branchName);

        return tcIgn.host()
                + "test/" + testNameId
                + "?currentProjectId=" + projectId
                + "&branch=" + UrlUtil.escape(branch);
    }

    /**
     * @param occurrence
     * @param tcIgnited TC service as Run stat supplier.
     * @param baseBranchId Base branch: Fail rate and flakyness detection normalized branch.
     * @param curBranchNormalized Cur branch normalized.
     * @param requireParameters
     */
    public void initStat(TestCompactedMult occurrence,
        ITeamcityIgnited tcIgnited,
        @Nullable Integer baseBranchId,
        @Nullable Integer curBranchNormalized,
        @Nullable Map<Integer, Integer> requireParameters) {
        final IRunHistory stat = occurrence.history(tcIgnited, baseBranchId, requireParameters);
        histBaseBranch.init(stat);

        IRunHistory statForProblemsDetection;

        if (!Objects.equals(curBranchNormalized, baseBranchId)) {
            statForProblemsDetection = occurrence.history(tcIgnited, curBranchNormalized, requireParameters);

            if (statForProblemsDetection != null) {
                histCurBranch = new DsTestHistoryUi();

                histCurBranch.init(statForProblemsDetection);
            }
        }
        else
            statForProblemsDetection = stat;

        if (statForProblemsDetection != null) {
            if (statForProblemsDetection.detectTemplate(EventTemplates.newFailure) != null)
                problemRef = new DsProblemRef("New Failure");

            if (statForProblemsDetection.detectTemplate(EventTemplates.newContributedTestFailure) != null)
                problemRef = new DsProblemRef("Recently contributed test failure");


            if (statForProblemsDetection.detectTemplate(EventTemplates.alwaysFailure) != null)
                problemRef = new DsProblemRef("Always failed test");

            if (statForProblemsDetection.isFlaky()
                    && statForProblemsDetection.detectTemplate(EventTemplates.newFailureForFlakyTest) != null)
                problemRef = new DsProblemRef("New failure of flaky test");
        }
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DsTestFailureUi failure = (DsTestFailureUi)o;
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


}
