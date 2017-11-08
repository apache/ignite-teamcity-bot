package org.apache.ignite.ci;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.cache.CacheException;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.analysis.Expirable;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.util.CollectionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by dpavlov on 03.08.2017
 */
public class IgnitePersistentTeamcity implements ITeamcity {

    public static final String TESTS = "tests";
    public static final String STAT = "stat";
    public static final String BUILD_RESULTS = "buildResults";
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
        final IgniteCache<K, V> cache = ignite.getOrCreateCache(ignCacheNme(cacheName));

        @Nullable final V persistedBuilds = cache.get(key);

        if (persistedBuilds != null)
            return persistedBuilds;
        final V loaded = loadFunction.apply(key);

        if (saveValueFilter == null || saveValueFilter.test(loaded))
            cache.put(key, loaded);
        return loaded;
    }

    public <K, V> V timedLoadIfAbsentOrMerge(String cacheName, int seconds, K key, BiFunction<K, V, V> loadWithMerge) {
        final IgniteCache<K, Expirable<V>> hist = ignite.getOrCreateCache(ignCacheNme(cacheName));
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

    /** {@inheritDoc} */
    @Nullable
    @Override public Build getBuildResults(String href) {
        try {
            Build results = loadIfAbsent(BUILD_RESULTS,
                href,
                teamcity::getBuildResults,
                Build::hasFinishDate);
            if (results.getBuildType() == null || results.getBuildType().getProjectId() == null) {
                //trying to reload to get version with filled project ID
                try {
                    Build results1 = teamcity.getBuildResults(href);
                    ignite.getOrCreateCache(ignCacheNme(BUILD_RESULTS)).put(href, results1);
                    return results1;
                }
                catch (CacheException e) {
                    if (Throwables.getRootCause(e) instanceof FileNotFoundException)
                        throw e;
                    e.printStackTrace();
                }
            }
            return results; //only completed builds are saved
        }
        catch (Exception e) {
            if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                //404 error from REST api
                final IgniteCache<Object, Object> cache = ignite.getOrCreateCache(ignCacheNme(BUILD_RESULTS));
                e.printStackTrace();
                //todo log error

                final Build fakeBuild = new Build();
                cache.put(href, fakeBuild); // save null result, because persistence may refer to some unexistent build on TC
                return fakeBuild;
            } else
                throw e;
        }
    }

    @NotNull private String ignCacheNme(String results) {
        return serverId + "." + results;
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
        return loadIfAbsent(TESTS,
            href.replace("\",count:7700\"", "\",count:7500\""),  //hack to avoid test reloading from stroe
            teamcity::getTests);
    }

    @Override public Statistics getBuildStat(String href) {
        return loadIfAbsent(STAT,
            href,
            teamcity::getBuildStat);
    }

    @Override public TestOccurrenceFull getTestFull(String href) {
        return loadIfAbsent("testOccurrenceFull",
            href,
            teamcity::getTestFull);
    }

    public List<RunStat> topFailing(int count) {
        Map<String, RunStat> map = runTestAnalysis();
        Stream<RunStat> data = map.values().stream();
        return CollectionUtil.top(data, count, Comparator.comparing(RunStat::getFailRate));
    }

    public List<RunStat> topLongRunning(int count) {
        Map<String, RunStat> map = runTestAnalysis();
        Stream<RunStat> data = map.values().stream();
        return CollectionUtil.top(data, count, Comparator.comparing(RunStat::getAverageDurationMs));
    }

    public Map<String, RunStat> runTestAnalysis() {
        final Map<String, RunStat> map = new HashMap<>();
        final IgniteCache<Object, TestOccurrences> cache = ignite.cache(ignCacheNme(TESTS));
        if (cache == null)
            return map;
        for (Cache.Entry<Object, TestOccurrences> next : cache) {
            final TestOccurrences val = next.getValue();
            for (TestOccurrence occurrence : val.getTests()) {
                final String name = occurrence.getName();
                if (!Strings.isNullOrEmpty(name) && occurrence.isNotMutedOrIgnoredTest())
                    map.computeIfAbsent(name, RunStat::new).addTestRun(occurrence);
            }
        }
        return map;
    }


    public List<RunStat> topFailingSuite(int count) {
        Map<String, RunStat> map = runSuiteAnalysis();
        Stream<RunStat> data = map.values().stream();
        return CollectionUtil.top(data, count, Comparator.comparing(RunStat::getFailRate));
    }

    public Map<String, RunStat> runSuiteAnalysis() {
        final Map<String, RunStat> map = new HashMap<>();
        final IgniteCache<Object, Build> cache = ignite.getOrCreateCache(ignCacheNme(BUILD_RESULTS));
        if (cache == null)
            return map;
        for (Cache.Entry<Object, Build> next : cache) {
            final Build build = next.getValue();
            final String name = build.suiteName();
            if (!Strings.isNullOrEmpty(name))
                map.computeIfAbsent(name, RunStat::new).addTestRun(build);

        }
        return map;
    }

    @Override public void close() {

    }

    @Override public CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut) {
        return teamcity.unzipFirstFile(fut);
    }

    @Override public CompletableFuture<File> downloadBuildLogZip(int id) {
        return teamcity.downloadBuildLogZip(id);
    }
}
