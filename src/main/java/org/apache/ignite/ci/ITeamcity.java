package org.apache.ignite.ci;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.ignite.ci.model.BuildType;

/** API for calling methods from REST service:
 * https://confluence.jetbrains.com/display/TCD10/REST+API */
public interface ITeamcity extends AutoCloseable {
    CompletableFuture<List<BuildType>> getProjectSuites(String projectId);
}
