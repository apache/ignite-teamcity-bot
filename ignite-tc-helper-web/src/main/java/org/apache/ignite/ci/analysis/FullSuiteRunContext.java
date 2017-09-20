package org.apache.ignite.ci.analysis;

import java.util.List;
import org.apache.ignite.ci.model.result.FullBuildInfo;
import org.apache.ignite.ci.model.result.TestOccurrencesRef;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrence;

/**
 * Run configuration execution results loaded from different API URLs
 */
public class FullSuiteRunContext {
    private FullBuildInfo buildInfo;
    private List<ProblemOccurrence> problems;

    public FullSuiteRunContext(FullBuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    public void setProblems(List<ProblemOccurrence> problems) {
        this.problems = problems;
    }

    public String suiteName() {
        return buildInfo.suiteName();
    }

    public String suiteId() {
        return buildInfo.getId();
    }

    public boolean hasNontestBuildProblem() {
        return problems != null && problems.stream().anyMatch(problem ->
            !"TC_FAILED_TESTS".equals(problem.type)
                && !"SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE".equals(problem.type)
                && !"BuildFailureOnMessage".equals(problem.type));

        //todo what to do with BuildFailureOnMessage, now it is ignored
    }


    public boolean hasTimeoutProblem() {
        return problems != null && problems.stream().anyMatch(problem ->
            !"TC_EXECUTION_TIMEOUT".equals(problem.type));
    }


    public int failedTests() {
        final TestOccurrencesRef testOccurrences = buildInfo.testOccurrences;

        if (testOccurrences == null)
            return 0;
        final Integer failed = testOccurrences.failed;

        return failed == null ? 0 : failed;
    }

    public int mutedTests() {
        TestOccurrencesRef testOccurrences = buildInfo.testOccurrences;
        if (testOccurrences == null)
            return 0;
        final Integer muted = testOccurrences.muted;

        return muted == null ? 0 : muted;
    }

    public int totalTests() {
        final TestOccurrencesRef testOccurrences = buildInfo.testOccurrences;

        if (testOccurrences == null)
            return 0;
        final Integer cnt = testOccurrences.count;

        return cnt == null ? 0 : cnt;
    }
}
