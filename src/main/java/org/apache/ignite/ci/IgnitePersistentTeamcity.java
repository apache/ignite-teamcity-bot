package org.apache.ignite.ci;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.model.Expirable;
import org.apache.ignite.ci.model.SuiteInBranch;
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
        final SuiteInBranch suiteInBranch = new SuiteInBranch(id, branch);
        final IgniteCache<SuiteInBranch, Expirable<List<Build>>> hist = ignite.getOrCreateCache(serverId + "." + "finishedBuildsIncludeFailed");
        @Nullable final Expirable<List<Build>> persistedBuilds = hist.get(suiteInBranch);
        if (persistedBuilds != null) {
            long ageTs = System.currentTimeMillis() - persistedBuilds.getTs();
            if (ageTs < TimeUnit.MINUTES.toMillis(1))
                return persistedBuilds.getData();
        }

        final List<Build> finished = teamcity.getFinishedBuildsIncludeFailed(id, branch);
        final SortedMap<Integer, Build> merge = new TreeMap<>();

        if (persistedBuilds != null)
            persistedBuilds.getData().forEach(b -> merge.put(b.getIdAsInt(), b));
        //to overwrite data from persistence
        finished.forEach(b -> merge.put(b.getIdAsInt(), b));

        final List<Build> builds = new ArrayList<>(merge.values());
        final Expirable<List<Build>> newVal = new Expirable<>(System.currentTimeMillis(), builds);
        hist.put(suiteInBranch, newVal);
        return builds;
    }

    @Override public FullBuildInfo getBuildResults(String href) {
        IgniteCache<String, FullBuildInfo> cache = ignite.getOrCreateCache(serverId + "." + "buildResults");
        FullBuildInfo info = cache.get(href);
        if (info != null)
            return info;
        FullBuildInfo results = teamcity.getBuildResults(href);
        if (results.finishDate != null) //only completed builds are saved
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
