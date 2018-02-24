package org.apache.ignite.ci.analysis;

import com.google.common.base.Strings;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.util.CollectionUtil;
import org.apache.ignite.ci.util.FutureUtil;
import org.apache.ignite.ci.util.TimeUtil;
import org.jetbrains.annotations.Nullable;

import static java.util.stream.Stream.concat;

/**
 * Run configuration execution results loaded from different API URLs.
 * Includes tests and problem occurrences; if logs processing is done also contains last started test
 */
public class MultBuildRunCtx implements ISuiteResults {
    @Nonnull private final Build firstBuildInfo;

    private List<SingleBuildRunCtx> builds = new CopyOnWriteArrayList<>();

    @Deprecated
    private List<ProblemOccurrence> problems = new CopyOnWriteArrayList<>();


    /** Tests: Map from full test name to multiple test occurrence. */
    private final Map<String, MultTestFailureOccurrences> tests = new ConcurrentSkipListMap<>();

    /**
     * Mapping for building test occurrence reference to test full results:
     * Map from "Occurrence in build id" test detailed info.
     * Note: only failed tests are loaded here
     */
    private Map<String, CompletableFuture<TestOccurrenceFull>> testFullMap = new HashMap<>();

    /** Used for associating build info with contact person */
    @Nullable private String contactPerson;

    @Nullable private Statistics stat;

    public void addBuild(SingleBuildRunCtx ctx) {
        builds.add(ctx);
    }

    /** Thread dump short file name */
    @Nullable private Integer threadDumpFileIdx;

    /** Currently running builds */
    @Nullable private CompletableFuture<Long> runningBuildCount;

    /** Currently scheduled builds */
    @Nullable private CompletableFuture<Long> queuedBuildCount;

    public MultBuildRunCtx(@Nonnull final Build buildInfo) {
        this.firstBuildInfo = buildInfo;
    }

    public Stream<String> getCriticalFailLastStartedTest() {
        return builds.stream().map(SingleBuildRunCtx::getCriticalFailLastStartedTest).filter(Objects::nonNull);
    }

    @Deprecated
    public void addProblems(List<ProblemOccurrence> problems) {
        this.problems.addAll(problems);
    }

    public String suiteId() {
        return firstBuildInfo.suiteId();
    }

    public String suiteName() {
        return firstBuildInfo.suiteName();
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
        return problems.stream().filter(p -> !p.isFailedTests() && !p.isShaphotDepProblem()).findAny();
    }

    public boolean hasTimeoutProblem() {
        return getExecutionTimeoutCount() > 0;
    }

    public long getExecutionTimeoutCount() {
        return problems.stream().filter(Objects::nonNull).filter(ProblemOccurrence::isExecutionTimeout).count();
    }

    public boolean hasJvmCrashProblem() {
        return getJvmCrashProblemCount() > 0;
    }

    public long getJvmCrashProblemCount() {
        return problems.stream().filter(Objects::nonNull).filter(ProblemOccurrence::isJvmCrash).count();
    }

    public boolean hasOomeProblem() {
        return getOomeProblemCount() > 0;
    }

    public long getOomeProblemCount() {
        return problems.stream().filter(ProblemOccurrence::isOome).count();
    }

    public int failedTests() {
        return (int)tests.values().stream().mapToInt(MultTestFailureOccurrences::failuresCount)
            .filter(cnt -> cnt > 0).count();
    }

    public int mutedTests() {
        TestOccurrencesRef testOccurrences = firstBuildInfo.testOccurrences;
        if (testOccurrences == null)
            return 0;
        final Integer muted = testOccurrences.muted;

        return muted == null ? 0 : muted;
    }

