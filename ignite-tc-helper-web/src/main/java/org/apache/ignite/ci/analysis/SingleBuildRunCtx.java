package org.apache.ignite.ci.analysis;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.util.FutureUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Дмитрий on 17.02.2018.
 */
public class SingleBuildRunCtx implements ISuiteResults {
    private Build build;
    private CompletableFuture<LogCheckResult> logCheckResultsFut;

    /** Build problems occurred during single build run. */
    @Nullable private List<ProblemOccurrence> problems;

    private List<Change> changes = new ArrayList<>();

    public SingleBuildRunCtx(Build build) {
        this.build = build;
    }

    public Integer buildId() {
        return build.getId();
    }

    public boolean hasTimeoutProblem() {
        return getExecutionTimeoutCount() > 0;
    }

    private long getExecutionTimeoutCount() {
        return getProblemsStream().filter(ProblemOccurrence::isExecutionTimeout).count();
    }

    private Stream<ProblemOccurrence> getProblemsStream() {
        if (problems == null)
            return Stream.empty();

        return problems.stream().filter(Objects::nonNull);
    }

    @Override public boolean hasJvmCrashProblem() {
        return getProblemsStream().anyMatch(ProblemOccurrence::isJvmCrash);
    }

    @Override public boolean hasOomeProblem() {
        return getProblemsStream().anyMatch(ProblemOccurrence::isOome);
    }

    @Override public boolean hasExitCodeProblem() {
        return getProblemsStream().anyMatch(ProblemOccurrence::isExitCode);
    }

    @Override public String suiteId() {
        return build.suiteId();
    }

    public void setLogCheckResultsFut(CompletableFuture<LogCheckResult> logCheckResultsFut) {
        this.logCheckResultsFut = logCheckResultsFut;
    }

    @Nullable
    public String getCriticalFailLastStartedTest() {
        LogCheckResult logCheckResult = getLogCheckIfFinished();
        if (logCheckResult == null)
            return null;

        return logCheckResult.getLastStartedTest();
    }

    @Nullable
    public Integer getBuildIdIfHasThreadDump() {
        LogCheckResult logCheckResult = getLogCheckIfFinished();
        if (logCheckResult == null)
            return null;

        return !Strings.isNullOrEmpty(logCheckResult.getLastThreadDump()) ? buildId() : null;
    }

    @Nullable public LogCheckResult getLogCheckIfFinished() {
        if (logCheckResultsFut == null)
            return null;

        if (!logCheckResultsFut.isDone() || logCheckResultsFut.isCancelled()) {
            return null;
        }

        LogCheckResult logCheckResult = FutureUtil.getResultSilent(logCheckResultsFut);

        if (logCheckResult == null)
            return null;
        return logCheckResult;
    }

    public void setProblems(@Nullable List<ProblemOccurrence> problems) {
        this.problems = problems;
    }

    public void addChange(Change change) {
        if (change.isFakeStub())
            return;

        this.changes.add(change);
    }

    public List<Change> getChanges() {
        return changes;
    }

    Stream<? extends Future<?>> getFutures() {
        if (logCheckResultsFut == null)
            return Stream.empty();
        else
            return Stream.of((Future<?>)logCheckResultsFut);
    }

}
