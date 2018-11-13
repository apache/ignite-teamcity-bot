/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.Expirable;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.web.rest.build.GetBuildTestFailures;
import org.apache.ignite.ci.web.rest.pr.GetPrTestFailures;
import org.apache.ignite.ci.web.rest.tracked.GetTrackedBranchTestResults;
import org.apache.ignite.configuration.CacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migrations to be applied to each TC related caches.
 */
public class DbMigrations {
    public static final String BUILD_QUEUE = "buildQueue";
    public static final String RUNNING_BUILDS = "runningBuilds";
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(DbMigrations.class);

    public static final String DONE_MIGRATIONS = "doneMigrations";
    @Deprecated
    public static final String TESTS = "tests";
    @Deprecated
    private static final String BUILD_RESULTS = "buildResults";

    private static final String BUILD_STATISTICS = "buildStatistics";

    private static final String BUILD_CONDITIONS_CACHE_NAME = "buildConditions";

    public static final String TESTS_COUNT_7700 = ",count:7700";

    //V1 caches, 1024 parts
    @Deprecated
    public static final String RUN_STAT_CACHE = "runStat";

    @Deprecated
    public static final String TEST_OCCURRENCE_FULL = "testOccurrenceFull";

    @Deprecated
    private static final String PROBLEMS = "problems";

    @Deprecated
    private static final String STAT = "stat";

    @Deprecated
    private static final String FINISHED_BUILDS = "finishedBuilds";

    @Deprecated
    public static final String FINISHED_BUILDS_INCLUDE_FAILED = "finishedBuildsIncludeFailed";

    @Deprecated
    public static final String ISSUES = "issues";

    /** Cache name */
    public static final String TEAMCITY_BUILD_CACHE_NAME_OLD = "teamcityBuild";


    private static final String CHANGE_INFO_FULL = "changeInfoFull";
    private static final String CHANGES_LIST = "changesList";

    public static final int SUITES_CNT = 100;

    private final Ignite ignite;
    private final String serverId;
    private IgniteCache<String, Object> doneMigrations;

    public DbMigrations(Ignite ignite, String srvId) {
        this.ignite = ignite;
        this.serverId = srvId;
    }

    public static String removeCountFromRef(String href) {
        return href.replace(TESTS_COUNT_7700, "")
            .replace(",count:7500", "");
    }

