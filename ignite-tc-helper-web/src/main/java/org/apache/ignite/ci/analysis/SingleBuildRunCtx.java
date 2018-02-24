package org.apache.ignite.ci.analysis;

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
    private List<ProblemOccurrence> problems;
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
        return problems.stream().filter(Objects::nonNull).filter(ProblemOccurrence::isExecutionTimeout).count();
    }

    public boolean hasJvmCrashProblem() {
        return getJvmCrashProblemCount() > 0;
    }

    private long getJvmCrashProblemCount() {
        return problems.stream().filter(Objects::nonNull).filter(ProblemOccurrence::isJvmCrash).count();
    }

    public boolean hasOomeProblem() {
        return getOomeProblemCount() > 0;
    }

    private long getOomeProblemCount() {
        return problems.stream().filter(ProblemOccurrence::isOome).count();
    }

    @Override public String suiteId() {
        return build.suiteId();
    }

    public void setLogCheckResultsFut(CompletableFuture<LogCheckResult> logCheckResultsFut) {
        this.logCheckResultsFut = logCheckResultsFut;
    }

    @Nullable
    public String getCriticalFailLastStartedTest() {
        if (logCheckResultsFut == null)
            return null;

        if (!logCheckResultsFut.isDone() || logCheckResultsFut.isCancelled()) {
            return null;
        }

        LogCheckResult logCheckResult = FutureUtil.getResultSilent(logCheckResultsFut);

        if (logCheckResult == null)
            return null;

        return logCheckResult.getLastStartedTest();
    }

    public void setProblems(List<ProblemOccurrence> problems) {
        this.problems = problems;
    }

    public void addChange(Change change) {
        this.changes.add(change);
    }

    public List<Change> getChanges() {
        return changes;
    }

    public Stream<? extends Future<?>> getFutures() {
        if (logCheckResultsFut == null)
            return Stream.empty();
        else
            return Stream.of((Future<?>)logCheckResultsFut);
    }
}
