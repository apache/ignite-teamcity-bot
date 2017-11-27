package org.apache.ignite.ci.analysis;

import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.util.TimeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Run configuration execution results loaded from different API URLs.
 * Includes tests and problem occurrences; if logs processing is done also contains last started test
 */
public class FullBuildRunContext {
    @Nonnull private final Build buildInfo;
    private List<ProblemOccurrence> problems;
    @Nullable private List<TestOccurrence> tests;

    /** Last started test. Optionally filled from log post processor */
    private String lastStartedTest;

    /** Used for associating build info with contact person */
    private String contactPerson;

    @Nullable private Statistics stat;

    /** Mapping for building test occurrence reference to test full results */
    private Map<String, TestOccurrenceFull> testFullMap = new HashMap<>();

    /** Thread dump short file name */
    @Nullable private Integer threadDumpFileIdx;

    public FullBuildRunContext(@Nonnull final Build buildInfo) {
        this.buildInfo = buildInfo;
    }

    public void setProblems(List<ProblemOccurrence> problems) {
        this.problems = problems;
    }

    public String suiteId() {
        return buildInfo.suiteId();
    }

    public String suiteName() {
        return buildInfo.suiteName();
    }

    public boolean hasNontestBuildProblem() {
        return problems != null && problems.stream().anyMatch(problem ->
            !problem.isFailedTests()
                && !problem.isShaphotDepProblem()
                && !ProblemOccurrence.BUILD_FAILURE_ON_MESSAGE.equals(problem.type));
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

    public boolean hasJvmCrashProblem() {
        return problems != null && problems.stream().anyMatch(ProblemOccurrence::isJvmCrash);
    }

    public boolean hasOomeProblem() {
        return problems != null && problems.stream().anyMatch(ProblemOccurrence::isOome);
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
        builder.append(getResult());
        builder.append(" ");
        builder.append(failedTests());

        if (stat != null) {
            final Long durationMs = stat.getBuildDuration();
            if (durationMs != null)
                builder.append(" ").append(TimeUtil.getDurationPrintable(durationMs));
        }

        if (contactPerson != null)
            builder.append("\t").append(contactPerson);

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

    /**
     * Suite Run Result (filled if failed)
     *
     * @return printable result
     */
    public String getResult() {
        String result;
        if (hasTimeoutProblem())
            result = ("TIMEOUT ");
        else if (hasJvmCrashProblem())
            result = ("JVM CRASH ");
        else if (hasOomeProblem())
            result = ("Out Of Memory Error ");
        else {
            Optional<ProblemOccurrence> bpOpt = getBuildProblemExceptTestOrSnapshot();
            result = bpOpt.map(occurrence -> occurrence.type)
                .orElse("");
        }
        return result;
    }

    public Stream<TestOccurrence> getFailedTests() {
        if (tests == null)
            return Stream.empty();
        return tests.stream()
            .filter(TestOccurrence::isFailedTest).filter(TestOccurrence::isNotMutedOrIgnoredTest);
    }

    public void setLastStartedTest(String lastStartedTest) {
        this.lastStartedTest = lastStartedTest;
    }

    public int getBuildId() {
        return buildInfo.getId();
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    boolean isFailed() {
        return failedTests() != 0 || hasAnyBuildProblemExceptTestOrSnapshot();
    }

    public String branchName() {
        return buildInfo.branchName;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getContactPersonOrEmpty() {
        return Strings.nullToEmpty(contactPerson);
    }

    public String getLastStartedTest() {
        return lastStartedTest;
    }

    public void setStat(Statistics stat) {
        this.stat = stat;
    }

    @Nullable
    public Long getBuildDuration() {
        return stat == null ? null : stat.getBuildDuration();
    }

    @Nullable
    public Long getSourceUpdateDuration() {
        return stat == null ? null : stat.getSourceUpdateDuration();
    }

    public void addTestInBuildToTestFull(String testInBuildId, TestOccurrenceFull testOccurrenceFull) {
        testFullMap.put(testInBuildId, testOccurrenceFull);
    }

    public Optional<TestOccurrenceFull> getFullTest(String id) {
        return Optional.ofNullable(testFullMap.get(id));
    }

    @Nullable
    public String projectId() {
        final BuildType type = buildInfo.getBuildType();

        if (type == null)
            return null;

        return type.getProjectId();
    }


    public void setThreadDumpFileIdx(Integer threadDumpFileIdx) {
        this.threadDumpFileIdx = threadDumpFileIdx;
    }

    @Nullable public Integer getThreadDumpFileIdx() {
        return threadDumpFileIdx;
    }
}