    public void dataMigration(
            IgniteCache<String, TestOccurrences> testOccurrencesCache, Consumer<TestOccurrences> saveTestToStat,
            Consumer<TestOccurrences> saveTestToLatest,
            Cache<String, Build> buildCache, Consumer<Build> saveBuildToStat,
            IgniteCache<SuiteInBranch, RunStat> suiteHistCache,
            IgniteCache<TestInBranch, RunStat> testHistCache,
            Cache<String, TestOccurrenceFull> testFullCache,
            Cache<String, ProblemOccurrences> problemsCache,
            Cache<SuiteInBranch, Expirable<List<BuildRef>>> buildHistCache,
            Cache<SuiteInBranch, Expirable<List<BuildRef>>> buildHistInFailedCache) {

        doneMigrations = doneMigrationsCache();

        applyMigration("InitialFillLatestRunsV3", () -> {
            int size = testOccurrencesCache.size();
            if (size > 0) {
                int i = 0;
                int maxFoundBuildId = 0;
                for (Cache.Entry<String, TestOccurrences> entry : testOccurrencesCache) {
                    String key = entry.getKey();

                    Integer buildId = RunStat.extractIdPrefixed(key, "locator=build:(id:", ")");
                    if (buildId != null) {
                        if (buildId > maxFoundBuildId)
                            maxFoundBuildId = buildId;

                        if (buildId < maxFoundBuildId - (RunStat.MAX_LATEST_RUNS * SUITES_CNT * 3))
                            System.out.println(serverId + " - Skipping entry " + i + " from " + size + ": " + key);
                        else {
                            System.out.println(serverId + " - Migrating entry " + i + " from " + size + ": " + key);

                            saveTestToLatest.accept(entry.getValue());
                        }
                    }

                    i++;
                }
            }
        });

        applyMigration(TESTS + "-to-" + testOccurrencesCache.getName(), () -> {
            IgniteCache<String, TestOccurrences> tests = ignite.cache(ignCacheNme(TESTS));
            if(tests==null)
                return;

            int size = tests.size();
            if (size > 0) {
                int i = 0;
                for (Cache.Entry<String, TestOccurrences> entry : tests) {
                    System.out.println("Migrating entry " + i + " from " + size + ": " + entry.getKey());

                    String transformedKey = removeCountFromRef(entry.getKey());
                    TestOccurrences val = entry.getValue();

                    if (testOccurrencesCache.putIfAbsent(transformedKey, val))
                        saveTestToStat.accept(val);
                    
                    i++;
                }

                tests.clear();

                tests.destroy();
            }
        });
        String newBuildsCache = BUILD_RESULTS + "-to-" + IgnitePersistentTeamcity.BUILDS + "V2";

        applyMigration("RemoveStatisticsFromBuildCache", ()->{
            if(doneMigrations.containsKey(newBuildsCache))
                return;

            final IgniteCache<Object, Object> cache = ignite.getOrCreateCache(ignCacheNme(BUILD_RESULTS));
            
            for (Cache.Entry<Object, Object> next : cache) {
                if(next.getValue() instanceof Statistics) {
                    System.err.println("Removed incorrect entity: Statistics from build cache");
                    
                    cache.remove(next.getKey());
                }
            }
        });

        applyMigration(newBuildsCache, () -> {
            IgniteCache<String, Build> oldBuilds = ignite.cache(ignCacheNme(BUILD_RESULTS));

            if (oldBuilds == null)
                return;

            int size = oldBuilds.size();
            if (size > 0) {
                int i = 0;
                for (Cache.Entry<String, Build> entry : oldBuilds) {
                    System.out.println("Migrating build entry " + i + " from " + size + ": " + entry.getKey());

                    Build val = entry.getValue();

                    if (buildCache.putIfAbsent(entry.getKey(), val))
                        saveBuildToStat.accept(val);

                    i++;
                }

                oldBuilds.clear();

                oldBuilds.destroy();
            }
        });

        applyMigration("RemoveBuildsWithoutProjectId", () -> {
            final IgniteCache<Object, Build> cache = ignite.getOrCreateCache(ignCacheNme(BUILD_RESULTS));

            for (Cache.Entry<Object, Build> next : cache) {
                Build results = next.getValue();
                //non fake builds but without required data
                if (results.getId() != null)
                    if (results.getBuildType() == null || results.getBuildType().getProjectId() == null) {
                        System.err.println("Removed incorrect entity: Build without filled parameters: " + next);

                        cache.remove(next.getKey());
                    }
            }
        });

        applyMigration("Remove-" + RUN_STAT_CACHE, ()->{
            IgniteCache<String, Build> oldBuilds = ignite.getOrCreateCache(ignCacheNme(RUN_STAT_CACHE));

            oldBuilds.clear();

            oldBuilds.destroy();
        });

        applyMigration("ReplaceKeyTypeOf-" + suiteHistCache.getName(), () -> {
            int i = 0;
            int size = suiteHistCache.size();

            for (Cache.Entry<?, RunStat> next : suiteHistCache) {
                Object key = next.getKey();

                if (key instanceof String) {
                    SuiteInBranch suiteKey = new SuiteInBranch((String)key, ITeamcity.DEFAULT);

                    suiteHistCache.put(suiteKey, next.getValue());
                    ((Cache)suiteHistCache).remove(key);

                    System.out.println("Migrating entry " + i + " from " + size + ": " + suiteKey);
                }

                i++;
            }
        });


        applyMigration("ReplaceKeyTypeOf-" + testHistCache.getName(), () -> {
            int i = 0;
            int size = testHistCache.size();

            for (Cache.Entry<?, RunStat> next : testHistCache) {
                Object key = next.getKey();

                if (key instanceof String) {
                    TestInBranch testKey = new TestInBranch((String)key, ITeamcity.DEFAULT);

                    testHistCache.put(testKey, next.getValue());
                    ((Cache)testHistCache).remove(key);

                    System.out.println("Migrating entry " + i + " from " + size + ": " + testKey);
                }

                i++;
            }
        });

        applyRemoveCache(GetTrackedBranchTestResults.ALL_TEST_FAILURES_SUMMARY);

        applyRemoveCache(GetTrackedBranchTestResults.TEST_FAILURES_SUMMARY_CACHE_NAME);
        applyRemoveCache(GetBuildTestFailures.TEST_FAILURES_SUMMARY_CACHE_NAME);
        applyRemoveCache(GetPrTestFailures.CURRENT_PR_FAILURES);

        applyMigration(TEST_OCCURRENCE_FULL + "-to-" + testFullCache.getName() + "V2", () -> {
            String cacheNme = ignCacheNme(TEST_OCCURRENCE_FULL);
            IgniteCache<String, TestOccurrenceFull> oldTestCache = ignite.cache(cacheNme);

            if (oldTestCache == null) {
                System.err.println("cache not found");

                return;
            }

            int size = oldTestCache.size();
            if (size > 0) {
                ignite.cluster().disableWal(testFullCache.getName());

                try {
                    int i = 0;

                    Map<String, TestOccurrenceFull> batch = new HashMap<>();
                    int maxFoundBuildId = 0;

                    IgniteDataStreamer<String, TestOccurrenceFull> streamer = ignite.dataStreamer(testFullCache.getName());

                    for (Cache.Entry<String, TestOccurrenceFull> entry : oldTestCache) {
                        String key = entry.getKey();

                        Integer buildId = RunStat.extractIdPrefixed(key, ",build:(id:", ")");

                        if (buildId != null) {
                            if (buildId > maxFoundBuildId)
                                maxFoundBuildId = buildId;

                            if (buildId < maxFoundBuildId - (RunStat.MAX_LATEST_RUNS * SUITES_CNT * 15))
                                logger.info(serverId + " - Skipping entry " + i + " from " + size + ": " + key);
                            else
                                batch.put(entry.getKey(), entry.getValue());
                        }


                        i++;

                        if (batch.size() >= 300)
                            saveOneBatch(cacheNme, size, i, batch, streamer);
                    }

                    if (!batch.isEmpty())
                        saveOneBatch(cacheNme, size, i, batch, streamer);

                    streamer.flush();

                    System.err.println("Removing data from old cache " + oldTestCache.getName());

                    oldTestCache.destroy();
                }
                finally {
                    ignite.cluster().enableWal(testFullCache.getName());
                }
            }
        });
        applyV1toV2Migration(PROBLEMS, problemsCache);
        applyV1toV2Migration(FINISHED_BUILDS, buildHistCache);
        applyV1toV2Migration(FINISHED_BUILDS_INCLUDE_FAILED, buildHistInFailedCache);

        Cache<IssueKey, Issue> issuesCache = IssuesStorage.botDetectedIssuesCache(ignite);
        applyMigration(ISSUES + "-to-" + issuesCache.getName() + "V2", () -> {
            String cacheName = ISSUES;
            IgniteCache<IssueKey, Issue> issuesOldCache = ignite.getOrCreateCache(cacheName);

            int size = issuesOldCache.size();
            if (size > 0) {
                int i = 0;

                Map<IssueKey, Issue> batch = new HashMap<>();

                IgniteDataStreamer<IssueKey, Issue> streamer = ignite.dataStreamer(issuesCache.getName());
                for (Cache.Entry<IssueKey, Issue> entry : issuesOldCache) {
                    batch.put(entry.getKey(), entry.getValue());

                    i++;

                    if (batch.size() >= 300)
                        saveOneBatch(cacheName, size, i, batch, streamer);
                }

                if (!batch.isEmpty())
                    saveOneBatch(cacheName, size, i, batch, streamer);

                System.err.println("Removing data from old cache " + issuesOldCache.getName());

                issuesOldCache.destroy();
            }
        });

        applyMigration("latestRunResultsToLatestRuns", () -> {
            System.out.println("Total entry for migrate : " + testHistCache.size());
            int i = 0;
            for (Cache.Entry<?, RunStat> next : testHistCache) {
                TestInBranch key = (TestInBranch)next.getKey();
                RunStat value = next.getValue();

                value.migrateLatestRuns();

                testHistCache.put(key, value);

                if (i % 1000 == 0)
                    System.out.println("Migrating entry: count : " + i);

                i++;
            }
        });

        applyDestroyIgnCacheMigration(RUNNING_BUILDS);

        applyDestroyIgnCacheMigration(BUILD_QUEUE);

        applyDestroyIgnCacheMigration(FINISHED_BUILDS_INCLUDE_FAILED);
        applyDestroyIgnCacheMigration(TEST_OCCURRENCE_FULL);

        applyDestroyIgnCacheMigration(TESTS);
        applyDestroyIgnCacheMigration(STAT);
        applyDestroyIgnCacheMigration(BUILD_STATISTICS);
        applyDestroyCacheMigration(BUILD_CONDITIONS_CACHE_NAME, BUILD_CONDITIONS_CACHE_NAME);
        applyDestroyCacheMigration(TEAMCITY_BUILD_CACHE_NAME_OLD, TEAMCITY_BUILD_CACHE_NAME_OLD);

        applyDestroyIgnCacheMigration(CHANGE_INFO_FULL);
        applyDestroyIgnCacheMigration(CHANGES_LIST);
    }

