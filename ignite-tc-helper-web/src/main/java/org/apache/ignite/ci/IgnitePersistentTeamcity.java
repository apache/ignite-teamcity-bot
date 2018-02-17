package org.apache.ignite.ci;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.analysis.Expirable;
import org.apache.ignite.ci.analysis.ISuiteResults;
import org.apache.ignite.ci.analysis.IVersioned;
import org.apache.ignite.ci.analysis.LogCheckResult;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.db.Migrations;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.util.CollectionUtil;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.util.typedef.T2;
import org.jetbrains.annotations.NotNull;

/**
 * Created by dpavlov on 03.08.2017
 */
public class IgnitePersistentTeamcity implements ITeamcity {
    public static final String RUN_STAT_CACHE = "runStat";

    public static final String STAT = "stat";
    public static final String BUILD_RESULTS = "buildResults";

    //V2 caches
    public static final String TESTS_OCCURRENCES = "testOccurrences";
    public static final String TESTS_RUN_STAT = "testsRunStat";
    public static final String LOG_CHECK_RESULT = "logCheckResult";

    private final Ignite ignite;
    private final IgniteTeamcityHelper teamcity;
    private final String serverId;

    public IgnitePersistentTeamcity(Ignite ignite, IgniteTeamcityHelper teamcity) {
        this.ignite = ignite;
        this.teamcity = teamcity;
        this.serverId = teamcity.serverId();

        Migrations migrations = new Migrations(ignite, teamcity.serverId());

        migrations.dataMigration(testOccurrencesCache(), this::addTestOccurrencesToStat);
    }


    public IgniteCache<String, TestOccurrences> testOccurrencesCache() {
        CacheConfiguration<String, TestOccurrences> ccfg = new CacheConfiguration<>(ignCacheNme(TESTS_OCCURRENCES));
        ccfg.setAffinity(new RendezvousAffinityFunction(false, 32));
        return ignite.getOrCreateCache(ccfg);
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

        return loadIfAbsent(cache, key, loadFunction, saveValueFilter);
    }

    public <K, V> V loadIfAbsent(IgniteCache<K, V> cache, K key, Function<K, V> loadFunction) {
        return loadIfAbsent(cache, key, loadFunction, null);
    }

    public <K, V> V loadIfAbsent(IgniteCache<K, V> cache, K key, Function<K, V> loadFunction,
        Predicate<V> saveValueFilter) {
        @Nullable final V persistedBuilds = cache.get(key);

        if (persistedBuilds != null)
            return persistedBuilds;

        final V loaded = loadFunction.apply(key);

        if (saveValueFilter == null || saveValueFilter.test(loaded))
            cache.put(key, loaded);

        return loaded;
    }

    public <K, V> V timedLoadIfAbsent(String cacheName, int seconds, K key, Function<K, V> load) {
        return timedLoadIfAbsentOrMerge(cacheName, seconds, key,
            (k, persistentValue) -> load.apply(k));
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
                List<BuildRef> builds;
                try {
                    builds = teamcity.getFinishedBuilds(projectId, branch);
                }
                catch (Exception e) {
                    if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                        System.err.println("Build history not found for build : " + projectId + " in " + branch);
                        builds = Collections.emptyList();
                    }
                    else
                        throw e;
                }
                return mergeByIdToHistoricalOrder(persistedValue, builds);
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
                List<BuildRef> failed = teamcity.getFinishedBuildsIncludeSnDepFailed(projectId, branch);


