package org.apache.ignite.ci.runners;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.model.result.FullBuildInfo;
import org.apache.ignite.ci.model.result.TestOccurrencesRef;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrences;

/**
 * Created by dpavlov on 03.08.2017
 */
public class CheckBuildChainResults {

    private static class ChainContext {
        private FullBuildInfo results;
        List<FullSuiteContext> list = new ArrayList<>();

        public ChainContext(FullBuildInfo results, List<FullSuiteContext> list) {
            this.results = results;
            this.list = list;
        }

        public int buildProblems() {
            return (int)list.stream().filter(FullSuiteContext::hasNontestBuildProblem).count();
        }

        public String suiteName() {
            return results.suiteName();
        }

        public int failedTests() {
            return list.stream().mapToInt(FullSuiteContext::failedTests).sum();
        }
    }

    private static class FullSuiteContext {
        private FullBuildInfo buildInfo;
        private List<ProblemOccurrence> problems;

        public FullSuiteContext(FullBuildInfo buildInfo) {
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
                !"TC_FAILED_TESTS".equals(problem.type)
                    && !"SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE".equals(problem.type)
                    && !"BuildFailureOnMessage".equals(problem.type));

            //todo what to do with BuildFailureOnMessage, now it is ignored
        }

        public int failedTests() {
            TestOccurrencesRef testOccurrences = buildInfo.testOccurrences;
            if (testOccurrences == null)
                return 0;
            Integer failed = testOccurrences.failed;
            return failed == null ? 0 : failed;
        }
    }

    public static void main(String[] args) throws Exception {
        List<ChainContext> suitesStatus = new ArrayList<>();
        try (IgniteTeamcityHelper teamcity = new IgniteTeamcityHelper("public")) {
            suitesStatus.add(getLatestSuiteRunStatus(teamcity, "Ignite20Tests_RunAll", "pull%2F2296%2Fhead"));
        }
        try (IgniteTeamcityHelper teamcity = new IgniteTeamcityHelper("private")) {
            suitesStatus.add(getLatestSuiteRunStatus(teamcity, "id8xIgniteGridGainTests_RunAll", "ignite-2.1.3"));
        }

        try (IgniteTeamcityHelper teamcity = new IgniteTeamcityHelper("public")) {
            suitesStatus.add(getLatestSuiteRunStatus(teamcity, "Ignite20Tests_RunAll", "refs/heads/master"));
        }
        try (IgniteTeamcityHelper teamcity = new IgniteTeamcityHelper("private")) {
            suitesStatus.add(getLatestSuiteRunStatus(teamcity, "id8xIgniteGridGainTests_RunAll", "refs/heads/master"));
        }

        for (ChainContext next : suitesStatus) {
            System.out.print(" " + (next == null ? "?" : next.suiteName())
                + "\t" + (next == null ? "?" : next.results.branchName) + "\t \t");
        }
        System.out.println();
        for (ChainContext suiteCtx : suitesStatus) {
            System.out.print(
                (suiteCtx == null ? "?" : suiteCtx.buildProblems()) + "\t"
                    + (suiteCtx == null ? "?" : suiteCtx.failedTests()) + "\t \t");
        }

    }

    private static ChainContext getLatestSuiteRunStatus(IgniteTeamcityHelper teamcity,
        String suite, String branch) {
        List<Build> all = teamcity.getBuildHistory(suite,
            branch,
            false,
            "finished");
        //todo status!="UNKNOWN";
        return all.isEmpty() ? null : loadChainContext(teamcity, all.get(0));
    }

    private static ChainContext loadChainContext(IgniteTeamcityHelper teamcity, Build latest) {
        String href = latest.href;
        FullBuildInfo results = teamcity.getBuildResults(href);
        List<Build> builds = results.getSnapshotDependenciesNonNull();

        List<FullSuiteContext> fullBuildInfoList = new ArrayList<>();
        for (Build next : builds) {
            FullBuildInfo dep = teamcity.getBuildResults(next.href);

            FullSuiteContext ctx = new FullSuiteContext(dep);
            if (dep.problemOccurrences != null) {
                ProblemOccurrences problems = teamcity.getProblems(dep.problemOccurrences.href);
                ctx.setProblems(problems.getProblemsNonNull());
            }
            fullBuildInfoList.add(ctx);
        }

        return new ChainContext(results, fullBuildInfoList);
    }
}
