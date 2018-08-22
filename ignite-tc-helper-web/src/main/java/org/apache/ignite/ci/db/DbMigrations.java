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
import java.util.Map;
import java.util.function.Consumer;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.web.rest.Metrics;
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
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(DbMigrations.class);

    public static final String DONE_MIGRATIONS = "doneMigrations";
    @Deprecated
    public static final String TESTS = "tests";
    @Deprecated
    public static final String BUILD_RESULTS = "buildResults";
    public static final String TESTS_COUNT_7700 = ",count:7700";

    //V1 caches, 1024 parts
    @Deprecated
    public static final String RUN_STAT_CACHE = "runStat";

    @Deprecated
    public static final String TEST_OCCURRENCE_FULL = "testOccurrenceFull";

    @Deprecated
    public static final String PROBLEMS = "problems";

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
        IgniteCache<String, TestOccurrenceFull> testFullCache,
        IgniteCache<String, ProblemOccurrences> problemsCache) {

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

                        if (buildId < maxFoundBuildId - (RunStat.MAX_LATEST_RUNS * 100 * 3))
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
            IgniteCache<String, TestOccurrences> tests = getOrCreateIgnCacheV1(TESTS);

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
            IgniteCache<String, Build> oldBuilds = getOrCreateIgnCacheV1(BUILD_RESULTS);

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
        applyRemoveCache(Metrics.FAILURES_PUBLIC);
        applyRemoveCache(Metrics.FAILURES_PRIVATE);
        applyRemoveCache(GetTrackedBranchTestResults.TEST_FAILURES_SUMMARY_CACHE_NAME);
        applyRemoveCache(GetBuildTestFailures.TEST_FAILURES_SUMMARY_CACHE_NAME);
        applyRemoveCache(GetPrTestFailures.CURRENT_PR_FAILURES);

        applyMigration(TEST_OCCURRENCE_FULL + "-to-" + testFullCache.getName() + "V2", () -> {
            this.v1tov2cacheMigrate(TEST_OCCURRENCE_FULL, testFullCache);
        });

        applyMigration(PROBLEMS + "-to-" + problemsCache.getName() + "V2", () -> {
            this.v1tov2cacheMigrate(PROBLEMS, problemsCache);
        });
    }

    private <K, V> IgniteCache<K, V> getOrCreateIgnCacheV1(String name) {
        return ignite.getOrCreateCache(ignCacheNme(name));
    }

    private <T> void v1tov2cacheMigrate(String deprecatedCache, Cache<String, T> testFullCache) {
        String cacheNme = ignCacheNme(deprecatedCache);
        IgniteCache<String, T> tests = ignite.getOrCreateCache(cacheNme);

        int size = tests.size();
        if (size > 0) {
            int i = 0;

            Map<String, T> batch = new HashMap<>();

            for (Cache.Entry<String, T> entry : tests) {
                batch.put(entry.getKey(), entry.getValue());

                i++;

                if (batch.size() >= 300)
                    saveOneBatch(testFullCache, cacheNme, size, i, batch);
            }

            if (!batch.isEmpty())
                saveOneBatch(testFullCache, cacheNme, size, i, batch);

            tests.clear();

            tests.destroy();
        }
    }

    private <T> void saveOneBatch(Cache<String, T> testFullCache, String cacheNme, int size,
        int i,
        Map<String, T> batch) {
        String key = batch.keySet().iterator().next();
        String msg = "Migrating " + cacheNme + " " + batch.size() + " entries." +
            " Processed " + i + " from " + size + ": One entry key " + key;
        logger.info(msg);

        testFullCache.putAll(batch);

        batch.clear();
    }

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
