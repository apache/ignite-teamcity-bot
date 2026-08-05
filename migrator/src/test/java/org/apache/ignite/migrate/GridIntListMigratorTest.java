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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteSnapshot;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryType;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteFuture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for GridIntList migration diagnostics.
 */
public class GridIntListMigratorTest {
    /** Production cache that may contain GridIntList. */
    private static final String FAT_BUILD = "teamcityFatBuild";

    /** Defect cache containing nested fat builds. */
    private static final String BOT_DETECTED_DEFECTS = "botDetectedDefects";

    /** Cache from the production failure. */
    private static final String BUILD_LOG_CHECK_RESULT = "buildLogCheckResult";

    /** Key from the production failure. */
    private static final long FAILED_BUILD_LOG_CHECK_RESULT_KEY = 6062419808021002488L;

    /** */
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Checks that failed-entry summary contains actionable cache/key diagnostics.
     */
    @Test public void failureSummaryContainsEntryDetails() {
        String summary = GridIntListMigrator.failureSummary(2, Arrays.asList(
            new GridIntListMigrator.MigrationFailure("cacheA", "java.lang.String",
                "org.apache.ignite.binary.BinaryObjectImpl", "key-1", "<value>", "key-1", "boom"),
            new GridIntListMigrator.MigrationFailure("cacheB", "java.lang.Long",
                "not-read", "42", "<value>", 42L, "read failed")
        ));

        assertTrue(summary.contains("stop this service now"));
        assertTrue(summary.contains("dump and remove all failed entries automatically"));
        assertTrue(summary.contains("cache=cacheA"));
        assertTrue(summary.contains("key=key-1"));
        assertTrue(summary.contains("valueType=org.apache.ignite.binary.BinaryObjectImpl"));
        assertTrue(summary.contains("reason=read failed"));
    }

    /**
     * Checks that failures from later caches are not hidden by many earlier failures.
     */
    @Test public void failureSummaryContainsExamplesFromEachFailedCache() {
        java.util.List<GridIntListMigrator.MigrationFailure> failures = new java.util.ArrayList<>();

        for (int i = 0; i < 10; i++) {
            failures.add(new GridIntListMigrator.MigrationFailure("cacheA", "java.lang.Long",
                "valueTypeA", "key-a-" + i, "<value>", i, "boom-a-" + i));
        }

        failures.add(new GridIntListMigrator.MigrationFailure("cacheB", "java.lang.Long",
            "valueTypeB", "key-b-1", "<value>", 100L, "boom-b"));

        String summary = GridIntListMigrator.failureSummary(failures.size(), failures);

        assertTrue(summary.contains("cache=cacheA, failedEntries=10, shown=5"));
        assertTrue(summary.contains("cache=cacheB, failedEntries=1, shown=1"));
        assertTrue(summary.contains("key=key-b-1"));
        assertTrue(summary.contains("5 more failed entries in this cache"));
    }

    /**
     * Checks the recovery path where a migrated cache value cannot resolve binary type metadata.
     */
    @Test public void migrationDumpsAndRemovesEntryWithMissingBinaryTypeDetails() throws Exception {
        System.setProperty(GridIntListMigrator.AUTO_REPAIR_WAIT_MILLIS_PROPERTY, "1");

        try {
            Long failedKey = 6062419808021002488L;
            Ignite ignite = mock(Ignite.class);
            IgniteCache<Object, Object> rawCache = mock(IgniteCache.class);
            IgniteCache<Object, Object> binCache = mock(IgniteCache.class);
            QueryCursor<Cache.Entry<Object, Object>> cursor = mock(QueryCursor.class);
            IgniteSnapshot snapshot = mock(IgniteSnapshot.class);
            IgniteFuture<Void> dumpFut = mock(IgniteFuture.class);
            java.io.File workDir = tmp.newFolder("ignite-work");

            when(ignite.cacheNames()).thenReturn(Collections.singleton(FAT_BUILD));
            when(ignite.cache(FAT_BUILD)).thenReturn(rawCache);
            when(ignite.configuration()).thenReturn(new IgniteConfiguration()
                .setWorkDirectory(workDir.getAbsolutePath()));
            when(ignite.snapshot()).thenReturn(snapshot);
            when(snapshot.createDump(anyString(), anyCollection())).thenAnswer(invocation -> {
                String dumpName = invocation.getArgument(0);

                Files.createDirectories(workDir.toPath().resolve("snapshots").resolve(dumpName));

                return dumpFut;
            });
            when(rawCache.withKeepBinary()).thenReturn(binCache);
            when(binCache.query(any(ScanQuery.class))).thenReturn(cursor);
            when(cursor.iterator()).thenReturn(Collections.<Cache.Entry<Object, Object>>singletonList(
                new TestEntry(failedKey, new BrokenBinaryObject())).iterator());
            when(binCache.remove(failedKey)).thenReturn(true);

            GridIntListMigrator.migrateOnInstance(ignite, null, true, false, 1);

            verify(binCache).remove(failedKey);

            Path dumpDir = new java.io.File(ignite.configuration().getWorkDirectory()).toPath()
                .resolve("diagnostic")
                .resolve("grid-int-list-migration-recovery");
            Path dump = Files.list(dumpDir)
                .filter(path -> path.getFileName().toString().endsWith("_manifest.jsonl"))
                .findFirst().orElseThrow(() ->
                new AssertionError("Recovery dump was not written"));
            String dumpText = new String(Files.readAllBytes(dump), StandardCharsets.UTF_8);

            assertTrue(dumpText.contains(FAT_BUILD));
            assertTrue(dumpText.contains(String.valueOf(failedKey)));
            assertTrue(dumpText.contains("Failed to get binary type details [typeId=-526400035]"));
        }
        finally {
            System.clearProperty(GridIntListMigrator.AUTO_REPAIR_WAIT_MILLIS_PROPERTY);
        }
    }

