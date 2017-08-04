package org.apache.ignite.ci;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.model.conf.BuildType;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.model.result.FullBuildInfo;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrences;

/**
 * Created by dpavlov on 03.08.2017
 */
public class IgnitePersistentTeamcity implements ITeamcity {

    private final Ignite ignite;
    private final IgniteTeamcityHelper teamcity;
    private final String serverId;

    public IgnitePersistentTeamcity(Ignite ignite, IgniteTeamcityHelper teamcity) {
        this.ignite = ignite;
        this.teamcity = teamcity;
        this.serverId = teamcity.serverId();
    }

    public IgnitePersistentTeamcity(Ignite ignite, String serverId) throws IOException {
        this(ignite, new IgniteTeamcityHelper(serverId));
    }

    @Override public CompletableFuture<List<BuildType>> getProjectSuites(String projectId) {
        return teamcity.getProjectSuites(projectId);
    }

    @Override public String serverId() {
        return serverId;
    }

    @Override public List<Build> getFinishedBuildsIncludeFailed(String id, String branch) {
        //todo may persist this
        return teamcity.getFinishedBuildsIncludeFailed(id, branch);
    }

    @Override public FullBuildInfo getBuildResults(String href) {
        IgniteCache<String, FullBuildInfo> cache = ignite.getOrCreateCache(serverId + "." + "buildResults");
        FullBuildInfo info = cache.get(href);
        if (info != null)
            return info;
        FullBuildInfo results = teamcity.getBuildResults(href);
        cache.put(href, results);
        return results;
    }

    @Override public ProblemOccurrences getProblems(String href) {
        IgniteCache<String, ProblemOccurrences> cache = ignite.getOrCreateCache(serverId + "." + "problems");
        ProblemOccurrences info = cache.get(href);
        if (info != null)
            return info;
        ProblemOccurrences results = teamcity.getProblems(href);
        cache.put(href, results);
        return results;
    }

    @Override public void close() throws Exception {

    }
}
