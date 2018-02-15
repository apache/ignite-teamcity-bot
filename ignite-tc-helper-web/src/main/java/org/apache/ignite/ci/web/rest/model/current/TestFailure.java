package org.apache.ignite.ci.web.rest.model.current;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.ITestFailureOccurrences;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;

import static org.apache.ignite.ci.util.UrlUtil.escape;
import static org.apache.ignite.ci.web.rest.model.current.SuiteCurrentStatus.branchForLink;

/**
 * UI model for test failure, probably merged with its history
 */
@SuppressWarnings("WeakerAccess") public class TestFailure {
    /** Test full Name */
    public String name;

    /** Current filtered failures count, Usually 0 for get current */
    public Integer curFailures;

    /** Registered number of failures from TC helper DB */
    @Nullable public Integer failures;

    /** Registered number of runs from TC helper DB */
    @Nullable public Integer runs;

    /** Registered percent of fails from TC helper DB */
    @Nullable public String failureRate;

    /** Link to test history */
    @Nullable public String webUrl;

    /** Link to mentioned issue (if any) */
    @Nullable public String webIssueUrl;

    /** Issue text (if any) */
    @Nullable public String webIssueText;

    /** Has some open investigations. */
    public boolean investigated;

    /**
     * @param failure
     * @param testFullOpt all related full test ocurrences
     * @param teamcity
     * @param suite
     */
    public void initFromOccurrence(@Nonnull final ITestFailureOccurrences failure,
        @Nonnull final Stream<TestOccurrenceFull> testFullOpt,
        @Nonnull final ITeamcity teamcity,
        @Nonnull final MultBuildRunCtx suite) {
        name = failure.getName();
        investigated = failure.isInvestigated();
        curFailures = failure.failuresCount();

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
            }
            if (webUrl == null)
                if (full.test != null && full.test.id != null)
                    webUrl = buildWebLink(teamcity, suite, full.test.id);
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

    private static String buildWebLink(ITeamcity teamcity, MultBuildRunCtx suite, Long id) {
        if (suite.projectId() == null)
            return null;
        final String branch = branchForLink(suite.branchName());
        return teamcity.host() + "project.html"
            + "?projectId=" + suite.projectId()
            + "&testNameId=" + id
            + "&branch=" + escape(branch)
            + "&tab=testDetails";
    }

    public void initStat(@Nullable final Function<String, RunStat> runStatSupplier) {
        final RunStat stat = runStatSupplier == null ? null : runStatSupplier.apply(name);
        if (stat != null) {
            failures = stat.failures;
            runs = stat.runs;
            failureRate = stat.getFailPercentPrintable();
        }
    }
}