    private void applyDestroyIgnCacheMigration(String cacheName) {
        String ignCacheNme = ignCacheNme(cacheName);
        applyDestroyCacheMigration(cacheName, ignCacheNme);
    }

    private void applyDestroyCacheMigration(String dispCacheName, String cacheNme) {
        applyMigration("destroy-" + dispCacheName, () -> {
            IgniteCache<Object, Object> cache = ignite.cache(cacheNme);

            if (cache == null) {
                System.err.println("cache [" + cacheNme + "] not found");

                return;
            }

            cache.destroy();
        });
    }

    private <K, V> void applyV1toV2Migration(String full, Cache<K, V> cache) {
        applyMigration(full + "-to-" + cache.getName() + "V2", () -> {
            v1tov2cacheMigrate(full, cache);
        });
    }

    private <K, V> IgniteCache<K, V> getOrCreateIgnCacheV1(String name) {
        return ignite.getOrCreateCache(ignCacheNme(name));
    }

    private <K,V> void v1tov2cacheMigrate(String deprecatedCache, Cache<K, V> newCache) {
        String cacheNme = ignCacheNme(deprecatedCache);
        IgniteCache<K, V> tests = ignite.cache(cacheNme);

        if (tests == null) {
            System.err.println("Cache not found: " + cacheNme);

            return;
        }

        int size = tests.size();
        if (size > 0) {
            ignite.cluster().disableWal(newCache.getName());

            try {
                int i = 0;

                Map<K, V> batch = new HashMap<>();

                IgniteDataStreamer<K, V> streamer = ignite.dataStreamer(newCache.getName());

                for (Cache.Entry<K, V> entry : tests) {
                    batch.put(entry.getKey(), entry.getValue());

                    i++;

                    if (batch.size() >= 300)
                        saveOneBatch(cacheNme, size, i, batch, streamer);
                }

                if (!batch.isEmpty())
                    saveOneBatch(cacheNme, size, i, batch, streamer);

                streamer.flush();
                System.err.println("Removing data from old cache " + tests.getName());

                tests.destroy();
            }
            finally {
                ignite.cluster().enableWal(newCache.getName());
            }
        }
    }

