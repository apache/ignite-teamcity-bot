package org.apache.ignite.ci.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.util.TimeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by dpavlov on 20.09.2017
 */
public class FullChainRunCtx {
    private Build chainResults;
    private List<MultBuildRunCtx> list = new ArrayList<>();

    public FullChainRunCtx(Build chainResults ) {
        this.chainResults = chainResults;
    }

    public int buildProblems() {
        return (int)list.stream().filter(MultBuildRunCtx::hasNontestBuildProblem).count();
    }

    public int timeoutsOomeCrashBuildProblems() {
        return (int)list.stream().filter(context ->
            context.hasJvmCrashProblem()
                || context.hasTimeoutProblem()
                || context.hasOomeProblem()).count();
    }

    public List<MultBuildRunCtx> suites() {
        return list;
    }

    public Integer getSuiteBuildId() {
        return chainResults.getId();
    }

    public String suiteId() {
        return chainResults.suiteId();
    }
    public String suiteName() {
        return chainResults.suiteName();
    }

    public String branchName() {
        return chainResults.branchName;
    }

    public int failedTests() {
        return list.stream().mapToInt(MultBuildRunCtx::failedTests).sum();
    }

    public int mutedTests() {
        return list.stream().mapToInt(MultBuildRunCtx::mutedTests).sum();
    }

    public int totalTests() {
        return list.stream().mapToInt(MultBuildRunCtx::totalTests).sum();
    }

    public Stream<MultBuildRunCtx> failedChildSuites() {
        return suites().stream().filter(MultBuildRunCtx::isFailed);
    }

    /**
     * @return may return less time than actual duration if not all statistic is provided
     */
    public Long getTotalDuration() {
        return getDurations().filter(Objects::nonNull).mapToLong(t -> t).sum();
    }

    public boolean hasFullDurationInfo() {
        return getDurations().noneMatch(Objects::isNull);
    }

    private Stream<Long> getDurations() {
        return suites().stream().map(MultBuildRunCtx::getBuildDuration);
    }

    @NotNull public String getDurationPrintable() {
        return (TimeUtil.getDurationPrintable(getTotalDuration()))
            + (hasFullDurationInfo() ? "" : "+");
    }

    private Stream<Long> getSourceUpdateDurations() {
        return suites().stream().map(MultBuildRunCtx::getSourceUpdateDuration);
    }

    public Long getTotalSourceUpdateDuration() {
        return getSourceUpdateDurations().filter(Objects::nonNull).mapToLong(t -> t).sum();
    }

    @NotNull public String getSourceUpdateDurationPrintable() {
        return (TimeUtil.getDurationPrintable(getTotalSourceUpdateDuration()))
            + (hasFullDurationInfo() ? "" : "+");
    }

    public void addAllSuites(ArrayList<MultBuildRunCtx> suites) {
        this.list.addAll(suites);
    }

    public Stream<Future<?>> getFutures() {
        return list.stream().flatMap(MultBuildRunCtx::getFutures);
    }

    public Stream<Future<?>> getRunningUpdates() {
        return getFutures().filter(Objects::nonNull).filter(future -> !future.isDone() && !future.isCancelled());
    }
}
