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
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertTrue;

/**
 * Integration tests for GridIntList migration recovery on real Ignite persistence.
 */
public class GridIntListMigratorIntegrationTest {
    /** Cache from the production failure. */
    private static final String BUILD_LOG_CHECK_RESULT = "buildLogCheckResult";

    /** Type name whose default Ignite type ID is -526400035. */
    private static final String MISSING_BINARY_METADATA_TYPE = "ayzjfkj";

    /** Type ID from the production failure. */
    private static final int MISSING_BINARY_METADATA_TYPE_ID = -526400035;

    /** Key from the production failure. */
    private static final long FAILED_BUILD_LOG_CHECK_RESULT_KEY = 6062419808021002488L;

    /** */
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Reproduces the production failure on a persistent Ignite cache by deleting binary metadata before migration.
     */
    @Test public void migrationAutoRepairsPersistentEntryWithMissingBinaryMetadata() throws Exception {
        System.setProperty(GridIntListMigrator.AUTO_REPAIR_WAIT_MILLIS_PROPERTY, "1");

        java.io.File workDir = tmp.newFolder("persistent-ignite-work");

        try {
            Ignite ignite = Ignition.start(persistentConfiguration(workDir, "missing-binary-metadata-1"));

            try {
                ignite.cluster().state(ClusterState.ACTIVE);

                IgniteCache<Long, Object> cache = ignite.getOrCreateCache(new CacheConfiguration<Long, Object>(
                    BUILD_LOG_CHECK_RESULT));
                BinaryObjectBuilder builder = ignite.binary().builder(MISSING_BINARY_METADATA_TYPE);

                builder.setField("field", "value");

                cache.withKeepBinary().put(FAILED_BUILD_LOG_CHECK_RESULT_KEY, builder.build());
            }
            finally {
                ignite.close();
            }

            deleteBinaryMetadata(workDir.toPath(), MISSING_BINARY_METADATA_TYPE_ID);

            ignite = Ignition.start(persistentConfiguration(workDir, "missing-binary-metadata-2"));

            try {
                ignite.cluster().state(ClusterState.ACTIVE);

                GridIntListMigrator.migrateOnInstance(ignite, BUILD_LOG_CHECK_RESULT, true, false, 1);

                IgniteCache<Object, Object> cache = ignite.cache(BUILD_LOG_CHECK_RESULT);

                assertTrue("Broken buildLogCheckResult entry must be removed",
                    !cache.containsKey(FAILED_BUILD_LOG_CHECK_RESULT_KEY));

                Path dumpDir = workDir.toPath().resolve("diagnostic").resolve("grid-int-list-migration-recovery");
                Path dump = Files.list(dumpDir)
                    .filter(path -> path.getFileName().toString().endsWith("_manifest.jsonl"))
                    .findFirst().orElseThrow(() ->
                    new AssertionError("Recovery dump was not written"));
                String dumpText = new String(Files.readAllBytes(dump), StandardCharsets.UTF_8);

                assertTrue(dumpText.contains(BUILD_LOG_CHECK_RESULT));
                assertTrue(dumpText.contains(String.valueOf(FAILED_BUILD_LOG_CHECK_RESULT_KEY)));
                assertTrue(dumpText.contains("Failed to get binary type details"));
                assertTrue("Built-in Ignite dump must be moved to diagnostics",
                    Files.list(dumpDir).anyMatch(Files::isDirectory));
            }
            finally {
                ignite.close();
            }
        }
        finally {
            System.clearProperty(GridIntListMigrator.AUTO_REPAIR_WAIT_MILLIS_PROPERTY);
        }
    }

    /**
     * @param workDir Work dir.
     * @param name Ignite instance name.
     * @return Persistent single-node configuration.
     */
    private IgniteConfiguration persistentConfiguration(java.io.File workDir, String name) {
        IgniteConfiguration cfg = new IgniteConfiguration();
        DataStorageConfiguration storage = new DataStorageConfiguration();

        storage.getDefaultDataRegionConfiguration().setPersistenceEnabled(true);

        cfg.setIgniteInstanceName(name);
        cfg.setConsistentId("missing-binary-metadata-node");
        cfg.setWorkDirectory(workDir.getAbsolutePath());
        cfg.setDataStorageConfiguration(storage);

        return cfg;
    }

    /**
     * @param workDir Ignite work dir.
     * @param typeId Binary type ID.
     */
    private void deleteBinaryMetadata(Path workDir, int typeId) throws Exception {
        String typeIdText = String.valueOf(typeId);
        boolean deleted;

        try (java.util.stream.Stream<Path> paths = Files.walk(workDir)) {
            deleted = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().contains(typeIdText))
                .map(path -> {
                    try {
                        Files.delete(path);

                        return true;
                    }
                    catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .reduce(false, (left, right) -> left || right);
        }

        assertTrue("Binary metadata file for typeId=" + typeId + " must be deleted", deleted);
    }
}
