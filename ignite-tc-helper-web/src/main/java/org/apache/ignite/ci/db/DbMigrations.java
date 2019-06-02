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

import com.google.common.collect.Sets;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.tcservice.model.result.Build;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Migrations to be applied to each TC related caches.
 */
public class DbMigrations {
    public static final String BUILD_QUEUE = "buildQueue";
    public static final String RUNNING_BUILDS = "runningBuilds";
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(DbMigrations.class);

    public static final String DONE_MIGRATIONS = "doneMigrations";

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
    public static final String DONE_MIGRATION_PREFIX = "apache";

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

        /** Cache name. */
        String TEST_HIST_CACHE_NAME = "testRunHistV0";
        String TEST_HIST_CACHE_NAME2 = "teamcityTestRunHistV0";

        /** Build Start time Cache name. */
        String BUILD_START_TIME_CACHE_NAME = "buildStartTimeV0";
        String BUILD_START_TIME_CACHE_NAME2 = "teamcityBuildStartTimeV0";

        /** Cache name. */
        String SUITE_HIST_CACHE_NAME = "teamcitySuiteRunHistV0";

        String CALCULATED_STATISTIC = "calculatedStatistic";

        String BUILDS = "builds";

        String BUILD_RESULTS = "buildResults";

        String BUILDS_FAILURE_RUN_STAT = "buildsFailureRunStat";

        String TESTS_RUN_STAT = "testsRunStat";

        //V2 caches, 32 parts (V1 caches were 1024 parts)
        String LOG_CHECK_RESULT = "logCheckResult";
    }

    private final Ignite ignite;
    private final Set<String> serverIds
            = Sets.newHashSet("apache", "public",
            "gg", "private", "gridgain", "gg4apache", "null");

    private IgniteCache<String, Object> doneMigrations;

    public DbMigrations(Ignite ignite ) {
        this.ignite = ignite;
    }

    public String dataMigration() {
        doneMigrations = doneMigrationsCache();

        int sizeBefore = doneMigrations.size();

        applyDestroyCacheMigration(COMPACT_VISAS_HISTORY_CACHE_NAME);

        applyMigration("InitialFillLatestRunsV3", () -> {
        });

        applyRemoveCache(Old.CURRENT_PR_FAILURES);


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

        applyDestroyCacheMigration(BUILD_CONDITIONS_CACHE_NAME, BUILD_CONDITIONS_CACHE_NAME);
        applyDestroyCacheMigration(TEAMCITY_BUILD_CACHE_NAME_OLD, TEAMCITY_BUILD_CACHE_NAME_OLD);



        applyDestroyCacheMigration(COMPACT_VISAS_HISTORY_CACHE_NAME, COMPACT_VISAS_HISTORY_CACHE_NAME);


        applyDestroyCacheMigration(Old.SUITE_HIST_CACHE_NAME);
        applyDestroyCacheMigration(Old.BUILD_START_TIME_CACHE_NAME);
        applyDestroyCacheMigration(Old.TEST_HIST_CACHE_NAME);
        applyDestroyCacheMigration(Old.TEST_HIST_CACHE_NAME2);
        applyDestroyCacheMigration(Old.BUILD_START_TIME_CACHE_NAME);
        applyDestroyCacheMigration(Old.BUILD_START_TIME_CACHE_NAME2);

        for (String srvId : serverIds) {
            if(!DONE_MIGRATION_PREFIX.equals(srvId))
                applyDestroyIgnCacheMigration(DONE_MIGRATIONS, srvId);

            applyMigration("Remove-" + RUN_STAT_CACHE, () -> {
                IgniteCache<String, Build> oldBuilds = ignite.getOrCreateCache(ignCacheNme(RUN_STAT_CACHE, srvId));

                oldBuilds.clear();

                oldBuilds.destroy();
            });

            applyDestroyIgnCacheMigration(TEST_OCCURRENCE_FULL, srvId);

            applyDestroyIgnCacheMigration(PROBLEMS, srvId);

            applyDestroyIgnCacheMigration(FINISHED_BUILDS_INCLUDE_FAILED, srvId);
            applyDestroyIgnCacheMigration(RUNNING_BUILDS, srvId);

            applyDestroyIgnCacheMigration(BUILD_QUEUE, srvId);

            applyDestroyIgnCacheMigration(FINISHED_BUILDS_INCLUDE_FAILED, srvId);
            applyDestroyIgnCacheMigration(TEST_OCCURRENCE_FULL, srvId);

            applyDestroyIgnCacheMigration(Old.TESTS, srvId);
            applyDestroyIgnCacheMigration(STAT, srvId);
            applyDestroyIgnCacheMigration(BUILD_STATISTICS, srvId);

            applyDestroyIgnCacheMigration(Old.CHANGE_INFO_FULL, srvId);
            applyDestroyIgnCacheMigration(Old.CHANGES_LIST, srvId);

            applyDestroyIgnCacheMigration(FINISHED_BUILDS, srvId);
            applyDestroyIgnCacheMigration(BUILD_HIST_FINISHED, srvId);
            applyDestroyIgnCacheMigration(BUILD_HIST_FINISHED_OR_FAILED, srvId);
            applyDestroyIgnCacheMigration(Old.BUILD_PROBLEMS, srvId);
            applyDestroyIgnCacheMigration(Old.TEST_FULL, srvId);

            applyDestroyIgnCacheMigration(Old.CONFIGURATIONS, srvId);
            applyDestroyIgnCacheMigration(Old.TESTS_OCCURRENCES, srvId);
            applyDestroyIgnCacheMigration(Old.TEST_REFS, srvId);

            applyDestroyIgnCacheMigration(Old.ISSUES_USAGES_LIST, srvId);
            applyDestroyIgnCacheMigration(Old.CALCULATED_STATISTIC, srvId);
            applyDestroyIgnCacheMigration(Old.BUILDS, srvId);
            applyDestroyIgnCacheMigration(Old.BUILD_RESULTS, srvId);
            applyDestroyIgnCacheMigration(Old.BUILDS_FAILURE_RUN_STAT, srvId);
            applyDestroyIgnCacheMigration(Old.TESTS_RUN_STAT, srvId);

            applyDestroyIgnCacheMigration(Old.LOG_CHECK_RESULT, srvId);
        }

        int sizeAfter = doneMigrations.size();
        return (sizeAfter - sizeBefore) + " Migrations done from " + sizeAfter;

    }

    private void applyDestroyIgnCacheMigration(String cacheName, String srvId) {
        String ignCacheNme = ignCacheNme(cacheName, srvId);
        applyDestroyCacheMigration(cacheName, ignCacheNme);
    }

    private void applyDestroyCacheMigration(String cacheNme) {
        applyDestroyCacheMigration(cacheNme, cacheNme);
    }

    private void applyDestroyCacheMigration(String dispCacheName, String cacheNme) {
        applyMigration("destroy-" + cacheNme, () -> {
            IgniteCache<Object, Object> cache = ignite.cache(cacheNme);

            if (cache == null) {
                System.err.println("cache [" + cacheNme + "] not found");

                return;
            }

            cache.destroy();
        });
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
        String migrations = ignCacheNme(DONE_MIGRATIONS, DONE_MIGRATION_PREFIX);
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

    @NotNull public static String ignCacheNme(String cache, String srvId) {
        return srvId + "." + cache;
    }

}
