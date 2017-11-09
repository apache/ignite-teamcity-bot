package org.apache.ignite.ci;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.analysis.FullBuildRunContext;
import org.apache.ignite.ci.logs.LogsAnalyzer;
import org.apache.ignite.ci.logs.handlers.LastTestLogCopyHandler;
import org.apache.ignite.ci.logs.handlers.ThreadDumpCopyHandler;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;

/**
 * API for calling methods from REST service:
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public interface ITeamcity extends AutoCloseable {

    String DEFAULT = "<default>";

    CompletableFuture<List<BuildType>> getProjectSuites(String projectId);

    String serverId();

    /**
     * @param projectId suite ID (string without spaces)
     * @param branch
     * @return list of builds in historical order, recent builds coming last
     */
    List<BuildRef> getFinishedBuilds(String projectId, String branch);

    default Optional<BuildRef> getLastFinishedBuild(String projectId, String branch) {
        final List<BuildRef> builds = getFinishedBuilds(projectId, branch);
        return builds.stream().max(Comparator.comparing(BuildRef::getId));
    }

    /**
     * Includes snapshot dependencies failed builds into list
     *
     * @param projectId suite ID (string without spaces)
     * @param branch
     * @return list of builds in historical order, recent builds coming last
     */
    List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch);

    default Optional<BuildRef> getLastBuildIncludeSnDepFailed(String projectId, String branch) {
        final List<BuildRef> builds = getFinishedBuildsIncludeSnDepFailed(projectId, branch);
        return builds.stream().max(Comparator.comparing(BuildRef::getId));
    }

    default int[] getBuildNumbersFromHistory(String projectId, String branchNameForHist) {
        return getFinishedBuilds(projectId, branchNameForHist).stream().mapToInt(BuildRef::getId).toArray();
    }

    Build getBuildResults(String href);

    default Build getBuildResults(int id) {
        return getBuildResults("app/rest/latest/builds/id:" + Integer.toString(id));
    }

    /**
     * @return Normalized Host address, ends with '/'.
     */
    public String host();

    ProblemOccurrences getProblems(String href);

    TestOccurrences getTests(String href);

    Statistics getBuildStat(String href);

    TestOccurrenceFull getTestFull(String href);

    /**
     * Runs deep collection of all related statistics for particular build
     *
     * @param build build from history with references to tests
     * @return full context
     */
    @Nonnull default FullBuildRunContext loadTestsAndProblems(@Nonnull Build build) {
        FullBuildRunContext ctx = new FullBuildRunContext(build);
        if (build.problemOccurrences != null)
            ctx.setProblems(getProblems(build.problemOccurrences.href).getProblemsNonNull());

        if (build.testOccurrences != null) {
            List<TestOccurrence> tests = getTests(build.testOccurrences.href + ",count:7700").getTests();
            ctx.setTests(tests);

            for (TestOccurrence next : tests) {
                //todo is it required to load non failed test here
                if (next.href != null && next.isFailedTest()) {
                    TestOccurrenceFull testOccurrenceFull = getTestFull(next.href);
                    String testInBuildId = next.id;
                    if (testOccurrenceFull.test != null && testOccurrenceFull.test.id != null)
                        ctx.addTestInBuildToTestFull(testInBuildId, testOccurrenceFull);
                }
            }
        }

        if (build.statisticsRef != null) {
            ctx.setStat(getBuildStat(build.statisticsRef.href));
        }
        return ctx;
    }

    @Override void close();

    default BuildRef tryReplaceBuildRefByRecent(BuildRef buildRef) {
        return getLastFinishedBuild(buildRef.buildTypeId,
            buildRef.branchName == null ? DEFAULT : buildRef.branchName).orElse(buildRef);
    }

    default FullBuildRunContext loadTestsAndProblems(BuildRef recentRef) {
        Build build = getBuildResults(recentRef.href);

        if (build == null || build.getId() == null)
            return null;

        return loadTestsAndProblems(build);
    }

    default CompletableFuture<File> processBuildLog(FullBuildRunContext ctx) {
        final CompletableFuture<File> zipFut = downloadBuildLogZip(ctx.getBuildId());
        final CompletableFuture<File> clearLogFut = unzipFirstFile(zipFut);
        final ThreadDumpCopyHandler threadDumpCp = new ThreadDumpCopyHandler();
        final LastTestLogCopyHandler lastTestCp = new LastTestLogCopyHandler();
        boolean dumpLastTest = ctx.hasTimeoutProblem() || ctx.hasJvmCrashProblem() || ctx.hasOomeProblem();
        lastTestCp.setDumpLastTest(dumpLastTest);
        final LogsAnalyzer analyzer = new LogsAnalyzer(threadDumpCp, lastTestCp);
        final CompletableFuture<File> fut2 = clearLogFut.thenApplyAsync(analyzer);
        return fut2.thenApplyAsync(file -> {
            if (dumpLastTest) {
                ctx.setLastStartedTest(lastTestCp.getLastTestName());
                ctx.setThreadDumpFileIdx(threadDumpCp.getLastFileIdx());
            }
            return file;
        });
    }

    CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut);

    CompletableFuture<File> downloadBuildLogZip(int id);
}
