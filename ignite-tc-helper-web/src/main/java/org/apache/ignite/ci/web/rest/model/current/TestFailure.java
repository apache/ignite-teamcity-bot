package org.apache.ignite.ci.web.rest.model.current;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullBuildRunContext;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;

import static org.apache.ignite.ci.util.UrlUtil.escape;
import static org.apache.ignite.ci.web.rest.model.current.SuiteCurrentStatus.branchForLink;

/**
 * UI model for test failure
 */
@SuppressWarnings("WeakerAccess") public class TestFailure {
    /** test Name */
    public String name;

    /** Registered number of failures from TC helper DB */
    public Integer failures;

    /** Registered number of runs from TC helper DB */
    public Integer runs;

    /** Link to test history */
    @Nullable public String webUrl;

    /** Link to mentioned issue (if any) */
    @Nullable public String webIssueUrl;

    /** Issue text (if any) */
    @Nullable public String webIssueText;

    public void initFromOccurrence(@Nonnull final TestOccurrence failure,
        @Nonnull final Optional<TestOccurrenceFull> testFullOpt,
        @Nonnull final ITeamcity teamcity,
        @Nonnull final FullBuildRunContext suite) {
        name = failure.getName();
        testFullOpt.ifPresent(full -> {
            String details = full.details;
            if (details != null) {
                if (webIssueUrl == null) {
                    String issueLinkPrefix = "https://issues.apache.org/jira/browse/";

                    int prefixFoundIdx = details.indexOf(issueLinkPrefix);
                    if (prefixFoundIdx >= 0) {
                        //todo indicate failure by issue
                        initIssueLink(issueLinkPrefix, details, prefixFoundIdx);
                    }
                }
                if (webIssueUrl == null) {
                    String issueLinkPrefix = "http://issues.apache.org/jira/browse/";
                    int prefixFoundIdx = details.indexOf(issueLinkPrefix);
                    if (prefixFoundIdx >= 0) {
                        //todo indicate failure by issue
                        initIssueLink(issueLinkPrefix, details, prefixFoundIdx);
                    }
                }
            }
            if (full.test != null && full.test.id != null) {
                webUrl = buildWebLink(teamcity, suite, full.test.id);
            }
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

    private static String buildWebLink(ITeamcity teamcity, FullBuildRunContext suite, Long id) {
        if (suite.projectId() == null)
            return null;
        final String branch = branchForLink(suite.branchName());
        return teamcity.host() + "project.html"
            + "?projectId=" + suite.projectId()
            + "&testNameId=" + id
            + "&branch=" + escape(branch)
            + "&tab=testDetails";
    }

    public void initStat(@Nullable final Map<String, RunStat> runStatMap) {
        final RunStat stat = runStatMap == null ? null : runStatMap.get(name);
        if (stat != null) {
            failures = stat.failures;
            runs = stat.runs;
        }
    }
}
