package org.apache.ignite.ci;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;

/**
 * API for calling methods from REST service:
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public interface ITeamcity extends AutoCloseable {
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
        return !builds.isEmpty() ? Optional.of(builds.get(builds.size() - 1)) : Optional.empty();
    }

    /**
     * Includes snapshot dependencies failed builds into list
     * @param projectId suite ID (string without spaces)
     * @param branch
     * @return list of builds in historical order, recent builds coming last
     */
    List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch);

    default Optional<BuildRef> getLastBuildIncludeSnDepFailed(String projectId, String branch) {
        final List<BuildRef> builds = getFinishedBuildsIncludeSnDepFailed(projectId, branch);
        return !builds.isEmpty() ? Optional.of(builds.get(builds.size() - 1)) : Optional.empty();
    }


    default int[] getBuildNumbersFromHistory(String projectId, String branchNameForHist) {
        return getFinishedBuilds(projectId, branchNameForHist).stream().mapToInt(BuildRef::getId).toArray();
    }


    Build getBuildResults(String href);

    default Build getBuildResults(int id) {
        return getBuildResults( "app/rest/latest/builds/id:" + Integer.toString(id));
    }

    /**
     * @return Normalized Host address, ends with '/'.
     */
    public String host();

    ProblemOccurrences getProblems(String href);

    TestOccurrences getTests(String href);

    @Override void close();
}
