package org.apache.ignite.ci.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.util.TimeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by dpavlov on 20.09.2017
 */
public class FullChainRunCtx {
    private Build results;
    private List<FullBuildRunContext> list = new ArrayList<>();

    public FullChainRunCtx(Build results, List<FullBuildRunContext> list) {
        this.results = results;
        this.list = list;
    }

    public int buildProblems() {
        return (int)list.stream().filter(FullBuildRunContext::hasNontestBuildProblem).count();
    }

    public int timeoutsOomeCrashBuildProblems() {
        return (int)list.stream().filter(context ->
            context.hasJvmCrashProblem()
                || context.hasTimeoutProblem()
                || context.hasOomeProblem()).count();
    }

    public List<FullBuildRunContext> suites() {
        return list;
    }

    public Integer getSuiteBuildId() {
        return results.getId();
    }

    public String suiteId() {
        return results.suiteId();
    }
    public String suiteName() {
        return results.suiteName();
    }

    public String branchName() {
        return results.branchName;
    }

    public int failedTests() {
        return list.stream().mapToInt(FullBuildRunContext::failedTests).sum();
    }

    public int mutedTests() {
        return list.stream().mapToInt(FullBuildRunContext::mutedTests).sum();
    }

    public int totalTests() {
        return list.stream().mapToInt(FullBuildRunContext::totalTests).sum();
    }

    public Stream<FullBuildRunContext> failedChildSuites() {
        return suites().stream().filter(FullBuildRunContext::isFailed);
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
        return suites().stream().map(FullBuildRunContext::getBuildDuration);
    }

    @NotNull public String getDurationPrintable() {
        return (TimeUtil.getDurationPrintable(getTotalDuration()))
            + (hasFullDurationInfo() ? "" : "+");
    }

    private Stream<Long> getSourceUpdateDurations() {
        return suites().stream().map(FullBuildRunContext::getSourceUpdateDuration);
    }

    public Long getTotalSourceUpdateDuration() {
        return getSourceUpdateDurations().filter(Objects::nonNull).mapToLong(t -> t).sum();
    }

    @NotNull public String getSourceUpdateDurationPrintable() {
        return (TimeUtil.getDurationPrintable(getTotalSourceUpdateDuration()))
            + (hasFullDurationInfo() ? "" : "+");
    }
}
