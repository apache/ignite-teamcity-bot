package org.apache.ignite.ci.web.rest.model.current;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullBuildRunContext;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.util.TimeUtil.getDurationPrintable;
import static org.apache.ignite.ci.util.UrlUtil.escape;

/**
 * Represent Suite result
 */
public class SuiteCurrentStatus extends AbstractTestMetrics {
    /** Suite Name */
    public String name;

    /** Suite Run Result (filled if failed) */
    public String result;

    /** Web Href. to suite runs history*/
    public String webToHist = "";

    /** Web Href. to suite particular run */
    public String webToBuild = "";

    /** Contact person. */
    public String contactPerson;

    public List<TestFailure> testFailures = new ArrayList<>();

    public void initFromContext(ITeamcity teamcity, FullBuildRunContext suite,
        Map<String, IgnitePersistentTeamcity.RunStat> runStatMap) {
        name = suite.suiteName();
        result = suite.getResult();
        failedTests = suite.failedTests();
        durationPrintable = getDurationPrintable(suite.getBuildDuration());
        contactPerson = suite.getContactPerson();
        webToHist = buildWebLink(teamcity, suite);
        webToBuild = buildWebLinkToBuild(teamcity, suite);
        suite.getFailedTests().forEach(occurrence -> {
            final TestFailure failure = new TestFailure();
            final String name = occurrence.getName();
            final IgnitePersistentTeamcity.RunStat stat = runStatMap.get(name);
            if (stat != null) {
                failure.failures = stat.failures;
                failure.runs = stat.runs;
            }
            failure.name = name;
            testFailures.add(failure);
        });
        if(!isNullOrEmpty(suite.getLastStartedTest())) {
            final TestFailure e = new TestFailure();
            e.name = suite.getLastStartedTest() + " (last started)";
            testFailures.add(e);
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
        return "refs/heads/master".equals(branchName) ? "<default>" : branchName;
    }
}
