package org.apache.ignite.ci.analysis;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.jetbrains.annotations.Nullable;

/**
 * Run configuration execution results loaded from different API URLs
 */
public class FullBuildRunContext {
    private Build buildInfo;
    private List<ProblemOccurrence> problems;
    @Nullable private List<TestOccurrence> tests;

    private String lastStartedTest;

    public FullBuildRunContext(Build buildInfo) {
        this.buildInfo = buildInfo;
    }

    public void setProblems(List<ProblemOccurrence> problems) {
        this.problems = problems;
    }

    public String suiteName() {
        return buildInfo.suiteName();
    }


    public boolean hasNontestBuildProblem() {
        return problems != null && problems.stream().anyMatch(problem ->
            !problem.isFailedTests()
                && !problem.isShaphotDepProblem()
                && !"BuildFailureOnMessage".equals(problem.type));
        //todo what to do with BuildFailureOnMessage, now it is ignored
    }

    public boolean hasAnyBuildProblemExceptTestOrSnapshot() {
        return getBuildProblemExceptTestOrSnapshot().isPresent();
    }

    private Optional<ProblemOccurrence> getBuildProblemExceptTestOrSnapshot() {
        if (problems == null)
            return Optional.empty();
        return problems.stream().filter(p -> !p.isFailedTests() && !p.isShaphotDepProblem()).findAny();
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
        builder.append("\t[").append(suiteName()).append("]\t");
        if (hasTimeoutProblem())
            builder.append("TIMEOUT ");
        else {
            Optional<ProblemOccurrence> bpOpt = getBuildProblemExceptTestOrSnapshot();
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
