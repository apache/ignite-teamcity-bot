package org.apache.ignite.ci;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.ignite.ci.model.conf.BuildType;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.model.result.FullBuildInfo;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.model.result.tests.TestOccurrences;

/**
 * API for calling methods from REST service:
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public interface ITeamcity extends AutoCloseable {
    CompletableFuture<List<BuildType>> getProjectSuites(String projectId);

    String serverId();

    /**
     *
     * @param projectId
     * @param branch
     * @return list of builds, recent builds coming last
     */
    List<Build> getFinishedBuildsIncludeFailed(String projectId, String branch);

    FullBuildInfo getBuildResults(String href);

    default FullBuildInfo getBuildResults(int id) {
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
