package org.apache.ignite.ci.analysis;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.ignite.ci.model.result.FullBuildInfo;
import org.apache.ignite.ci.model.result.TestOccurrencesRef;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.model.result.tests.TestOccurrence;
import org.jetbrains.annotations.Nullable;

/**
 * Run configuration execution results loaded from different API URLs
 */
public class FullSuiteRunContext {
    private FullBuildInfo buildInfo;
    private List<ProblemOccurrence> problems;
    @Nullable private List<TestOccurrence> tests;

    private String lastStartedTest;

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
            !problem.isFailedTests()
                && !"SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE".equals(problem.type)
                && !"BuildFailureOnMessage".equals(problem.type));
        //todo what to do with BuildFailureOnMessage, now it is ignored
    }

    public boolean hasAnyBuildProblemExceptTest() {
        return getBuildProblemExceptTest().isPresent();
    }

    private Optional<ProblemOccurrence> getBuildProblemExceptTest() {
        if (problems == null)
            return Optional.empty();
        return problems.stream().filter(p -> !p.isFailedTests()).findAny();
    }

    public boolean hasTimeoutProblem() {
        return problems != null && problems.stream().anyMatch(ProblemOccurrence::isExecutionTimeout);
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

    public void setTests(List<TestOccurrence> tests) {
        this.tests = tests;
    }

    public String getPrintableStatusString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[").append(suiteName()).append("]\t");
        if (hasTimeoutProblem())
            builder.append("TIMEOUT ");
        else {
            Optional<ProblemOccurrence> bpOpt = getBuildProblemExceptTest();
            if (bpOpt.isPresent())
                builder.append(bpOpt.get().type + " ");
        }

        builder.append(failedTests());
        builder.append("\n");
        if (lastStartedTest != null)
            builder.append("\t").append(lastStartedTest).append(" (Last started) \n");

        getFailedTests().map(TestOccurrence::getName).forEach(
            name -> {
                builder.append("\t").append(name).append("\n");
            }
        );
        return builder.toString();
    }

    public Stream<TestOccurrence> getFailedTests() {
        if (tests == null)
            return Stream.empty();
        return tests.stream()
            .filter(TestOccurrence::isFailedTest).filter(TestOccurrence::isNotMutedTest);
    }

    public void setLastStartedTest(String lastStartedTest) {
        this.lastStartedTest = lastStartedTest;
    }
}