    public int totalTests() {
        final TestOccurrencesRef testOccurrences = firstBuildInfo.testOccurrences;

        if (testOccurrences == null)
            return 0;

        final Integer cnt = testOccurrences.count;

        return cnt == null ? 0 : cnt;
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
        getCriticalFailLastStartedTest().forEach(lastStartedTest ->
            builder.append("\t").append(lastStartedTest).append(" (Last started) \n"));

        getFailedTests().map(ITestFailureOccurrences::getName).forEach(
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
        long execToCnt = getExecutionTimeoutCount();

        StringBuilder res = new StringBuilder();
        if (execToCnt > 0) {
            if (res.length() > 0)
                res.append(", ");

            res.append("TIMEOUT ").append(execToCnt > 1 ? "[" + execToCnt + "]" : "");
        }


        long jvmCrashProblemCnt = getJvmCrashProblemCount();
        if (jvmCrashProblemCnt > 0) {
            if (res.length() > 0)
                res.append(", ");

            res.append("JVM CRASH ").append(jvmCrashProblemCnt > 1 ? "[" + jvmCrashProblemCnt + "]" : "");
        }

        long oomeProblemCnt = getOomeProblemCount();
        if (oomeProblemCnt > 0) {
            if (res.length() > 0)
                res.append(", ");

            res.append("Out Of Memory Error ").append(oomeProblemCnt > 1 ? "[" + oomeProblemCnt + "]" : "");
        }

        {
            Stream<ProblemOccurrence> stream =
                problems.stream().filter(p ->
                    !p.isFailedTests()
                        && !p.isShaphotDepProblem()
                        && !p.isExecutionTimeout()
                        && !p.isJvmCrash()
                        && !p.isOome());
            Optional<ProblemOccurrence> bpOpt = stream.findAny();
            if(bpOpt.isPresent()) {
                if (res.length() > 0)
                    res.append(", ");

                res.append(bpOpt.get().type).append(" ") ;
            }
        }
        return res.toString();
    }

    public Stream<? extends ITestFailureOccurrences> getTopLongRunning() {
        Stream<MultTestFailureOccurrences> stream = tests.values().stream();
        Comparator<MultTestFailureOccurrences> comparing = Comparator.comparing(MultTestFailureOccurrences::getAvgDurationMs);
        return CollectionUtil.top(stream, 3, comparing).stream();
    }

    public Stream<? extends ITestFailureOccurrences> getFailedTests() {
        return tests.values().stream().filter(MultTestFailureOccurrences::hasFailedButNotMuted);
    }

    public void addTests(Iterable<TestOccurrence> tests) {
        for (TestOccurrence next : tests) {
            this.tests.computeIfAbsent(next.name,
                k -> new MultTestFailureOccurrences())
                .add(next);
        }
    }

    public int getBuildId() {
        return firstBuildInfo.getId();
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    boolean isFailed() {
        return failedTests() != 0 || hasAnyBuildProblemExceptTestOrSnapshot();
    }

    public String branchName() {
        return firstBuildInfo.branchName;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getContactPersonOrEmpty() {
        return Strings.nullToEmpty(contactPerson);
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

    /**
     * @param testOccurrenceInBuildId, something like: 'id:15666,build:(id:1093907)'
     * @param fullFut
     */
    public void addTestInBuildToTestFull(String testOccurrenceInBuildId,
        CompletableFuture<TestOccurrenceFull> fullFut) {
        testFullMap.put(testOccurrenceInBuildId, fullFut);
    }

    private Optional<TestOccurrenceFull> getFullTest(String testOccurrenceInBuildId) {
        return Optional.ofNullable(testFullMap.get(testOccurrenceInBuildId))
            .flatMap(fut ->
                Optional.ofNullable(fut.isDone() ?  FutureUtil.getResultSilent(fut) : null));
    }

    /**
     * @return aggregation project ID, such as "Ignite_Tests_20"
     */
    @Nullable public String projectId() {
        final BuildType type = firstBuildInfo.getBuildType();

        if (type == null)
            return null;

        return type.getProjectId();
    }

    @Deprecated
    @Nullable public Integer getThreadDumpFileIdx() {
        return threadDumpFileIdx;
    }

    public void setRunningBuildCount(CompletableFuture<Long> runningBuildCount) {
        this.runningBuildCount = runningBuildCount;
    }

    public void setQueuedBuildCount(CompletableFuture<Long> queuedBuildCount) {
        this.queuedBuildCount = queuedBuildCount;
    }

    public boolean hasScheduledBuildsInfo() {
        return runningBuildCount != null && queuedBuildCount != null;
    }

    public Integer queuedBuildCount() {
        if (queuedBuildCount == null)
            return 0;

        Long val = FutureUtil.getResultSilent(queuedBuildCount);

        return val == null ? 0 : val.intValue();

    }

    public Integer runningBuildCount() {
        if (runningBuildCount == null)
            return 0;

        Long val = FutureUtil.getResultSilent(runningBuildCount);

        return val == null ? 0 : val.intValue();
    }

    public Stream<TestOccurrenceFull> getFullTests(ITestFailureOccurrences occurrence) {
        return occurrence.getOccurrenceIds()
            .map(this::getFullTest)
            .filter(Optional::isPresent)
            .map(Optional::get);
    }

    /**
     * @return Username's stream for users introduced changes in this commit
     */
    public Stream<String> lastChangeUsers() {
        return builds.stream()
            .flatMap(k -> k.getChanges().stream())
            .map(change -> change.username)
            .filter(Objects::nonNull);
    }

    public Stream<? extends Future<?>> getFutures() {
        Stream<CompletableFuture<?>> stream1 = queuedBuildCount != null ? Stream.of(queuedBuildCount) : Stream.empty();
        Stream<CompletableFuture<?>> stream2 = runningBuildCount != null ? Stream.of(runningBuildCount) : Stream.empty();

        Stream<? extends Future<?>> stream3 = testFullMap.values().stream();

        Stream<? extends Future<?>> stream4 = builds.stream().flatMap(SingleBuildRunCtx::getFutures);

        return
            concat(
                concat(stream1, stream2),
                concat(stream4, stream3));
    }
}
