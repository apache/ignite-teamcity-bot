package org.apache.ignite.ci.web.rest.model.current;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullBuildRunContext;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.util.UrlUtil.escape;

/**
 * Represent Suite result
 */
public class SuiteCurrentStatus extends AbstractTestMetrics {
    /** Suite Name */
    public String name;

    /** Suite Run Result (filled if failed) */
    public String result;

    /** Web Href. */
    public String web;

    /** Contact person. */
    public String contactPerson;

    public List<TestFailure> testFailures = new ArrayList<>();

    public void initFromContext(ITeamcity teamcity, FullBuildRunContext suite,
        Map<String, IgnitePersistentTeamcity.RunStat> runStatMap) {
        name = suite.suiteName();
        result = suite.getResult();
        failedTests = suite.failedTests();
        contactPerson = suite.getExtendedComment();
        web = teamcity.host() + "viewType.html?buildTypeId=" + suite.suiteId()
            + "&branch=" + escape(suite.branchName())
            + "&tab=buildTypeStatusDiv";
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
}
