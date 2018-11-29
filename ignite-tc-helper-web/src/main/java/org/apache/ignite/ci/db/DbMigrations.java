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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.observer.CompactBuildsInfo;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.web.model.CompactContributionKey;
import org.apache.ignite.ci.web.model.CompactVisa;
import org.apache.ignite.ci.web.model.CompactVisaRequest;
import org.apache.ignite.ci.web.model.hist.VisasHistoryStorage;
import org.apache.ignite.ci.web.rest.build.GetBuildTestFailures;
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
    private static final String BUILD_RESULTS = "buildResults";

    private static final String BUILD_STATISTICS = "buildStatistics";

    private static final String BUILD_CONDITIONS_CACHE_NAME = "buildConditions";

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

    @Deprecated
    private static final String BUILD_HIST_FINISHED = "buildHistFinished";

    @Deprecated
    private static final String BUILD_HIST_FINISHED_OR_FAILED = "buildHistFinishedOrFailed";

    @Deprecated
    public static final String TEAMCITY_BUILD_CACHE_NAME_OLD = "teamcityBuild";

    /** */
    @Deprecated
    public static final String COMPACT_VISAS_HISTORY_CACHE_NAME = "compactVisasHistoryCache";

    interface Old {
        String TEST_FULL = "testFull";
        String BUILD_PROBLEMS = "buildProblems";
        String CHANGES_LIST = "changesList";
        String CHANGE_INFO_FULL = "changeInfoFull";
        String CURRENT_PR_FAILURES = "currentPrFailures";
        String CONFIGURATIONS = "configurations";
        String TESTS_OCCURRENCES = "testOccurrences";
        String TESTS = "tests";
        String TEST_REFS = "testRefs";
        String ISSUES_USAGES_LIST = "issuesUsagesList";

        /** Cache name.*/
        public static final String TEST_HIST_CACHE_NAME = "testRunHistV0";
        public static final String TEST_HIST_CACHE_NAME2 ="teamcityTestRunHistV0";

        /** Build Start time Cache name. */
        public static final String BUILD_START_TIME_CACHE_NAME = "buildStartTimeV0";
        public static final String BUILD_START_TIME_CACHE_NAME2 = "teamcityBuildStartTimeV0";

        /** Cache name.*/
        public static final String SUITE_HIST_CACHE_NAME = "teamcitySuiteRunHistV0";

    }

    private final Ignite ignite;
    private final String serverId;
    private IgniteCache<String, Object> doneMigrations;

    public DbMigrations(Ignite ignite, String srvId) {
        this.ignite = ignite;
        this.serverId = srvId;
    }

    public void dataMigration(
        Cache<String, Build> buildCache, Consumer<Build> saveBuildToStat,
        IgniteCache<SuiteInBranch, RunStat> suiteHistCache,
        IgniteCache<TestInBranch, RunStat> testHistCache,
        Cache<CompactContributionKey, List<CompactVisaRequest>> visasCache) {

        doneMigrations = doneMigrationsCache();

        applyMigration(COMPACT_VISAS_HISTORY_CACHE_NAME + "-to-" + VisasHistoryStorage.VISAS_CACHE_NAME, () -> {
            IgniteCache<Object, Object> cache = ignite.cache(COMPACT_VISAS_HISTORY_CACHE_NAME);
            if (cache == null) {
                System.err.println("Cache not found " + COMPACT_VISAS_HISTORY_CACHE_NAME);

                return;
            }

            IgniteCache<Object, Object> oldVisasCache = cache.withKeepBinary();

            if (Objects.isNull(oldVisasCache)) {
                System.out.println("Old cache [" + COMPACT_VISAS_HISTORY_CACHE_NAME + "] not found");

                return;
            }

            int size = oldVisasCache.size();

            int i = 0;

            for (IgniteCache.Entry<Object, Object> entry : oldVisasCache) {
                System.out.println("Migrating entry " + i++ + " from " + size);

                Collection<BinaryObject> binVisaReqs = null;
                Object val = entry.getValue();
                if (val instanceof List)
                    binVisaReqs = (Collection<BinaryObject>)val;
                else {
                    if (val instanceof Map)
                        binVisaReqs = ((Map<?, BinaryObject>)val).values();
                }

                if (binVisaReqs == null)
                    continue;

                List<CompactVisaRequest> compactVisaReqs = new ArrayList<>();

                CompactContributionKey compactKey = ((BinaryObject)entry.getKey()).deserialize();

                for (BinaryObject binVisaReq : binVisaReqs) {
                    CompactBuildsInfo compactInfo = ((BinaryObject)binVisaReq.field("compactInfo")).deserialize();

                    CompactVisa compactVisa = ((BinaryObject)binVisaReq.field("compactVisa")).deserialize();

                    compactVisaReqs.add(new CompactVisaRequest(compactVisa, compactInfo, false));
                }

                visasCache.put(compactKey, compactVisaReqs);
            }
        });

        applyMigration("InitialFillLatestRunsV3", () -> {});

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
        applyRemoveCache(Old.CURRENT_PR_FAILURES);

        applyDestroyIgnCacheMigration(TEST_OCCURRENCE_FULL);
        /*
            int size = oldTestCache.size();
            if (size > 0) {
                ignite.cluster().disableWal(testFullCache.getName());
                try {

                    oldTestCache.destroy();
                }
                finally {
                    ignite.cluster().enableWal(testFullCache.getName());
                }
            }
        });*/
        applyDestroyIgnCacheMigration(PROBLEMS);

        applyDestroyIgnCacheMigration(FINISHED_BUILDS_INCLUDE_FAILED);

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

        applyDestroyIgnCacheMigration(Old.TESTS);
        applyDestroyIgnCacheMigration(STAT);
        applyDestroyIgnCacheMigration(BUILD_STATISTICS);
        applyDestroyCacheMigration(BUILD_CONDITIONS_CACHE_NAME, BUILD_CONDITIONS_CACHE_NAME);
        applyDestroyCacheMigration(TEAMCITY_BUILD_CACHE_NAME_OLD, TEAMCITY_BUILD_CACHE_NAME_OLD);

        applyDestroyIgnCacheMigration(Old.CHANGE_INFO_FULL);
        applyDestroyIgnCacheMigration(Old.CHANGES_LIST);

        applyDestroyIgnCacheMigration(FINISHED_BUILDS);
        applyDestroyIgnCacheMigration(BUILD_HIST_FINISHED);
        applyDestroyIgnCacheMigration(BUILD_HIST_FINISHED_OR_FAILED);

        applyDestroyCacheMigration(COMPACT_VISAS_HISTORY_CACHE_NAME, COMPACT_VISAS_HISTORY_CACHE_NAME);

        applyDestroyIgnCacheMigration(Old.BUILD_PROBLEMS);
        applyDestroyIgnCacheMigration(Old.TEST_FULL);

        applyDestroyIgnCacheMigration(Old.CONFIGURATIONS);
        applyDestroyIgnCacheMigration(Old.TESTS_OCCURRENCES);
        applyDestroyIgnCacheMigration(Old.TEST_REFS);

        applyDestroyIgnCacheMigration(Old.ISSUES_USAGES_LIST);

        applyDestroyCacheMigration(Old.SUITE_HIST_CACHE_NAME);
        applyDestroyCacheMigration(Old.BUILD_START_TIME_CACHE_NAME);
        applyDestroyCacheMigration(Old.TEST_HIST_CACHE_NAME);
        applyDestroyCacheMigration(Old.TEST_HIST_CACHE_NAME2);
        applyDestroyCacheMigration(Old.BUILD_START_TIME_CACHE_NAME);
        applyDestroyCacheMigration(Old.BUILD_START_TIME_CACHE_NAME2);
    }

    private void applyDestroyIgnCacheMigration(String cacheName) {
        String ignCacheNme = ignCacheNme(cacheName);
        applyDestroyCacheMigration(cacheName, ignCacheNme);
    }

    private void applyDestroyCacheMigration(String cacheNme) {
        applyDestroyCacheMigration(cacheNme, cacheNme);
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
                IgniteCache<Object, Object> oldBuilds = ignite.cache(cacheNme);

                if (oldBuilds == null)
                    return;

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
