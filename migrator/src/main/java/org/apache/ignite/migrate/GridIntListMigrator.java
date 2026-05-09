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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
 * ./gradlew -p migrator run --args="--apply --report 50000"      # apply to all caches
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
     * Failed entries printed to the final diagnostic report.
     */
    private static final int FAILURE_DETAILS_LIMIT = 100;

    /**
     * Time to wait for operator input before automatic repair starts.
     */
    private static final long AUTO_REPAIR_WAIT_SECONDS = 60;

    /**
     * Test/support override for the auto-repair wait time.
     */
    static final String AUTO_REPAIR_WAIT_MILLIS_PROPERTY = "gridintlist.migration.autorepair.wait.millis";

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

            long updated = migrateOnInstance(
                ig,
                a.cacheFilter,
                a.apply,
                a.verbose,
                a.reportEvery
            );

            log.info("Migration finished. Total updated: {}", updated);
        }
    }

    /**
     * Perform migration on existing Ignite instance
     * @param ignite Ignite instance
     * @param cacheFilter cache name filter
     * @param apply true to apply changes, false to dry-run
     * @param verbose logging
     * @param reportEvery frequency of reports
     * @return number of updated records
     */
    public static long migrateOnInstance(Ignite ignite,
        String cacheFilter,
        boolean apply,
        boolean verbose,
        int reportEvery) {
        Collection<String> cacheNames = new ArrayList<>(ignite.cacheNames());

        if (cacheFilter != null && !cacheFilter.isEmpty())
            cacheNames.removeIf(n -> !n.contains(cacheFilter));

        log.info("GridIntList migration - Caches to scan: {}", cacheNames);

        Transformer transformer = new Transformer(verbose);
        long totalUpdated = 0;
        long totalFailed = 0;
        long totalScanned = 0;
        List<MigrationFailure> failureDetails = new ArrayList<>();

        if (reportEvery <= 0)
            reportEvery = 1;

        for (String cacheName : cacheNames) {
            IgniteCache<Object, Object> rawCache = ignite.cache(cacheName);

            if (rawCache == null)
                continue;

            IgniteCache<Object, Object> c = rawCache.withKeepBinary();

            log.info("GridIntList migration - Scanning cache: {}", cacheName);

            ScanQuery<Object, Object> q = new ScanQuery<>();
            q.setPageSize(DEFAULT_PAGE_SIZE);

            AtomicLong scanned = new AtomicLong();
            AtomicLong updated = new AtomicLong();
            AtomicLong failed = new AtomicLong();

            try (QueryCursor<Cache.Entry<Object, Object>> cur = c.query(q)) {
                for (Cache.Entry<Object, Object> e : cur) {
                    Object key = null;
                    Object val = null;
                    String valType = "not-read";

                    try {
                        key = e.getKey();
                        val = e.getValue();
                        valType = typeName(val);

                        TransformResult tr = transformer.transform(val, 0);

                        if (tr.changed) {
                            if (apply) {
                                c.put(key, tr.val);

                                updated.incrementAndGet();
                            }
                            else if (verbose)
                                log.info("DRY-RUN would update key={}", key);
                        }
                    }
                    catch (Throwable t) {
                        failed.incrementAndGet();

                        MigrationFailure failure = new MigrationFailure(cacheName, typeName(key), valType,
                            safeToString(key), safeToString(val), key, failureMessage(t));

                        failureDetails.add(failure);

                        if (verbose)
                            log.warn("GridIntList entry migration failed: {}", failure, t);
                    }
                    finally {
                        long s = scanned.incrementAndGet();
                        long globalScanned = totalScanned + s;
                        long globalUpdated = totalUpdated + updated.get();
                        long globalFailed = totalFailed + failed.get();

                        if (globalScanned % reportEvery == 0) {
                            logProgress(cacheName, s, updated.get(), failed.get(), globalScanned, globalUpdated,
                                globalFailed);
                        }
                    }
                }
            }

            log.info("Done {}: scanned={} updated={} failed={}", cacheName, scanned.get(), updated.get(), failed.get());

            totalUpdated += updated.get();
            totalFailed += failed.get();
            totalScanned += scanned.get();
        }

        logProgress("all caches", totalScanned, totalUpdated, totalFailed, totalScanned, totalUpdated, totalFailed);

        if (totalFailed > 0) {
            String failureSummary = failureSummary(totalFailed, failureDetails);

            System.err.println(failureSummary);
            log.error(failureSummary);

            RecoveryDump dump = dumpFailedEntriesSafely(ignite, failureDetails);

            if (tryAutoRepair(ignite, failureDetails, dump)) {
                log.info("GridIntList migration auto-repair completed. Deleted failed entries: {}", totalFailed);

                return totalUpdated;
            }

            throw new IllegalStateException("GridIntList migration failed for " + totalFailed +
                " entries. Migration marker will not be written.\n" +
                failureSummary);
        }

        log.info("GridIntList migration finished. Total scanned: {}. Total updated: {}", totalScanned, totalUpdated);

        return totalUpdated;
    }

    /**
     * Logs progress to both application log and console. Console output is intentional because web-app startup may look
     * stuck while the persistent cache scan is still moving.
     *
     * @param cacheName Current cache name.
     * @param scanned Entries scanned in current cache.
     * @param updated Entries updated in current cache.
     * @param failed Entries failed in current cache.
     * @param totalScanned Entries scanned in all caches.
     * @param totalUpdated Entries updated in all caches.
     * @param totalFailed Entries failed in all caches.
     */
    private static void logProgress(String cacheName, long scanned, long updated, long failed, long totalScanned,
        long totalUpdated, long totalFailed) {
        String msg = "GridIntList migration progress [cache=" + cacheName
            + ", scanned=" + scanned
            + ", updated=" + updated
            + ", failed=" + failed
            + ", totalScanned=" + totalScanned
            + ", totalUpdated=" + totalUpdated
            + ", totalFailed=" + totalFailed + "]";

        System.err.println(msg);
        log.info(msg);
    }

    /**
     * @param totalFailed Total failed entries.
     * @param failures Recorded failure samples.
     * @return Human-readable diagnostic report.
     */
    static String failureSummary(long totalFailed, List<MigrationFailure> failures) {
        StringBuilder sb = new StringBuilder();

        sb.append("Failed GridIntList migration entries");

        if (totalFailed <= FAILURE_DETAILS_LIMIT) {
            sb.append(" (all known entries are listed below):");
        }
        else {
            sb.append(" (first ").append(FAILURE_DETAILS_LIMIT).append(" of ").append(totalFailed)
                .append(" entries are listed below):");
        }

        for (int i = 0; i < Math.min(failures.size(), FAILURE_DETAILS_LIMIT); i++)
            sb.append(System.lineSeparator()).append(i + 1).append(". ").append(failures.get(i));

        if (failures.isEmpty())
            sb.append(System.lineSeparator()).append("No entry details were captured.");

        sb.append(System.lineSeparator()).append("Suggested actions:");
        sb.append(System.lineSeparator())
            .append("- If you want to repair these entries manually, stop this service now.");
        sb.append(System.lineSeparator())
            .append("- If the service is not stopped, this migrator will dump and remove all ")
            .append("failed entries automatically after the timeout.");
        sb.append(System.lineSeparator()).append("- This is safe for disposable derived caches such as ")
            .append("buildLogCheckResult: missing entries will be recalculated when needed.");
        sb.append(System.lineSeparator())
            .append("- If the data must be preserved, inspect the cache/key pair manually, ")
            .append("fix the value that matches the reason above, and rerun startup.");
        sb.append(System.lineSeparator()).append("- For deeper diagnostics run the offline migrator with --cache ")
            .append("<cache-name> --verbose --report 50000.");

        return sb.toString();
    }

    /**
     * Dumps failed entries and removes them after a short operator prompt.
     *
     * @param ignite Ignite instance.
     * @param failures Failed entries.
     * @param dump Recovery dump, if dump succeeded.
     * @return {@code true} if failed entries were removed.
     */
    private static boolean tryAutoRepair(Ignite ignite, List<MigrationFailure> failures, RecoveryDump dump) {
        if (failures.isEmpty())
            return false;

        waitBeforeAutoRepair();

        if (dump == null) {
            String msg = "GridIntList migration auto-repair aborted: failed entries were not dumped.";

            System.err.println(msg);
            log.error(msg);

            return false;
        }

        long removed = removeFailedEntries(ignite, failures);
        String msg = "GridIntList migration auto-repair removed " + removed + " entries. Recovery dump: " + dump;

        System.err.println(msg);
        log.warn(msg);

        return removed == failures.size();
    }

    /**
     * @param ignite Ignite instance.
     * @param failures Failed entries.
     * @return Recovery dump paths, or {@code null} if dump failed.
     */
    private static RecoveryDump dumpFailedEntriesSafely(Ignite ignite, List<MigrationFailure> failures) {
        try {
            RecoveryDump dump = dumpFailedEntries(ignite, failures);
            String msg = "GridIntList migration dumped failed entries. " + dump;

            System.err.println(msg);
            log.warn(msg);

            return dump;
        }
        catch (IOException e) {
            String msg = "GridIntList migration failed to dump failed entries.";

            System.err.println(msg + " " + e);
            log.error(msg, e);

            return null;
        }
    }

    /**
     * Waits before automatic repair. System.in is intentionally not consumed here: in the web launcher it is already
     * used as the service stop signal.
     */
    private static void waitBeforeAutoRepair() {
        String prompt = "GridIntList migration can dump and remove the listed failed entries. "
            + "If you want to repair them manually, stop this service now within "
            + autoRepairWaitSecondsForDisplay()
            + " seconds. If the service keeps running, auto-repair will start.";

        System.err.println(prompt);
        log.warn(prompt);

        try {
            Thread.sleep(autoRepairWaitMillis());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @return Auto-repair wait timeout in milliseconds.
     */
    private static long autoRepairWaitMillis() {
        return Long.getLong(AUTO_REPAIR_WAIT_MILLIS_PROPERTY, TimeUnit.SECONDS.toMillis(AUTO_REPAIR_WAIT_SECONDS));
    }

    /**
     * @return Auto-repair wait timeout in seconds for operator-facing messages.
     */
    private static long autoRepairWaitSecondsForDisplay() {
        return Math.max(1, (autoRepairWaitMillis() + 999) / 1000);
    }

    /**
     * @param ignite Ignite instance.
     * @param failures Failed entries.
     * @return Dump file path.
     */
    private static RecoveryDump dumpFailedEntries(Ignite ignite, List<MigrationFailure> failures) throws IOException {
        File workDir = igniteWorkDir(ignite);
        Path dumpDir = workDir.toPath().resolve("diagnostic").resolve("grid-int-list-migration-recovery");

        Files.createDirectories(dumpDir);

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String dumpName = "grid_int_list_migration_failed_" + ts;
        Path igniteDump = createIgniteDump(ignite, failures, dumpDir, dumpName);
        Path manifest = dumpDir.resolve(dumpName + "_manifest.jsonl");
        List<String> lines = new ArrayList<>();

        for (MigrationFailure failure : failures)
            lines.add(failure.toJsonLine());

        Files.write(manifest, lines, StandardCharsets.UTF_8);

        return new RecoveryDump(igniteDump, manifest);
    }

    /**
     * Creates a built-in Ignite dump for caches with failed entries.
     *
     * @param ignite Ignite instance.
     * @param failures Failed entries.
     * @param diagnosticDir Diagnostic directory.
     * @param dumpName Dump name.
     * @return Ignite dump path, if it was found and moved to diagnostics.
     */
    private static Path createIgniteDump(Ignite ignite, List<MigrationFailure> failures, Path diagnosticDir,
        String dumpName) throws IOException {
        Set<String> caches = new LinkedHashSet<>();

        for (MigrationFailure failure : failures)
            caches.add(failure.cacheName);

        try {
            ignite.snapshot().createDump(dumpName, caches).get();
        }
        catch (Throwable t) {
            throw new IOException("Unable to create Ignite dump " + dumpName + " for caches " + caches, t);
        }

        Path src = findIgniteDump(igniteWorkDir(ignite).toPath(), dumpName);

        if (src == null)
            return diagnosticDir.resolve(dumpName);

        Path dst = diagnosticDir.resolve(dumpName);

        if (!src.equals(dst)) {
            if (Files.exists(dst))
                deleteRecursively(dst);

            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }

        return dst;
    }

    /**
     * @param workDir Ignite work dir.
     * @param dumpName Dump name.
     * @return Found dump path or {@code null}.
     */
    private static Path findIgniteDump(Path workDir, String dumpName) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(workDir)) {
            return paths
                .filter(Files::isDirectory)
                .filter(path -> dumpName.equals(path.getFileName().toString()))
                .findFirst()
                .orElse(null);
        }
    }

    /**
     * @param path Path to delete.
     */
    private static void deleteRecursively(Path path) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(path)) {
            Iterator<Path> it = paths.sorted(Comparator.reverseOrder()).iterator();

            while (it.hasNext())
                Files.delete(it.next());
        }
    }

    /**
     * @param ignite Ignite instance.
     * @return Ignite work dir.
     */
    private static File igniteWorkDir(Ignite ignite) {
        String workDir = ignite.configuration().getWorkDirectory();

        if (workDir == null || workDir.isEmpty())
            workDir = ignite.configuration().getIgniteHome();

        return new File(workDir == null || workDir.isEmpty() ? "." : workDir);
    }

    /**
     * @param ignite Ignite instance.
     * @param failures Failed entries.
     * @return Number of removed entries.
     */
    private static long removeFailedEntries(Ignite ignite, List<MigrationFailure> failures) {
        long removed = 0;

        for (MigrationFailure failure : failures) {
            IgniteCache<Object, Object> cache = ignite.cache(failure.cacheName);

            if (cache == null) {
                log.warn("GridIntList migration auto-repair: cache is not available, skip {}", failure);

                continue;
            }

            if (cache.withKeepBinary().remove(failure.keyObj))
                removed++;
            else
                log.warn("GridIntList migration auto-repair: entry was not removed {}", failure);
        }

        return removed;
    }

    /**
     * @param obj Object.
     * @return Base64 Java serialization, or short failure text.
     */
    private static String serializedBase64(Object obj) {
        if (obj == null)
            return "";

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(obj);
            }

            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
        catch (Throwable t) {
            return "<serialization failed: " + failureMessage(t) + ">";
        }
    }

    /**
     * @param val Value.
     * @return JSON-escaped value.
     */
    private static String json(String val) {
        if (val == null)
            return "";

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < val.length(); i++) {
            char ch = val.charAt(i);

            switch (ch) {
                case '\\':
                    sb.append("\\\\");
                    break;

                case '"':
                    sb.append("\\\"");
                    break;

                case '\n':
                    sb.append("\\n");
                    break;

                case '\r':
                    sb.append("\\r");
                    break;

                case '\t':
                    sb.append("\\t");
                    break;

                default:
                    sb.append(ch);
            }
        }

        return sb.toString();
    }

    /**
     * Recovery dump paths.
     */
    private static final class RecoveryDump {
        /** Built-in Ignite dump directory. */
        private final Path igniteDump;

        /** Failed entries manifest path. */
        private final Path manifest;

        /**
         * @param igniteDump Built-in Ignite dump directory.
         * @param manifest Failed entries manifest path.
         */
        private RecoveryDump(Path igniteDump, Path manifest) {
            this.igniteDump = igniteDump;
            this.manifest = manifest;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "igniteDump=" + igniteDump + ", manifest=" + manifest;
        }
    }

    /**
     * @param obj Object.
     * @return Type name safe for diagnostics.
     */
    private static String typeName(Object obj) {
        return obj == null ? "null" : obj.getClass().getName();
    }

    /**
     * @param obj Object.
     * @return String representation safe for diagnostics.
     */
    private static String safeToString(Object obj) {
        if (obj == null)
            return "null";

        try {
            return String.valueOf(obj);
        }
        catch (Throwable t) {
            return "<toString failed: " + failureMessage(t) + ">";
        }
    }

    /**
     * @param t Throwable.
     * @return Compact failure message.
     */
    private static String failureMessage(Throwable t) {
        String msg = t.getMessage();

        return t.getClass().getName() + (msg == null || msg.isEmpty() ? "" : ": " + msg);
    }

    /**
     * Compact failed-entry diagnostics.
     */
    static final class MigrationFailure {
        /** Cache name. */
        private final String cacheName;

        /** Key type name. */
        private final String keyType;

        /** Value type name. */
        private final String valueType;

        /** Safe key text. */
        private final String key;

        /** Safe value text. */
        private final String value;

        /** Original key object. */
        private final Object keyObj;

        /** Failure reason. */
        private final String reason;

        /**
         * @param cacheName Cache name.
         * @param keyType Key type name.
         * @param valueType Value type name.
         * @param key Safe key text.
         * @param value Safe value text.
         * @param keyObj Original key object.
         * @param reason Failure reason.
         */
        MigrationFailure(String cacheName, String keyType, String valueType, String key, String value, Object keyObj,
            String reason) {
            this.cacheName = cacheName;
            this.keyType = keyType;
            this.valueType = valueType;
            this.key = key;
            this.value = value;
            this.keyObj = keyObj;
            this.reason = reason;
        }

        /**
         * @return JSON line for recovery dump.
         */
        private String toJsonLine() {
            return "{"
                + "\"cache\":\"" + json(cacheName) + "\","
                + "\"key\":\"" + json(key) + "\","
                + "\"keyType\":\"" + json(keyType) + "\","
                + "\"keySerializedBase64\":\"" + json(serializedBase64(keyObj)) + "\","
                + "\"valueType\":\"" + json(valueType) + "\","
                + "\"value\":\"" + json(value) + "\","
                + "\"reason\":\"" + json(reason) + "\""
                + "}";
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "cache=" + cacheName
                + ", key=" + key
                + ", keyType=" + keyType
                + ", valueType=" + valueType
                + ", reason=" + reason;
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
