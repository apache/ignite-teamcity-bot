package org.apache.ignite.ci;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.analysis.Expirable;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.jetbrains.annotations.NotNull;

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

    public IgnitePersistentTeamcity(Ignite ignite, String serverId) {
        this(ignite, new IgniteTeamcityHelper(serverId));
    }

    @Override public CompletableFuture<List<BuildType>> getProjectSuites(String projectId) {
        return teamcity.getProjectSuites(projectId);
    }

    @Override public String serverId() {
        return serverId;
    }

    public <K, V> V loadIfAbsent(String cacheName, K key, Function<K, V> loadFunction) {
        return loadIfAbsent(cacheName, key, loadFunction, (V v) -> true);
    }

    public <K, V> V loadIfAbsent(String cacheName, K key, Function<K, V> loadFunction, Predicate<V> saveValueFilter) {
        final IgniteCache<K, V> cache = ignite.getOrCreateCache(serverId + "." + cacheName);

        @Nullable final V persistedBuilds = cache.get(key);

        if (persistedBuilds != null)
            return persistedBuilds;
        final V loaded = loadFunction.apply(key);

        if (saveValueFilter == null || saveValueFilter.test(loaded))
            cache.put(key, loaded);
        return loaded;
    }

    public <K, V> V timedLoadIfAbsentOrMerge(String cacheName, int seconds, K key, BiFunction<K, V, V> loadWithMerge) {
        final IgniteCache<K, Expirable<V>> hist = ignite.getOrCreateCache(serverId + "." + cacheName);
        @Nullable final Expirable<V> persistedBuilds = hist.get(key);
        if (persistedBuilds != null) {
            long ageTs = System.currentTimeMillis() - persistedBuilds.getTs();
            if (ageTs < TimeUnit.SECONDS.toMillis(seconds))
                return persistedBuilds.getData();
        }
        V apply = loadWithMerge.apply(key, persistedBuilds != null ? persistedBuilds.getData() : null);
        final Expirable<V> newVal = new Expirable<>(System.currentTimeMillis(), apply);
        hist.put(key, newVal);
        return apply;
    }

    /** {@inheritDoc} */
    @Override public List<BuildRef> getFinishedBuilds(String projectId, String branch) {
        final SuiteInBranch suiteInBranch = new SuiteInBranch(projectId, branch);
        return timedLoadIfAbsentOrMerge("finishedBuilds", 60, suiteInBranch,
            (key, persistedValue) -> {
                return mergeByIdToHistoricalOrder(persistedValue,
                    teamcity.getFinishedBuilds(projectId, branch));
            });
    }

    @NotNull private List<BuildRef> mergeByIdToHistoricalOrder(List<BuildRef> persistedVal, List<BuildRef> mostActualVal) {
        final SortedMap<Integer, BuildRef> merge = new TreeMap<>();
        if (persistedVal != null)
            persistedVal.forEach(b -> merge.put(b.getId(), b));
        mostActualVal.forEach(b -> merge.put(b.getId(), b)); //to overwrite data from persistence by values from REST
        return new ArrayList<>(merge.values());
    }

    //loads build history with following parameter: defaultFilter:false,state:finished
    /** {@inheritDoc} */
    @Override public List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch) {
        final SuiteInBranch suiteInBranch = new SuiteInBranch(projectId, branch);
        return timedLoadIfAbsentOrMerge("finishedBuildsIncludeFailed", 60, suiteInBranch,
            (key, persistedValue) -> {
                return mergeByIdToHistoricalOrder(persistedValue,
                    teamcity.getFinishedBuildsIncludeSnDepFailed(projectId, branch));
            });
    }

    @Override public Build getBuildResults(String href) {
        return loadIfAbsent("buildResults",
            href,
            teamcity::getBuildResults,
            Build::hasFinishDate); //only completed builds are saved
    }

    @Override public String host() {
        return teamcity.host();
    }

    @Override public ProblemOccurrences getProblems(String href) {
        return loadIfAbsent("problems",
            href,
            teamcity::getProblems);

    }

    @Override public TestOccurrences getTests(String href) {
        return loadIfAbsent("tests",
            href,
            teamcity::getTests);

    }

    @Override public void close() {

    }
}
