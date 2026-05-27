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
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.tcbot.engine.issue.IssuesStorage;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcservice.model.result.Build;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.ignite.migrate.GridIntListMigrator;

import javax.cache.Cache;
import java.util.HashMap;
import java.util.List;
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

    /** Build refs SQL table name. */
    private static final String BUILD_REF_TABLE = "BUILDREFCOMPACTED";

    /** Index for suite history lookups inside a branch. */
    private static final String BUILD_REF_BRANCH_BUILD_TYPE_ID_IDX = "BUILDREFCOMPACTED_BRANCH_BUILD_TYPE_ID_IDX";

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
        //V1 caches, 1024 parts
        String RUN_STAT_CACHE = "runStat";
        String TEST_OCCURRENCE_FULL = "testOccurrenceFull";
        String PROBLEMS = "problems";
        String STAT = "stat";
        String FINISHED_BUILDS = "finishedBuilds";
        String FINISHED_BUILDS_INCLUDE_FAILED = "finishedBuildsIncludeFailed";
        String ISSUES = "issues";
        String BUILD_HIST_FINISHED = "buildHistFinished";
        String BUILD_HIST_FINISHED_OR_FAILED = "buildHistFinishedOrFailed";
        String TEAMCITY_BUILD_CACHE_NAME_OLD = "teamcityBuild";
        String COMPACT_VISAS_HISTORY_CACHE_NAME = "compactVisasHistoryCache";

        public static final String TEST_HIST_CACHE_NAME_V2_0 = "teamcityTestRunHist";
        public static final String SUITE_HIST_CACHE_NAME_V2_0 = "teamcitySuiteRunHist";
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

        applyDestroyCacheMigration(Old.COMPACT_VISAS_HISTORY_CACHE_NAME);

        applyMigration("InitialFillLatestRunsV3", () -> {
        });

        applyRemoveCache(Old.CURRENT_PR_FAILURES);


        Cache<IssueKey, Issue> issuesCache = IssuesStorage.botDetectedIssuesCache(ignite);
        applyMigration(Old.ISSUES + "-to-" + issuesCache.getName() + "V2", () -> {
            String cacheName = Old.ISSUES;
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
        applyDestroyCacheMigration(Old.TEAMCITY_BUILD_CACHE_NAME_OLD, Old.TEAMCITY_BUILD_CACHE_NAME_OLD);



        applyDestroyCacheMigration(Old.COMPACT_VISAS_HISTORY_CACHE_NAME, Old.COMPACT_VISAS_HISTORY_CACHE_NAME);


        applyDestroyCacheMigration(Old.SUITE_HIST_CACHE_NAME);
        applyDestroyCacheMigration(Old.BUILD_START_TIME_CACHE_NAME);
        applyDestroyCacheMigration(Old.TEST_HIST_CACHE_NAME);
        applyDestroyCacheMigration(Old.TEST_HIST_CACHE_NAME2);
        applyDestroyCacheMigration(Old.BUILD_START_TIME_CACHE_NAME);
        applyDestroyCacheMigration(Old.BUILD_START_TIME_CACHE_NAME2);

        for (String srvId : serverIds) {
            if(!DONE_MIGRATION_PREFIX.equals(srvId))
                applyDestroyIgnCacheMigration(DONE_MIGRATIONS, srvId);

            applyMigration("Remove-" + Old.RUN_STAT_CACHE, () -> {
                IgniteCache<String, Build> oldBuilds = ignite.getOrCreateCache(ignCacheNme(Old.RUN_STAT_CACHE, srvId));

                oldBuilds.clear();

                oldBuilds.destroy();
            });

            applyDestroyIgnCacheMigration(Old.TEST_OCCURRENCE_FULL, srvId);

            applyDestroyIgnCacheMigration(Old.PROBLEMS, srvId);

            applyDestroyIgnCacheMigration(Old.FINISHED_BUILDS_INCLUDE_FAILED, srvId);
            applyDestroyIgnCacheMigration(RUNNING_BUILDS, srvId);

            applyDestroyIgnCacheMigration(BUILD_QUEUE, srvId);

            applyDestroyIgnCacheMigration(Old.FINISHED_BUILDS_INCLUDE_FAILED, srvId);
            applyDestroyIgnCacheMigration(Old.TEST_OCCURRENCE_FULL, srvId);

            applyDestroyIgnCacheMigration(Old.TESTS, srvId);
            applyDestroyIgnCacheMigration(Old.STAT, srvId);
            applyDestroyIgnCacheMigration(BUILD_STATISTICS, srvId);

            applyDestroyIgnCacheMigration(Old.CHANGE_INFO_FULL, srvId);
            applyDestroyIgnCacheMigration(Old.CHANGES_LIST, srvId);

            applyDestroyIgnCacheMigration(Old.FINISHED_BUILDS, srvId);
            applyDestroyIgnCacheMigration(Old.BUILD_HIST_FINISHED, srvId);
            applyDestroyIgnCacheMigration(Old.BUILD_HIST_FINISHED_OR_FAILED, srvId);
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

        applyDestroyCacheMigration("issues");
        applyDestroyCacheMigration("digestHist");

        applyDestroyCacheMigration(Old.TEST_HIST_CACHE_NAME_V2_0);
        applyDestroyCacheMigration(Old.SUITE_HIST_CACHE_NAME_V2_0);

        applyBuildRefHistoryQuerySchemaMigration();
        applyGridIntListMigration();

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

    /**
     * Applies the GridIntList migration from ignite.internal to tcbot-common realization
     */
    private void applyGridIntListMigration() {
        applyMigration("migrate-GridIntList", () -> {
            try {
                logger.info("Starting GridIntList type migration");

                String cacheFilter = null;
                boolean apply = true;
                boolean verbose = false;
                int reportEvery = 50000;

                long updated = GridIntListMigrator.migrateOnInstance(
                    ignite,
                    cacheFilter,
                    apply,
                    verbose,
                    reportEvery
                );

                logger.info("GridIntList migration completed. Updated {} entries", updated);

            }
            catch (Exception e) {
                logger.error("GridIntList migration failed", e);

                throw new RuntimeException("GridIntList migration failed", e);
            }
        });
    }

    /**
     * Adds SQL fields and an index required by suite/branch build-ref history queries.
     */
    private void applyBuildRefHistoryQuerySchemaMigration() {
        applyMigration("add-BuildRef-suite-branch-history-index", () -> {
            IgniteCache<Object, Object> cache = ignite.cache(BuildRefDao.TEAMCITY_BUILD_CACHE_NAME);

            if (cache == null) {
                logger.info("BuildRef cache [{}] does not exist yet, skipping SQL schema migration",
                    BuildRefDao.TEAMCITY_BUILD_CACHE_NAME);

                return;
            }

            if (!sqlTableExists(cache, BUILD_REF_TABLE)) {
                logger.info("BuildRef SQL table [{}] does not exist yet, skipping SQL schema migration",
                    BUILD_REF_TABLE);

                return;
            }

            if (!sqlColumnExists(cache, BUILD_REF_TABLE, "BUILDTYPEID"))
                sqlDdl(cache, "ALTER TABLE " + BUILD_REF_TABLE + " ADD COLUMN buildTypeId INT");

            if (!sqlColumnExists(cache, BUILD_REF_TABLE, "ID"))
                sqlDdl(cache, "ALTER TABLE " + BUILD_REF_TABLE + " ADD COLUMN id INT");

            if (!sqlIndexExists(cache, BUILD_REF_BRANCH_BUILD_TYPE_ID_IDX)) {
                sqlDdl(cache, "CREATE INDEX " + BUILD_REF_BRANCH_BUILD_TYPE_ID_IDX + " ON " + BUILD_REF_TABLE
                    + " (branchName, buildTypeId, id)");
            }
        });
    }

    /**
     * @param cache Cache.
     * @param tableName SQL table name.
     */
    private boolean sqlTableExists(IgniteCache<?, ?> cache, String tableName) {
        return sqlCount(cache, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?", tableName) > 0;
    }

    /**
     * @param cache Cache.
     * @param tableName SQL table name.
     * @param columnName SQL column name.
     */
    private boolean sqlColumnExists(IgniteCache<?, ?> cache, String tableName, String columnName) {
        return sqlCount(cache, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
            tableName, columnName) > 0;
    }

    /**
     * @param cache Cache.
     * @param indexName SQL index name.
     */
    private boolean sqlIndexExists(IgniteCache<?, ?> cache, String indexName) {
        return sqlCount(cache, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME = ?", indexName) > 0;
    }

    /**
     * @param cache Cache.
     * @param sql SQL query.
     * @param args Query arguments.
     */
    private long sqlCount(IgniteCache<?, ?> cache, String sql, Object... args) {
        try (QueryCursor<List<?>> cur = cache.query(new SqlFieldsQuery(sql).setArgs(args))) {
            return ((Number)cur.getAll().get(0).get(0)).longValue();
        }
    }

    /**
     * @param cache Cache.
     * @param sql SQL DDL.
     */
    private void sqlDdl(IgniteCache<?, ?> cache, String sql) {
        try (QueryCursor<List<?>> ignored = cache.query(new SqlFieldsQuery(sql))) {
            // No-op.
        }
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
