package org.apache.ignite.ci.web.model.current;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.issues.IssueRef;
import org.apache.ignite.ci.tcmodel.result.issues.IssueUsage;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.util.TimeUtil;

public class BuildStatisticsSummary {
    public Build build;

    private List<ProblemOccurrence> problems;

    public List<IssueRef> relatedIssues;

    public String durationPrintable;

    public String result;

    public BuildStatisticsSummary(Build build){
        this.build = build;
    }

    public void initialize(@Nonnull final ITeamcity teamcity) {

        problems = teamcity.getProblems(build).getProblemsNonNull();

        relatedIssues = teamcity.getIssuesUsagesList(build.relatedIssuesRef.href).getIssuesUsagesNonNull().stream()
            .map(IssueUsage::getIssue).collect(Collectors.toList());

        durationPrintable = TimeUtil
            .getDurationPrintable(build.getFinishDate().getTime() - build.getStartDate().getTime());

        result = getResult();
    }

    private long getExecutionTimeoutCount() {
        return getProblemsStream().filter(ProblemOccurrence::isExecutionTimeout).count();
    }

    private long getJvmCrashProblemCount() {

        return getProblemsStream().filter(ProblemOccurrence::isJvmCrash).count();
    }

    private long getExitCodeProblemsCount() {
        return getProblemsStream().filter(ProblemOccurrence::isExitCode).count();
    }

    private long getOomeProblemCount() {
        return getProblemsStream().filter(ProblemOccurrence::isOome).count();
    }

    private long getFailedTestsProblemCount() {
        return getProblemsStream().filter(ProblemOccurrence::isFailedTests).count();
    }

    private long getSnapshotDepProblemCount() {
        return getProblemsStream().filter(ProblemOccurrence::isSnapshotDepProblem).count();
    }

    private long getOtherProblemCount() {
        return getProblemsStream().filter(p ->
            !p.isFailedTests()
                && !p.isSnapshotDepProblem()
                && !p.isExecutionTimeout()
                && !p.isJvmCrash()
                && !p.isExitCode()
                && !p.isOome()).count();
    }

    private Stream<ProblemOccurrence> getProblemsStream() {
        if (problems == null)
            return Stream.empty();

        return problems.stream().filter(Objects::nonNull);
    }

    private String getResult() {
        StringBuilder res = new StringBuilder();

        addKnownProblemCnt(res, "TIMEOUT", getExecutionTimeoutCount());
        addKnownProblemCnt(res, "JVM CRASH", getJvmCrashProblemCount());
        addKnownProblemCnt(res, "OOMe", getOomeProblemCount());
        addKnownProblemCnt(res, "EXIT CODE", getExitCodeProblemsCount());
        addKnownProblemCnt(res, "FAILED TESTS", getFailedTestsProblemCount());
        addKnownProblemCnt(res, "SNAPSHOT DEPENDENCY ERROR", getSnapshotDepProblemCount());
        addKnownProblemCnt(res, "OTHER", getOtherProblemCount());

        res.insert(0, "ALL [" + build.problemOccurrences.count + "]" + (res.length() != 0 ? ": " : " "));

        return res.toString();
    }

    private void addKnownProblemCnt(StringBuilder res, String nme, long execToCnt) {
        if (execToCnt > 0) {
            if (res.length() > 0)
                res.append(", ");

            res.append(nme)
                .append(" ")
                .append(execToCnt > 1 ? "[" + execToCnt + "]" : "");
        }
    }

}