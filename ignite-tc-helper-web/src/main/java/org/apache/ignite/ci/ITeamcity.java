package org.apache.ignite.ci;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.analysis.ISuiteResults;
import org.apache.ignite.ci.analysis.LogCheckResult;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangeRef;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.internal.util.typedef.T2;
import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.db.DbMigrations.TESTS_COUNT_7700;

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

    /**
     * Includes snapshot dependencies failed builds into list
     *
     * @param projectId suite ID (string without spaces)
     * @param branch branch in TC identification
     * @return list of builds in historical order, recent builds coming last
     */
    List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch);

    default Optional<BuildRef> getLastBuildIncludeSnDepFailed(String projectId, String branch) {
        final List<BuildRef> builds = getFinishedBuildsIncludeSnDepFailed(projectId, branch);
        return builds.stream().max(Comparator.comparing(BuildRef::getId));
    }

    /**   */
    CompletableFuture<List<BuildRef>> getRunningBuilds(@Nullable String branch);

    /**   */
    CompletableFuture<List<BuildRef>> getQueuedBuilds(@Nullable String branch);

    default int[] getBuildNumbersFromHistory(String projectId, String branchNameForHist) {
        return getFinishedBuilds(projectId, branchNameForHist).stream().mapToInt(BuildRef::getId).toArray();
    }

    Build getBuild(String href);

    default Build getBuild(int id) {
        return getBuild(getBuildHrefById(id));
    }

    @NotNull default String getBuildHrefById(int id) {
        return "app/rest/latest/builds/id:" + Integer.toString(id);
    }

    /**
     * @return Normalized Host address, ends with '/'.
     */
    public String host();

    ProblemOccurrences getProblems(String href);

    TestOccurrences getTests(String href);

    Statistics getBuildStat(String href);

    CompletableFuture<TestOccurrenceFull> getTestFull(String href);

    Change getChange(String href);

    ChangesList getChangesList(String href);

    /**
     * Runs deep collection of all related statistics for particular build
     *
     * @param build build from history with references to tests
     * @return full context
     */
    @Nonnull default MultBuildRunCtx loadTestsAndProblems(@Nonnull Build build) {
        MultBuildRunCtx ctx = new MultBuildRunCtx(build);
        loadTestsAndProblems(build, ctx);
        return ctx;
    }

    default SingleBuildRunCtx loadTestsAndProblems(@Nonnull Build build, @Deprecated MultBuildRunCtx mCtx) {
        SingleBuildRunCtx ctx = new SingleBuildRunCtx(build);
        if (build.problemOccurrences != null) {
            List<ProblemOccurrence> problems = getProblems(build.problemOccurrences.href).getProblemsNonNull();

            mCtx.addProblems(problems);
            ctx.setProblems(problems);
        }

        if (build.lastChanges != null) {
            for (ChangeRef next : build.lastChanges.changes) {
                if(!isNullOrEmpty(next.href)) {
                    // just to cache this change
                    getChange(next.href);
                }
            }
        }

        if (build.changesRef != null) {
            ChangesList changeList = getChangesList(build.changesRef.href);
            // System.err.println("changes: " + changeList);
            if (changeList.changes != null) {
                for (ChangeRef next : changeList.changes) {
                    if (!isNullOrEmpty(next.href)) {
                        // just to cache this change
                        ctx.addChange(getChange(next.href));
                    }
                }
            }
        }

        if (build.testOccurrences != null) {
            List<TestOccurrence> tests = getTests(build.testOccurrences.href +
                TESTS_COUNT_7700).getTests();
            mCtx.addTests(tests);

            for (TestOccurrence next : tests) {
                if (next.href != null && next.isFailedTest()) {
                    CompletableFuture<TestOccurrenceFull> testFullFut = getTestFull(next.href);

                    String testInBuildId = next.getId();

                    mCtx.addTestInBuildToTestFull(testInBuildId, testFullFut);
                }
            }
        }

        if (build.statisticsRef != null)
            mCtx.setStat(getBuildStat(build.statisticsRef.href));

        return ctx;
    }

    @Override void close();


    CompletableFuture<T2<File, LogCheckResult>> processBuildLog(int buildId, ISuiteResults ctx);

    CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut);

    CompletableFuture<File> downloadBuildLogZip(int id);

    CompletableFuture<LogCheckResult> getLogCheckResults(Integer buildId, SingleBuildRunCtx ctx);

    void setExecutor(ExecutorService pool);
}