    /**
     * Checks that a diagnostic dump failure does not stop automatic migration recovery.
     */
    @Test public void migrationRemovesFailedEntryWhenDumpFails() throws Exception {
        System.setProperty(GridIntListMigrator.AUTO_REPAIR_WAIT_MILLIS_PROPERTY, "1");

        try {
            Long failedKey = 6062419808021002488L;
            Ignite ignite = mock(Ignite.class);
            IgniteCache<Object, Object> rawCache = mock(IgniteCache.class);
            IgniteCache<Object, Object> binCache = mock(IgniteCache.class);
            QueryCursor<Cache.Entry<Object, Object>> cursor = mock(QueryCursor.class);
            IgniteSnapshot snapshot = mock(IgniteSnapshot.class);
            java.io.File workDir = tmp.newFolder("ignite-work-dump-fails");

            when(ignite.cacheNames()).thenReturn(Collections.singleton(FAT_BUILD));
            when(ignite.cache(FAT_BUILD)).thenReturn(rawCache);
            when(ignite.configuration()).thenReturn(new IgniteConfiguration()
                .setWorkDirectory(workDir.getAbsolutePath()));
            when(ignite.snapshot()).thenReturn(snapshot);
            when(snapshot.createDump(anyString(), anyCollection())).thenThrow(new RuntimeException("dump failed"));
            when(rawCache.withKeepBinary()).thenReturn(binCache);
            when(binCache.query(any(ScanQuery.class))).thenReturn(cursor);
            when(cursor.iterator()).thenReturn(Collections.<Cache.Entry<Object, Object>>singletonList(
                new TestEntry(failedKey, new BrokenBinaryObject())).iterator());
            when(binCache.remove(failedKey)).thenReturn(true);

            GridIntListMigrator.migrateOnInstance(ignite, null, true, false, 1);

            verify(binCache).remove(failedKey);
        }
        finally {
            System.clearProperty(GridIntListMigrator.AUTO_REPAIR_WAIT_MILLIS_PROPERTY);
        }
    }

