package org.apache.ignite.ci;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.ignite.ci.model.conf.BuildType;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.model.result.FullBuildInfo;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrences;

/** API for calling methods from REST service:
 * https://confluence.jetbrains.com/display/TCD10/REST+API */
public interface ITeamcity extends AutoCloseable {
    CompletableFuture<List<BuildType>> getProjectSuites(String projectId);

    String serverId();

    List<Build> getFinishedBuildsIncludeFailed(String id, String branch);

    FullBuildInfo getBuildResults(String href);

    ProblemOccurrences getProblems(String href);
}
