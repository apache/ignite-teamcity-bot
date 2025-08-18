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

package migrate;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
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
import java.lang.reflect.Constructor;
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
     * Fully-qualified name of the legacy type to replace.
     */
    private static final String OLD_KEYS_TYPE = "org.apache.ignite.internal.util.GridIntList";

    /**
     * Fully-qualified name of the new type.
     */
    private static final String NEW_KEYS_TYPE = "org.apache.ignite.tcbot.common.util.GridIntList";

    /**
     * Scan page size.
     */
    private static final int DEFAULT_PAGE_SIZE = 256;

    /**
     * Recursion guard to prevent accidental cycles.
     */
    private static final int MAX_DEPTH = 32;

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

        Args a = Args.parse(args);

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
     * Result of a recursive transformation: value and flag indicating changes.
     */
    private static final class TransformResult {
        /** Value. */
        final Object val;

        /** Changed. */
        final boolean changed;

        private TransformResult(Object val, boolean changed) {
            this.val = val;
            this.changed = changed;
        }

        static TransformResult same(Object v) {
            return new TransformResult(v, false);
        }

        static TransformResult changed(Object v) {
            return new TransformResult(v, true);
        }
    }

    /**
     * Performs recursive transformation of values, replacing legacy GridIntList with the new type.
     * Guarded by a MAX_DEPTH to avoid accidental cycles.
     */
    private static final class Transformer {
        private final boolean verbose;

        /** Cached constructor of NEW_KEYS_TYPE(int[]) to avoid excessive reflection. */
        private final Constructor<?> newKeysCachedConstruct;

        Transformer(boolean verb) {
            verbose = verb;
            newKeysCachedConstruct = findNewKeysConstruct();
        }

        /**
         * Recursively transforms a value.
         * <p>
         * Rules:
         * - BinaryObject of OLD_KEYS_TYPE -> build NEW_KEYS_TYPE or fallback to int[].
         * - Java object of OLD_KEYS_TYPE -> same replacement.
         * - BinaryObject (other type) -> rebuild only if some field changed.
         * - List/Set/Map/Object[] -> rebuild container only if any element changed.
         * - Primitives/String/int[] -> unchanged.
         *
         * @param v     value to transform
         * @param depth recursion depth (fixed guard inside)
         * @return transform result with possibly new value and 'changed' flag
         */
        TransformResult transform(Object v, int depth) {
            if (v == null)
                return TransformResult.same(null);

            // Strict migration interruption
            if (depth > MAX_DEPTH) {
                String errMsg = String.format("Max depth %d reached; value type=%s, aborting migration.", MAX_DEPTH, v.getClass());

                throw new IllegalStateException(errMsg);
            }

            // Binary legacy type
            if (v instanceof BinaryObject) {
                BinaryObject bo = (BinaryObject)v;
                String typeName = bo.type().typeName();

                if (typeName.equals(OLD_KEYS_TYPE)) {
                    int[] ints = extractInts(bo);
                    Object newKeys = buildNewKeys(ints);

                    return TransformResult.changed(newKeys);
                }

                // Generic BinaryObject
                BinaryObjectBuilder bb = bo.toBuilder();
                boolean anyChanged = false;

                for (String oldChildField : bo.type().fieldNames()) {
                    // Transform nested fields
                    TransformResult childRes = transform(bo.field(oldChildField), depth + 1);

                    if (childRes.changed) {
                        bb.setField(oldChildField, childRes.val);

                        anyChanged = true;
                    }
                }

                // Rebuild only if some field changed
                return anyChanged ? TransformResult.changed(bb.build()) : TransformResult.same(v);
            }

            // Java legacy type
            if (v instanceof org.apache.ignite.internal.util.GridIntList) {
                org.apache.ignite.internal.util.GridIntList g = (org.apache.ignite.internal.util.GridIntList)v;
                int[] ints = new int[g.size()];

                for (int i = 0; i < ints.length; i++)
                    ints[i] = g.get(i);

                Object newKeys = buildNewKeys(ints);

                return TransformResult.changed(newKeys);
            }

            // Already new type
            if (v.getClass().getName().equals(NEW_KEYS_TYPE))
                return TransformResult.same(v);

            // Collections
            if (v instanceof List) {
                List<?> src = (List<?>)v;
                boolean anyChanged = false;

                List<Object> out = new ArrayList<>(src.size());

                for (Object listElem : src) {
                    TransformResult tr = transform(listElem, depth + 1);

                    out.add(tr.val);
                    anyChanged |= tr.changed;
                }

                return anyChanged ? TransformResult.changed(out) : TransformResult.same(v);
            }

            if (v instanceof Set) {
                Set<?> src = (Set<?>)v;
                boolean anyChanged = false;

                LinkedHashSet<Object> out = new LinkedHashSet<>(Math.max(16, (int)Math.ceil(src.size() / 0.75)));

                for (Object el : src) {
                    TransformResult tr = transform(el, depth + 1);

                    out.add(tr.val);
                    anyChanged |= tr.changed;
                }

                return anyChanged ? TransformResult.changed(out) : TransformResult.same(v);
            }

            if (v instanceof Map) {
                Map<?, ?> src = (Map<?, ?>)v;
                boolean anyChanged = false;

                LinkedHashMap<Object, Object> out = new LinkedHashMap<>(Math.max(16, (int)Math.ceil(src.size() / 0.75)));

                for (Map.Entry<?, ?> en : src.entrySet()) {
                    TransformResult k = transform(en.getKey(), depth + 1);
                    TransformResult val = transform(en.getValue(), depth + 1);

                    out.put(k.val, val.val);
                    anyChanged |= k.changed || val.changed;
                }

                return anyChanged ? TransformResult.changed(out) : TransformResult.same(v);
            }

            // Object[] arrays (non-primitive)
            if (v.getClass().isArray() && !v.getClass().getComponentType().isPrimitive()) {
                Object[] arr = (Object[])v;
                Object[] out = new Object[arr.length];

                boolean anyChanged = false;

                for (int i = 0; i < arr.length; i++) {
                    TransformResult tr = transform(arr[i], depth + 1);

                    out[i] = tr.val;
                    anyChanged |= tr.changed;
                }

                return anyChanged ? TransformResult.changed(out) : TransformResult.same(v);
            }

            // Primitives, String, int[] etc.: unchanged
            return TransformResult.same(v);
        }

        /**
         * Extracts int[] from legacy GridIntList BinaryObject.
         */
        private int[] extractInts(BinaryObject oldKeys) {
            try {
                Object obj = oldKeys.deserialize();

                if (obj instanceof org.apache.ignite.internal.util.GridIntList) {
                    org.apache.ignite.internal.util.GridIntList g = (org.apache.ignite.internal.util.GridIntList)obj;

                    int[] res = new int[g.size()];

                    for (int i = 0; i < res.length; i++)
                        res[i] = g.get(i);

                    return res;
                }
            }
            catch (Throwable t) {
                if (verbose)
                    log.info("Deserialize fallback: {}", t.toString());
            }

            // Hardcode ("arr") based on GridIntList fields
            Collection<String> childFields = oldKeys.type().fieldNames();
            if (childFields.contains("arr")) {
                int[] arr = oldKeys.field("arr");

                if (arr != null)
                    return arr;
            }

            log.warn("Can't extract ints from {} fields={}", oldKeys.type().typeName(), childFields);

            return new int[0]; // best effort fallback
        }

        /**
         * Builds an instance of the new GridIntList (org.apache.ignite.tcbot.common.util.GridIntList),
         * or falls back to int[] if the class is not on the classpath.
         */
        private Object buildNewKeys(int[] ints) {
            if (newKeysCachedConstruct != null) {
                try {
                    return newKeysCachedConstruct.newInstance((Object)ints);
                }
                catch (ReflectiveOperationException ignored) {
                    // fall through to fallback
                }
            }

            if (verbose)
                log.warn("NEW_KEYS_TYPE {} is not available; falling back to raw int[]", NEW_KEYS_TYPE);

            return ints; // best effort fallback
        }

        /**
         * Resolves constructor NEW_KEYS_TYPE(int[]) once to avoid reflection per entry.
         */
        private Constructor<?> findNewKeysConstruct() {
            try {
                Class<?> cls = Class.forName(NEW_KEYS_TYPE);

                return cls.getConstructor(int[].class);
            }
            catch (Throwable t) {
                if (verbose) {
                    log.warn("New keys type {} is not on classpath, will fallback to int[] (cause: {})",
                        NEW_KEYS_TYPE, t.toString());
                }

                return null;
            }
        }
    }

    /**
     * Command-line arguments.
     * <p>
     * Supported flags:
     * --apply           actually write changes (otherwise dry-run)
     * --verbose         more diagnostics
     * --cache <substr>  process only caches whose name contains given substring
     * --report <N>      progress log interval
     * --workDir <path>  path to work/ directory (overrides IGNITE_WORK_DIR)
     */
    static final class Args {
        boolean apply = false;
        boolean verbose = false;
        String cacheFilter = null;
        long reportEvery = 500;
        String workDir = null;

        static Args parse(String[] args) {
            Args cliArgs = new Args();

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--apply":
                        cliArgs.apply = true;
                        break;
                    case "--verbose":
                        cliArgs.verbose = true;
                        break;
                    case "--cache":
                        cliArgs.cacheFilter = args[++i];
                        break;
                    case "--report":
                        cliArgs.reportEvery = Long.parseLong(args[++i]);
                        break;
                    case "--workDir":
                        cliArgs.workDir = args[++i];
                        break;
                    default:
                        break;
                }
            }
            LoggerFactory.getLogger(GridIntListMigrator.class).info(
                "Args: apply={} verbose={} cacheFilter={} reportEvery={} workDir={}",
                cliArgs.apply, cliArgs.verbose, cliArgs.cacheFilter, cliArgs.reportEvery, cliArgs.workDir
            );

            return cliArgs;
        }
    }
}