    /**
     * Checks that dry-run mode never removes failed entries.
     */
    @Test public void dryRunDumpsDiagnosticsButDoesNotAutoRepair() throws Exception {
        Long failedKey = 6062419808021002488L;
        Ignite ignite = mock(Ignite.class);
        IgniteCache<Object, Object> rawCache = mock(IgniteCache.class);
        IgniteCache<Object, Object> binCache = mock(IgniteCache.class);
        QueryCursor<Cache.Entry<Object, Object>> cursor = mock(QueryCursor.class);
        IgniteSnapshot snapshot = mock(IgniteSnapshot.class);
        IgniteFuture<Void> dumpFut = mock(IgniteFuture.class);
        java.io.File workDir = tmp.newFolder("ignite-work-dry-run");

        when(ignite.cacheNames()).thenReturn(Collections.singleton(FAT_BUILD));
        when(ignite.cache(FAT_BUILD)).thenReturn(rawCache);
        when(ignite.configuration()).thenReturn(new IgniteConfiguration()
            .setWorkDirectory(workDir.getAbsolutePath()));
        when(ignite.snapshot()).thenReturn(snapshot);
        when(snapshot.createDump(anyString(), anyCollection())).thenAnswer(invocation -> {
            String dumpName = invocation.getArgument(0);

            Files.createDirectories(workDir.toPath().resolve("snapshots").resolve(dumpName));

            return dumpFut;
        });
        when(rawCache.withKeepBinary()).thenReturn(binCache);
        when(binCache.query(any(ScanQuery.class))).thenReturn(cursor);
        when(cursor.iterator()).thenReturn(Collections.<Cache.Entry<Object, Object>>singletonList(
            new TestEntry(failedKey, new BrokenBinaryObject())).iterator());

        try {
            GridIntListMigrator.migrateOnInstance(ignite, null, false, false, 1);

            fail("Dry-run migration must fail after reporting diagnostics for failed entries");
        }
        catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("GridIntList migration failed for 1 entries"));
        }

        verify(binCache, never()).remove(failedKey);

        Path dumpDir = workDir.toPath().resolve("diagnostic").resolve("grid-int-list-migration-recovery");
        assertTrue("Dry-run should still write diagnostics", Files.isDirectory(dumpDir));
    }

    /**
     * Checks that unrelated caches are skipped by default, including the cache that exposed the production failure.
     */
    @Test public void migrationSkipsNonGridIntListCachesByDefault() {
        Ignite ignite = mock(Ignite.class);

        when(ignite.cacheNames()).thenReturn(Collections.singleton(BUILD_LOG_CHECK_RESULT));

        long updated = GridIntListMigrator.migrateOnInstance(ignite, null, true, false, 1);

        assertTrue("No entries should be updated in skipped caches", updated == 0);
        verify(ignite, never()).cache(BUILD_LOG_CHECK_RESULT);
    }

    /**
     * Checks that defect values with nested fat builds are included in the default migration.
     */
    @Test public void migrationScansBotDetectedDefectsByDefault() {
        Ignite ignite = mock(Ignite.class);
        IgniteCache<Object, Object> rawCache = mock(IgniteCache.class);
        IgniteCache<Object, Object> binCache = mock(IgniteCache.class);
        QueryCursor<Cache.Entry<Object, Object>> cursor = mock(QueryCursor.class);

        when(ignite.cacheNames()).thenReturn(Collections.singleton(BOT_DETECTED_DEFECTS));
        when(ignite.cache(BOT_DETECTED_DEFECTS)).thenReturn(rawCache);
        when(rawCache.withKeepBinary()).thenReturn(binCache);
        when(binCache.query(any(ScanQuery.class))).thenReturn(cursor);
        when(cursor.iterator()).thenReturn(Collections.<Cache.Entry<Object, Object>>emptyList().iterator());

        long updated = GridIntListMigrator.migrateOnInstance(ignite, null, true, false, 1);

        assertTrue("No entries should be updated in an empty cache", updated == 0);
        verify(ignite).cache(BOT_DETECTED_DEFECTS);
    }

    /**
     * Test cache entry.
     */
    private static class TestEntry implements Cache.Entry<Object, Object> {
        /** */
        private final Object key;

        /** */
        private final Object val;

        /**
         * @param key Key.
         * @param val Value.
         */
        private TestEntry(Object key, Object val) {
            this.key = key;
            this.val = val;
        }

        /** {@inheritDoc} */
        @Override public Object getKey() {
            return key;
        }

        /** {@inheritDoc} */
        @Override public Object getValue() {
            return val;
        }

        /** {@inheritDoc} */
        @Override public <T> T unwrap(Class<T> cls) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Binary object that reproduces missing binary metadata failure from a real persistent cache.
     */
    private static class BrokenBinaryObject implements BinaryObject {
        /** {@inheritDoc} */
        @Override public BinaryType type() {
            throw new BinaryObjectException("Failed to get binary type details [typeId=-526400035]");
        }

        /** {@inheritDoc} */
        @Override public <F> F field(String fieldName) {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override public boolean hasField(String fieldName) {
            return false;
        }

        /** {@inheritDoc} */
        @Override public <T> T deserialize() {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override public <T> T deserialize(ClassLoader ldr) {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override public BinaryObject clone() {
            return this;
        }

        /** {@inheritDoc} */
        @Override public BinaryObjectBuilder toBuilder() {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override public int enumOrdinal() {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override public String enumName() {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override public int size() {
            return 0;
        }
    }
}