    /**
     * @param cacheNme Cache name.
     * @param size overall size of cache.
     * @param i Processed count.
     * @param batch Batch.
     * @param streamer
     */
    private <K, V> void saveOneBatch(String cacheNme, int size,
        int i,
        Map<K, V> batch, IgniteDataStreamer<K, V> streamer) {
        K key = batch.keySet().iterator().next();
        String msg = "Migrating " + cacheNme + " " + batch.size() + " entries." +
            " Processed " + i + " from " + size + ": One entry key " + key;
        System.out.println(msg);
        logger.info(msg);

        streamer.addData(batch);

        batch.clear();
    }

    /**
     * @param cacheNme Cache nme.
     */
    private void applyRemoveCache(String cacheNme) {
        applyMigration("remove" + cacheNme, () -> {
            if (ignite.cacheNames().contains(cacheNme)) {
                IgniteCache<String, Build> oldBuilds = ignite.getOrCreateCache(cacheNme);

                oldBuilds.clear();

                oldBuilds.destroy();
            }
        });
    }

    private IgniteCache<String, Object> doneMigrationsCache() {
        String migrations = ignCacheNme(DONE_MIGRATIONS);
        CacheConfiguration<String, Object> ccfg = new CacheConfiguration<>(migrations);
        ccfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
        ccfg.setCacheMode(CacheMode.REPLICATED);

        return ignite.getOrCreateCache(ccfg);
    }

    private void applyMigration(String code, Runnable runnable) {
        if (doneMigrations.containsKey(code))
            return;

        synchronized (DbMigrations.class) {
            if (doneMigrations.containsKey(code))
                return;

            String msg = "Running migration procedure [" + code + "]";
            System.err.println(msg);
            logger.warn(msg);

            runnable.run();

            doneMigrations.put(code, true);

            String msgComp = "Completed migration procedure [" + code + "]";
            System.err.println(msgComp);
            logger.warn(msgComp);
        }
    }

    private String ignCacheNme(String tests) {
        return IgnitePersistentTeamcity.ignCacheNme(tests, serverId);
    }
}
