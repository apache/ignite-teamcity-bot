package org.apache.ignite.ci.web.rest.model.current;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullBuildRunContext;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.util.TimeUtil.getDurationPrintable;
import static org.apache.ignite.ci.util.UrlUtil.escape;

/**
 * Represent Suite result
 */
@SuppressWarnings("WeakerAccess") public class SuiteCurrentStatus extends AbstractTestMetrics {
    /** Suite Name */
    public String name;

    /** Suite Run Result (filled if failed) */
    public String result;

    /** Web Href. to suite runs history */
    public String webToHist = "";

    /** Web Href. to suite particular run */
    public String webToBuild = "";

    /** Contact person. */
    public String contactPerson;

    public List<TestFailure> testFailures = new ArrayList<>();

    public void initFromContext(@Nonnull final ITeamcity teamcity,
        @Nonnull final FullBuildRunContext suite,
        @Nullable final Map<String, IgnitePersistentTeamcity.RunStat> runStatMap) {
        name = suite.suiteName();
        result = suite.getResult();
        failedTests = suite.failedTests();
        durationPrintable = getDurationPrintable(suite.getBuildDuration());
        contactPerson = suite.getContactPerson();
        webToHist = buildWebLink(teamcity, suite);
        webToBuild = buildWebLinkToBuild(teamcity, suite);
        suite.getFailedTests().forEach(occurrence -> {
            final TestFailure failure = new TestFailure();
            failure.initFromOccurrence(occurrence, suite.getFullTest(occurrence.id), teamcity, suite);
            failure.initStat(runStatMap);
            testFailures.add(failure);
        });
        if (!isNullOrEmpty(suite.getLastStartedTest())) {
            final TestFailure failure = new TestFailure();
            failure.name = suite.getLastStartedTest() + " (last started)";
            testFailures.add(failure);
        }
    }

    private static String buildWebLinkToBuild(ITeamcity teamcity, FullBuildRunContext suite) {
        return teamcity.host() + "viewLog.html?buildId=" + Integer.toString(suite.getBuildId());
    }

    private static String buildWebLink(ITeamcity teamcity, FullBuildRunContext suite) {
        final String branch = branchForLink(suite.branchName());
        return teamcity.host() + "viewType.html?buildTypeId=" + suite.suiteId()
            + "&branch=" + escape(branch)
            + "&tab=buildTypeStatusDiv";
    }

    public static String branchForLink(String branchName) {
        return branchName == null || "refs/heads/master".equals(branchName) ? "<default>" : branchName;
    }
}