                return mergeByIdToHistoricalOrder(persistedValue,
                    failed);
            });
    }

    /** {@inheritDoc} */
    @Override public List<BuildRef> getRunningBuilds(String projectId, String branch) {
        //todo cache or parse from build queue instead
        return teamcity.getRunningBuilds(projectId, branch);
    }

    /** {@inheritDoc} */
    @Override public List<BuildRef> getQueuedBuilds(String projectId, String branch) {
        //todo cache or parse from build queue instead
        return teamcity.getQueuedBuilds(projectId, branch);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public Build getBuildResults(String href) {
        return loadIfAbsent(BUILD_RESULTS,
            href,
            href1 -> {
                try {
                    return teamcity.getBuildResults(href1);
                }
                catch (Exception e) {
                    if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                        e.printStackTrace();
                        return new Build();// save null result, because persistence may refer to some  unexistent build on TC
                    }
                    else
                        throw e;
                }
            },
            build -> {
                return build.getId() == null || build.hasFinishDate();
            }); //only completed builds are saved

    }

    @NotNull private String ignCacheNme(String cache) {
        return ignCacheNme(cache, serverId);
    }

    @NotNull public static String ignCacheNme(String cache, String serverId) {
        return serverId + "." + cache;
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
        String hrefForDb = Migrations.removeCountFromRef(href);

        return loadIfAbsent(testOccurrencesCache(),
            hrefForDb,  //hack to avoid test reloading from store in case of href filter replaced
            hrefIgnored -> {
                TestOccurrences loadedTests = teamcity.getTests(href);

                addTestOccurrencesToStat(loadedTests);

                return loadedTests;
            });
    }

    public void addTestOccurrencesToStat(TestOccurrences value) {
        //may use invoke all
        List<TestOccurrence> tests = value.getTests();
        for (TestOccurrence next : tests) {
            addTestOccurrenceToStat(next);
        }
    }

    @Override public Statistics getBuildStat(String href) {
        return loadIfAbsent(STAT,
            href,
            href1 -> {
                try {
                    return teamcity.getBuildStat(href1);
                }
                catch (Exception e) {
                    if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                        e.printStackTrace();
                        return new Statistics();// save null result, because persistence may refer to some  unexistent build on TC
                    }
                    else
                        throw e;
                }
            });
    }

    @Override public TestOccurrenceFull getTestFull(String href) {
        return loadIfAbsent("testOccurrenceFull",
            href,
            teamcity::getTestFull);
    }

    public List<RunStat> topFailing(int count) {
        Stream<RunStat> data = allTestAnalysis();
        return CollectionUtil.top(data, count, Comparator.comparing(RunStat::getFailRate));
    }

    public List<RunStat> topLongRunning(int count) {
        Stream<RunStat> data = allTestAnalysis();
        return CollectionUtil.top(data, count, Comparator.comparing(RunStat::getAverageDurationMs));
    }

    public Function<String, RunStat> getTestRunStatProvider() {
        return name -> name == null ? null : testRunStatCache().get(name);
    }

    public Stream<RunStat> allTestAnalysis() {
        return StreamSupport.stream(testRunStatCache().spliterator(), false)
            .map(Cache.Entry::getValue);
    }

    private IgniteCache<String, RunStat> testRunStatCache() {
        CacheConfiguration<String, RunStat> ccfg = new CacheConfiguration<>(ignCacheNme(TESTS_RUN_STAT));
        ccfg.setAffinity(new RendezvousAffinityFunction(false, 32));
        return ignite.getOrCreateCache(ccfg);
    }

    private IgniteCache<Integer, LogCheckResult> logCheckResultCache() {
        CacheConfiguration<Integer, LogCheckResult> ccfg = new CacheConfiguration<>(ignCacheNme(LOG_CHECK_RESULT));
        ccfg.setAffinity(new RendezvousAffinityFunction(false, 32));
        return ignite.getOrCreateCache(ccfg);
    }


    private void addTestOccurrenceToStat(TestOccurrence next) {
        String name = next.getName();
        if(Strings.isNullOrEmpty(name) )
            return;

        if(next.isMutedTest() || next.isIgnoredTest())
            return;

        testRunStatCache().invoke(name, new EntryProcessor<String, RunStat, Object>() {
            @Override
            public Object process(MutableEntry<String, RunStat> entry,
                Object... arguments) throws EntryProcessorException {

                String key = entry.getKey();

                TestOccurrence testOccurrence = (TestOccurrence)arguments[0];

                RunStat value = entry.getValue();
                if (value == null) {
                    value = new RunStat(key);
                }
                value.addTestRun(testOccurrence);

                entry.setValue(value);
                return null;
            }
        }, next);
    }

    public List<RunStat> topFailingSuite(int count) {
        Map<String, RunStat> map = runSuiteAnalysis();
        Stream<RunStat> data = map.values().stream();
        return CollectionUtil.top(data, count, Comparator.comparing(RunStat::getFailRate));
    }

    public Map<String, RunStat> runSuiteAnalysis() {
        return timedLoadIfAbsent(ignCacheNme(RUN_STAT_CACHE),
            60 * 5, "runSuiteAnalysis",
            k ->  runSuiteAnalysisNoCache());
    }

    @NotNull private Map<String, RunStat> runSuiteAnalysisNoCache() {
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

    @Override public CompletableFuture<T2<File, LogCheckResult>> processBuildLog(int buildId, ISuiteResults ctx) {
        return teamcity.processBuildLog(buildId, ctx);
    }

    @Override public CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut) {
        return teamcity.unzipFirstFile(fut);
    }

    @Override public CompletableFuture<File> downloadBuildLogZip(int id) {
        return teamcity.downloadBuildLogZip(id);
    }

    @Override public CompletableFuture<LogCheckResult> getLogCheckResults(Integer buildId, SingleBuildRunCtx ctx) {
        return loadFutureIfAbsent(logCheckResultCache(), buildId,
            k -> teamcity.getLogCheckResults(buildId, ctx));
    }

    public <K, V extends IVersioned> CompletableFuture<V> loadFutureIfAbsent(IgniteCache<K, V> cache,
        K key,
        Function<K, CompletableFuture<V>> submitFunction) {
        @Nullable final V persistedValue = cache.get(key);

        if (persistedValue != null
            && persistedValue.version() >= persistedValue.latestVersion())
            return CompletableFuture.completedFuture(persistedValue);

        //todo caching of already submitted computations
        CompletableFuture<V> apply = submitFunction.apply(key);

        return apply.thenApplyAsync(val -> {
            cache.put(key, val);
            return val;
        });
    }
}
