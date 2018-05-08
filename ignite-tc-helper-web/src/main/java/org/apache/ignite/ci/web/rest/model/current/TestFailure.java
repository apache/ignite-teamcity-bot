package org.apache.ignite.ci.web.rest.model.current;

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
import org.apache.ignite.ci.logs.LogMsgToWarn;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;

import static org.apache.ignite.ci.util.TimeUtil.getDurationPrintable;
import static org.apache.ignite.ci.util.UrlUtil.escape;
import static org.apache.ignite.ci.web.rest.model.current.SuiteCurrentStatus.branchForLink;

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

    /** Current filtered failures count, Usually 0 for get current */
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
                if (webIssueUrl == null) {
                    String issueLinkPrefix = "https://issues.apache.org/jira/browse/";

                    int prefixFoundIdx = details.indexOf(issueLinkPrefix);
                    if (prefixFoundIdx >= 0)
                        initIssueLink(issueLinkPrefix, details, prefixFoundIdx);
                }
                if (webIssueUrl == null) {
                    String issueLinkPrefix = "http://issues.apache.org/jira/browse/";
                    int prefixFoundIdx = details.indexOf(issueLinkPrefix);
                    if (prefixFoundIdx >= 0)
                        initIssueLink(issueLinkPrefix, details, prefixFoundIdx);
                }

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

    private void initIssueLink(String prefix, String txt, int idx) {
        String issueMention = txt.substring(idx - prefix.length());
        String issueIdStart = issueMention.substring(prefix.length());
        Matcher m = Pattern.compile("IGNITE-[0-9]*").matcher(issueIdStart);
        if (m.find()) {
            String issueId = m.group(0);
            webIssueText = issueId;
            webIssueUrl = prefix + issueId;
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

            latestRuns = stat.getLatestRunResults();
        }

        if(!curBranchNormalized.equals(failRateNormalizedBranch)) {
            TestInBranch testInBranchS = new TestInBranch(name, curBranchNormalized);

            RunStat apply = runStatSupplier.apply(testInBranchS);

            latestRuns = apply != null ? apply.getLatestRunResults() : null;
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
