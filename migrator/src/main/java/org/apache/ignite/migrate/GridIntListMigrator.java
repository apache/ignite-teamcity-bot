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

package org.apache.ignite.migrate;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Offline migrator for TeamCity Bot Ignite persistence.
 * <p>
 * Recursively scans all entries in Ignite caches and replaces any occurrence of the legacy type
 * org.apache.ignite.internal.util.GridIntList with the new type
 * org.apache.ignite.tcbot.common.util.GridIntList, preserving the int[] payload.
 * <p>
 * Usage:
 * export IGNITE_WORK_DIR=/abs/path/to/work_backup
 * ./gradlew -p migrator run --args="--verbose --report 200"      # dry run with verbose report
 * ./gradlew -p migrator run --args="--apply --report 500"        # apply to all caches
 */

public final class GridIntListMigrator {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(GridIntListMigrator.class);

    /**
     * Scan page size.
     */
    private static final int DEFAULT_PAGE_SIZE = 256;

    /**
     * Default constructor.
     */
    private GridIntListMigrator() {
    }

    /**
     * Boots a standalone Ignite node on the given work dir and executes migration.
     */
    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");

        MigratorArgs a = MigratorArgs.parse(args);

        if (a.workDir == null || a.workDir.isEmpty()) {
            String wd = System.getProperty("IGNITE_WORK_DIR");

            if (wd == null || wd.isEmpty())
                wd = System.getenv("IGNITE_WORK_DIR");

            a.workDir = wd;
        }

        if (a.workDir == null || a.workDir.isEmpty()) {
            log.error("IGNITE_WORK_DIR is not set. Use --workDir or set system/env variable.");

            System.exit(2);
        }

        File checkDir = new File(a.workDir);

        if (!checkDir.isDirectory()) {
            log.error("IGNITE_WORK_DIR is not a directory: {}", checkDir.getAbsolutePath());

            System.exit(2);
        }

        IgniteConfiguration cfg = new IgniteConfiguration()
            .setIgniteInstanceName("tcbot-migrator")
            .setWorkDirectory(a.workDir);

        String consistentId = detectConsistentId(a.workDir);

        if (consistentId != null) {
            cfg.setConsistentId(consistentId);

            log.info("Using consistentId={}", consistentId);
        }
        else
            log.warn("Couldnt detect consistentId.");

        DataStorageConfiguration ds = new DataStorageConfiguration();
        ds.getDefaultDataRegionConfiguration().setPersistenceEnabled(true);
        cfg.setDataStorageConfiguration(ds);

        BinaryConfiguration bcfg = new BinaryConfiguration();
        cfg.setBinaryConfiguration(bcfg);

        Ignition.setClientMode(false);

        try (Ignite ig = Ignition.start(cfg)) {
            ig.cluster().state(ClusterState.ACTIVE);

            Collection<String> cacheNames = new ArrayList<>(ig.cacheNames());

            if (a.cacheFilter != null && !a.cacheFilter.isEmpty())
                cacheNames.removeIf(n -> !n.contains(a.cacheFilter));

            log.info("Caches to scan: {}", cacheNames);

            Transformer transformer = new Transformer(a.verbose);

            for (String cacheName : cacheNames) {
                IgniteCache<Object, Object> rawCache = ig.cache(cacheName);

                if (rawCache == null)
                    continue;

                IgniteCache<Object, Object> c = rawCache.withKeepBinary();

                log.info("Scanning cache: {}", cacheName);

                ScanQuery<Object, Object> q = new ScanQuery<>();
                q.setPageSize(DEFAULT_PAGE_SIZE);

                AtomicLong scanned = new AtomicLong();
                AtomicLong updated = new AtomicLong();

                try (QueryCursor<Cache.Entry<Object, Object>> cur = c.query(q)) {
                    for (Cache.Entry<Object, Object> e : cur) {
                        try {
                            Object v = e.getValue();
                            TransformResult tr = transformer.transform(v, 0);

                            if (tr.changed) {
                                if (a.apply) {
                                    c.put(e.getKey(), tr.val);

                                    updated.incrementAndGet();
                                }
                                else if (a.verbose)
                                    log.info("DRY-RUN would update key={}", e.getKey());
                            }

                            long s = scanned.incrementAndGet();

                            if (s % a.reportEvery == 0)
                                log.info("Scanned={} updated={}", s, updated.get());
                        }
                        catch (Throwable t) {
                            log.warn("Entry migration failed, skipping. Cause: {}", t.toString());

                            scanned.incrementAndGet();
                        }
                    }
                }

                log.info("Done {}: scanned={} updated={}", cacheName, scanned.get(), updated.get());
            }

            log.info("Migration finished.");
        }
    }

    /**
     * Infers consistentId by reading first subdirectory under work/db.
     *
     * @param workDir path to Ignite work dir.
     * @return consistentId or null if not found.
     */
    private static String detectConsistentId(String workDir) {
        File dbDir = new File(workDir, "db");

        if (!dbDir.isDirectory())
            return null;

        File[] kids = dbDir.listFiles(File::isDirectory);

        if (kids == null || kids.length == 0)
            return null;

        return kids[0].getName();
    }

    /**
     * Class static logger.
     */
    public static Logger GetMigratorLogger() {
        return log;
    }
}