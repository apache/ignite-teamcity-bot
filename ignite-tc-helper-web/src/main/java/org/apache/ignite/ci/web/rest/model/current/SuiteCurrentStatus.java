package org.apache.ignite.ci.web.rest.model.current;

import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.ci.analysis.FullBuildRunContext;

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

    public List<TestFailure> testFailures = new ArrayList<>();

    public void initFromContext(FullBuildRunContext suite) {
        name = suite.suiteName();
        result = suite.getResult();
        failedTests = suite.failedTests();
        suite.getFailedTests().forEach(occurrence -> {
            final TestFailure failure = new TestFailure();
            final String name = occurrence.getName();
            failure.name = name;
            testFailures.add(failure);
        });
    }
}
