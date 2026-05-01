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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.ci.db.DbMigrations;
import org.apache.ignite.ci.db.Ignite2Configurer;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.failure.FailureContext;
import org.apache.ignite.failure.FailureHandler;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.tcbot.common.util.GridIntList;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.apache.ignite.tcbot.persistence.IgniteStringCompactor.STRINGS_CACHE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers opening and migrating a TC Bot persistent storage created by old bot sources on Ignite 2.14.
 */
public class LegacyPersistentStorageCompatibilityTest {
    /** Megabyte in bytes. */
    private static final long MB = 1024L * 1024L;

    /** Last commit before IGNITE-27101. Its build uses Ignite 2.14.0 and Java 11 toolchain. */
    private static final String LEGACY_COMMIT = "7437ec80^";

    /** Expected Ignite version in the old checkout. */
    private static final String LEGACY_IGNITE_VERSION = "2.14.0";

    /** Ignite 2.14 default WAL segments count used by the old bot configuration. */
    private static final int LEGACY_WAL_SEGMENTS = 10;

    /** Ignite 2.14 default WAL segment size used by the old bot configuration. */
    private static final long LEGACY_WAL_SEGMENT_SIZE = 64 * MB;

    /** Test TeamCity server id. Keep it different from apache to avoid collisions with real local work dirs. */
    private static final String SRV_ID = "perf-test-tc";

    /** Project/suite that represents Ignite on Java 8 in TeamCity. */
    private static final String RUN_ALL_JAVA_8 = "IgniteTests24Java8_RunAll";

    /** Branch used by the local perf compatibility test. */
    private static final String BRANCH = "refs/heads/perf-test-master";

    /** Cache that intentionally stores Ignite 2.14 internal GridIntList for migrator coverage. */
    private static final String LEGACY_GRID_INT_LIST_CACHE = "legacyGridIntListCache";

    /** Cache used only to produce enough WAL with old Ignite. */
    private static final String LEGACY_WAL_STRESS_CACHE = "legacyWalStressCache";

    /** Small durable region for the test. */
    private static final long REGION_SIZE = 256L * 1024 * 1024;

    /** */
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Creates a small durable store with old bot code and Ignite 2.14, then opens and migrates it with the current code.
     */
    @Test
    public void currentNodeStartsAndMigratesDatabaseCreatedByLegacyBot() throws Exception {
        File legacyCheckout = tmp.newFolder("legacy-checkout");
        File workRoot = compatibilityWorkRoot();
        File workDir = new File(workRoot, "legacy-tcbot-work");

        recreateDirectory(workRoot, workDir);

        System.out.println("Compat DB work root: " + workRoot.getAbsolutePath());
        System.out.println("Compat DB left after test at: " + workDir.getAbsolutePath());

        createLegacyDatabaseWithOldBot(legacyCheckout, workDir);
        assertLegacyWalWasExercised(workDir);

        RecordingFailureHandler failureHandler = new RecordingFailureHandler();

        System.out.println("Current test JVM Java version: " + System.getProperty("java.version"));
        System.out.println("Current test JVM java.home: " + System.getProperty("java.home"));

        IgniteConfiguration currentCfg = currentNodeConfiguration(workDir, 64212, failureHandler);

        assertCurrentWalConfigurationCompatible(currentCfg);

        Ignite ignite = Ignition.start(currentCfg);

        try {
            System.out.println("Current Ignite node version: " + ignite.version());

            ignite.cluster().active(true);

            String migrationRes = runMigrationsWithFilteredOutput(ignite);

            System.out.println("Migration result: " + migrationRes);

            PersistentStringCompactor compactor = new PersistentStringCompactor(ignite);

            int srvId = ITeamcityIgnited.serverIdToInt(SRV_ID);
            long key = BuildRefDao.buildIdToCacheKey(srvId, 100500);

            IgniteCache<Long, BuildRefCompacted> refs = ignite.cache(BuildRefDao.TEAMCITY_BUILD_CACHE_NAME);
            IgniteCache<Long, FatBuildCompacted> fatBuilds = ignite.cache(FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME);

            assertNotNull(refs);
            assertNotNull(fatBuilds);

            BuildRefCompacted ref = refs.get(key);
            FatBuildCompacted fatBuild = fatBuilds.get(key);

            assertNotNull(ref);
            assertNotNull(fatBuild);
            assertEquals(RUN_ALL_JAVA_8, ref.buildTypeId(compactor));
            assertEquals(BRANCH, ref.branchName(compactor));
            assertTrue(fatBuild.isFinished(compactor));

            Object migrated = ignite.cache(LEGACY_GRID_INT_LIST_CACHE).get("legacy");

            assertEquals(GridIntList.asList(1, 2, 3), migrated);

            failureHandler.assertNoFailure();
        }
        finally {
            failureHandler.stopping();

            ignite.close();
        }
    }

    /**
     * @param ignite Ignite.
     * @return Migration result string.
     */
    private String runMigrationsWithFilteredOutput(Ignite ignite) throws Exception {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        try (PrintStream capture = new PrintStream(buf, true, StandardCharsets.UTF_8.name())) {
            System.setOut(capture);
            System.setErr(capture);

            try {
                return new DbMigrations(ignite).dataMigration();
            }
            finally {
                System.setOut(origOut);
                System.setErr(origErr);

                printMigrationSummary(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * @param log Captured migration output.
     */
    private void printMigrationSummary(String log) {
        Set<String> running = new LinkedHashSet<>();
        Set<String> completed = new LinkedHashSet<>();
        int missingCaches = 0;
        Set<String> gridIntListLines = new LinkedHashSet<>();

        for (String line : log.split("\\R")) {
            if (line.startsWith("Running migration procedure ["))
                running.add(migrationName(line));

            if (line.startsWith("Completed migration procedure ["))
                completed.add(migrationName(line));

            if (line.startsWith("cache [") && line.endsWith("] not found"))
                missingCaches++;

            if (line.contains("migrate-GridIntList"))
                gridIntListLines.add(line);
        }

        for (String line : gridIntListLines)
            System.out.println("Migration detail: " + line);

        System.out.println("Migration procedures observed: running=" + running.size() + ", completed="
            + completed.size() + ", missingCaches=" + missingCaches);
        assertTrue("GridIntList migration must run", !gridIntListLines.isEmpty());
    }

    /**
     * @param line Migration log line.
     * @return Migration procedure name.
     */
    private String migrationName(String line) {
        int start = line.indexOf('[');
        int end = line.lastIndexOf(']');

        return start >= 0 && end > start ? line.substring(start + 1, end) : line;
    }

    /**
     * @param workDir Ignite work root populated by old code.
     */
    private void assertLegacyWalWasExercised(File workDir) throws IOException {
        WalLayout wal = walLayout(workDir);

        System.out.println("Legacy WAL layout before current start: active=" + wal.activeFiles
            + ", archive=" + wal.archiveFiles + ", archiveBytes=" + wal.archiveBytes);

        assertEquals("Old Ignite should create the legacy default active WAL ring",
            LEGACY_WAL_SEGMENTS,
            wal.activeFiles);
        assertTrue("Legacy generator must write enough data to roll WAL into archive", wal.archiveFiles > 0);
    }

    /**
     * @param cfg Current Ignite configuration.
     */
    private void assertCurrentWalConfigurationCompatible(IgniteConfiguration cfg) {
        org.apache.ignite.configuration.DataStorageConfiguration dsCfg = cfg.getDataStorageConfiguration();

        assertEquals("Current WAL segments must remain compatible with the legacy Ignite 2.14 PDS",
            LEGACY_WAL_SEGMENTS,
            dsCfg.getWalSegments());
        assertEquals("Current WAL segment size must remain compatible with the legacy Ignite 2.14 PDS",
            LEGACY_WAL_SEGMENT_SIZE,
            dsCfg.getWalSegmentSize());
        assertFalse("Old PDS does not contain checkpoint recovery files by default",
            dsCfg.isWriteRecoveryDataOnCheckpoint());
    }

    /**
     * @param workDir Ignite work root.
     * @return WAL layout.
     */
    private WalLayout walLayout(File workDir) throws IOException {
        File walRoot = new File(workDir, "tcbot_srv/db/wal");

        return new WalLayout(
            countWalFiles(new File(walRoot, "tcbot")),
            countWalFiles(new File(walRoot, "archive/tcbot")),
            walBytes(new File(walRoot, "archive/tcbot")));
    }

    /**
     * @param dir WAL directory.
     * @return Number of segment files.
     */
    private int countWalFiles(File dir) throws IOException {
        if (!dir.isDirectory())
            return 0;

        try (java.util.stream.Stream<Path> paths = Files.list(dir.toPath())) {
            return (int)paths
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".wal") || name.endsWith(".wal.zip"))
                .count();
        }
    }

    /**
     * @param dir WAL directory.
     * @return Total file size.
     */
    private long walBytes(File dir) throws IOException {
        if (!dir.isDirectory())
            return 0;

        try (java.util.stream.Stream<Path> paths = Files.list(dir.toPath())) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString();

                    return name.endsWith(".wal") || name.endsWith(".wal.zip");
                })
                .mapToLong(path -> path.toFile().length())
                .sum();
        }
    }

    /**
     * @param checkoutDir Directory for old source checkout.
     * @param workDir Ignite work root to be populated by old code.
     */
    private void createLegacyDatabaseWithOldBot(File checkoutDir, File workDir) throws Exception {
        File archive = new File(tmp.getRoot(), "legacy.zip");

        run(repositoryRoot(), "", "git", "archive", "--format=zip", "--output=" + archive.getAbsolutePath(),
            LEGACY_COMMIT);

        unzip(archive, checkoutDir);

        String oldIgniteVersion = legacyIgniteVersion(checkoutDir.toPath());

        System.out.println("Legacy source commit-ish: " + LEGACY_COMMIT);
        System.out.println("Legacy checkout Ignite version from build.gradle: " + oldIgniteVersion);

        assertEquals(LEGACY_IGNITE_VERSION, oldIgniteVersion);

        normalizeLegacyGradleBuild(checkoutDir.toPath());
        addLegacyGeneratorModule(checkoutDir.toPath());

        String gradle = legacyGradleCommand();
        String javaHome = legacyJavaHome();

        System.out.println("Legacy Gradle command: " + gradle);
        System.out.println("Legacy Java home: " + javaHome);

        run(javaHomeBin(javaHome, "java").getParentFile(), "[legacy-java-version] ",
            javaHomeBin(javaHome, "java").getAbsolutePath(), "-version");

        int major = javaMajor(javaHome);

        if (major != 8 && major != 11)
            throw new AssertionError("Legacy database must be generated with Java 8 or 11, but got Java " + major
                + " from " + javaHome);

        List<String> cmd = new ArrayList<>();

        if (isWindows() && gradle.endsWith(".bat")) {
            cmd.add("cmd");
            cmd.add("/c");
        }

        cmd.add(gradle);
        cmd.add("--no-daemon");
        cmd.add("-Dorg.gradle.java.home=" + javaHome);
        cmd.add(":legacy-db-generator:run");
        cmd.add("--args=" + workDir.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(checkoutDir);
        pb.redirectErrorStream(true);
        pb.environment().put("JAVA_HOME", javaHome);
        pb.environment().put("PATH", new File(javaHome, "bin").getAbsolutePath() + File.pathSeparator
            + pb.environment().get("PATH"));

        run(pb, "[legacy-gradle] ");
    }

    /**
     * @param root Legacy checkout root.
     */
    private void normalizeLegacyGradleBuild(Path root) throws IOException {
        Files.walk(root)
            .filter(path -> path.getFileName().toString().endsWith(".gradle"))
            .forEach(path -> {
                try {
                    String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

                    text = text.replace("apply plugin: 'java'", "apply plugin: 'java-library'")
                        .replace("testCompile", "testImplementation")
                        .replaceAll("(?m)^(\\s*)compile(\\s+|\\()", "$1api$2");

                    Files.write(path, text.getBytes(StandardCharsets.UTF_8));
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    /**
     * @param root Legacy checkout root.
     */
    private void addLegacyGeneratorModule(Path root) throws IOException {
        Files.write(root.resolve("settings.gradle"),
            "\ninclude 'legacy-db-generator'\n".getBytes(StandardCharsets.UTF_8),
            java.nio.file.StandardOpenOption.APPEND);

        Path module = root.resolve("legacy-db-generator");
        Path javaDir = module.resolve("src/main/java/org/apache/ignite/ci/db");
        Path resourcesDir = module.resolve("src/main/resources");

        Files.createDirectories(javaDir);
        Files.createDirectories(resourcesDir);

        Files.write(module.resolve("build.gradle"), legacyGeneratorBuildGradle().getBytes(StandardCharsets.UTF_8));
        Files.write(javaDir.resolve("LegacyBotDatabaseGenerator.java"),
            legacyGeneratorJava().getBytes(StandardCharsets.UTF_8));
        Files.write(resourcesDir.resolve("logback.xml"), legacyGeneratorLogback().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return Build file for the injected old-source generator.
     */
    private String legacyGeneratorBuildGradle() {
        return "apply plugin: 'java'\n"
            + "apply plugin: 'application'\n"
            + "\n"
            + "dependencies {\n"
            + "    implementation project(':ignite-tc-helper-web')\n"
            + "    implementation project(':tcbot-common')\n"
            + "    implementation project(':tcbot-persistence')\n"
            + "    implementation project(':tcbot-teamcity')\n"
            + "    implementation project(':tcbot-teamcity-ignited')\n"
            + "    implementation group: 'com.google.guava', name: 'guava', version: guavaVer\n"
            + "    implementation group: 'org.apache.ignite', name: 'ignite-core', version: ignVer\n"
            + "    implementation group: 'org.apache.ignite', name: 'ignite-slf4j', version: ignVer\n"
            + "    implementation group: 'org.apache.ignite', name: 'ignite-direct-io', version: ignVer\n"
            + "    implementation group: 'ch.qos.logback', name: 'logback-core', version: logbackVer\n"
            + "    implementation group: 'ch.qos.logback', name: 'logback-classic', version: logbackVer\n"
            + "}\n"
            + "\n"
            + "application {\n"
            + "    mainClass = 'org.apache.ignite.ci.db.LegacyBotDatabaseGenerator'\n"
            + "    applicationDefaultJvmArgs = [\n"
            + "        '-XX:+IgnoreUnrecognizedVMOptions',\n"
            + "        '--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED',\n"
            + "        '--add-exports=java.base/sun.nio.ch=ALL-UNNAMED',\n"
            + "        '--add-exports=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED',\n"
            + "        '--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED',\n"
            + "        '--illegal-access=permit'\n"
            + "    ]\n"
            + "}\n";
    }

    /**
     * @return Java source compiled and executed in the old checkout.
     */
    private String legacyGeneratorJava() {
        return "package org.apache.ignite.ci.db;\n"
            + "\n"
            + "import java.io.File;\n"
            + "import java.util.Collections;\n"
            + "import java.util.Iterator;\n"
            + "import javax.cache.Cache;\n"
            + "import org.apache.ignite.Ignite;\n"
            + "import org.apache.ignite.IgniteCache;\n"
            + "import org.apache.ignite.Ignition;\n"
            + "import org.apache.ignite.cache.QueryEntity;\n"
            + "import org.apache.ignite.cache.query.ScanQuery;\n"
            + "import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;\n"
            + "import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;\n"
            + "import org.apache.ignite.configuration.CacheConfiguration;\n"
            + "import org.apache.ignite.configuration.DataRegionConfiguration;\n"
            + "import org.apache.ignite.configuration.IgniteConfiguration;\n"
            + "import org.apache.ignite.internal.util.GridIntList;\n"
            + "import org.apache.ignite.logger.slf4j.Slf4jLogger;\n"
            + "import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;\n"
            + "import org.apache.ignite.tcbot.persistence.CacheConfigs;\n"
            + "import org.apache.ignite.tcbot.persistence.IStringCompactor;\n"
            + "import org.apache.ignite.tcignited.ITeamcityIgnited;\n"
            + "import org.apache.ignite.tcignited.build.FatBuildDao;\n"
            + "import org.apache.ignite.tcignited.buildref.BuildRefDao;\n"
            + "import org.apache.ignite.tcservice.model.conf.BuildType;\n"
            + "import org.apache.ignite.tcservice.model.hist.BuildRef;\n"
            + "import org.apache.ignite.tcservice.model.result.Build;\n"
            + "\n"
            + "public class LegacyBotDatabaseGenerator {\n"
            + "    private static final String SRV_ID = \"" + SRV_ID + "\";\n"
            + "    private static final String RUN_ALL_JAVA_8 = \"" + RUN_ALL_JAVA_8 + "\";\n"
            + "    private static final String BRANCH = \"" + BRANCH + "\";\n"
            + "    private static final String LEGACY_GRID_INT_LIST_CACHE = \"" + LEGACY_GRID_INT_LIST_CACHE + "\";\n"
            + "    private static final String LEGACY_WAL_STRESS_CACHE = \"" + LEGACY_WAL_STRESS_CACHE + "\";\n"
            + "    private static final long REGION_SIZE = " + REGION_SIZE + "L;\n"
            + "    private static final int WAL_STRESS_MB = " + legacyWalStressMb() + ";\n"
            + "\n"
            + "    public static void main(String[] args) throws Exception {\n"
            + "        if (args.length != 1)\n"
            + "            throw new IllegalArgumentException(\"Expected work dir argument\");\n"
            + "\n"
            + "        System.out.println(\"Legacy generator Java version: \" + System.getProperty(\"java.version\"));\n"
            + "        System.out.println(\"Legacy generator java.home: \" + System.getProperty(\"java.home\"));\n"
            + "\n"
            + "        File workDir = new File(args[0]);\n"
            + "\n"
            + "        try (Ignite ignite = Ignition.start(configuration(workDir))) {\n"
            + "            System.out.println(\"Legacy Ignite node version: \" + ignite.version());\n"
            + "\n"
            + "            ignite.cluster().active(true);\n"
            + "\n"
            + "            PersistentStringCompactor compactor = new PersistentStringCompactor(ignite);\n"
            + "            IgniteCache<Long, BuildRefCompacted> refs = ignite.getOrCreateCache(CacheConfigs.getCacheV2Config(BuildRefDao.TEAMCITY_BUILD_CACHE_NAME));\n"
            + "            IgniteCache<Long, FatBuildCompacted> fatBuilds = ignite.getOrCreateCache(CacheConfigs.getCacheV2Config(FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME));\n"
            + "            int srvId = ITeamcityIgnited.serverIdToInt(SRV_ID);\n"
            + "\n"
            + "            for (int i = 0; i < 3; i++) {\n"
            + "                Build build = fakeJava8Build(100500 + i);\n"
            + "                long key = BuildRefDao.buildIdToCacheKey(srvId, build.getId());\n"
            + "                refs.put(key, new BuildRefCompacted(compactor, build));\n"
            + "                fatBuilds.put(key, new FatBuildCompacted(compactor, build));\n"
            + "            }\n"
            + "\n"
            + "            IgniteCache<String, Object> legacy = ignite.getOrCreateCache(new CacheConfiguration<String, Object>(LEGACY_GRID_INT_LIST_CACHE));\n"
            + "            legacy.put(\"legacy\", new GridIntList(new int[] {1, 2, 3}));\n"
            + "\n"
            + "            writeWalStressData(ignite);\n"
            + "\n"
            + "            System.out.println(\"Legacy DB generated at: \" + workDir.getAbsolutePath());\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    private static void writeWalStressData(Ignite ignite) {\n"
            + "        IgniteCache<Integer, byte[]> cache = ignite.getOrCreateCache(new CacheConfiguration<Integer, byte[]>(LEGACY_WAL_STRESS_CACHE));\n"
            + "        byte[] payload = new byte[1024 * 1024];\n"
            + "\n"
            + "        for (int i = 0; i < payload.length; i++)\n"
            + "            payload[i] = (byte)i;\n"
            + "\n"
            + "        for (int i = 0; i < WAL_STRESS_MB; i++) {\n"
            + "            payload[0] = (byte)i;\n"
            + "            cache.put(i % 32, payload.clone());\n"
            + "\n"
            + "            if ((i + 1) % 128 == 0)\n"
            + "                System.out.println(\"Legacy WAL stress wrote \" + (i + 1) + \" MB\");\n"
            + "        }\n"
            + "\n"
            + "        System.out.println(\"Legacy WAL stress total payload: \" + WAL_STRESS_MB + \" MB\");\n"
            + "    }\n"
            + "\n"
            + "    private static IgniteConfiguration configuration(File workDir) throws Exception {\n"
            + "        IgniteConfiguration cfg = new IgniteConfiguration();\n"
            + "        Ignite2Configurer.setIgniteHome(cfg, workDir);\n"
            + "        cfg.setWorkDirectory(new File(cfg.getIgniteHome(), \"tcbot_srv\").getCanonicalPath());\n"
            + "        cfg.setIgniteInstanceName(\"legacy-tcbot-db\");\n"
            + "        cfg.setConsistentId(\"tcbot\");\n"
            + "        cfg.setGridLogger(new Slf4jLogger());\n"
            + "\n"
            + "        TcpDiscoverySpi spi = new TcpDiscoverySpi();\n"
            + "        spi.setLocalPort(64211);\n"
            + "        spi.setLocalPortRange(1);\n"
            + "        spi.setIpFinder(new TcHelperDb.LocalOnlyTcpDiscoveryIpFinder(64211));\n"
            + "        cfg.setDiscoverySpi(spi);\n"
            + "\n"
            + "        DataRegionConfiguration regConf = Ignite2Configurer.getDataRegionConfiguration();\n"
            + "        regConf.setMaxSize(REGION_SIZE);\n"
            + "        cfg.setDataStorageConfiguration(Ignite2Configurer.getDataStorageConfiguration(regConf));\n"
            + "\n"
            + "        return cfg;\n"
            + "    }\n"
            + "\n"
            + "    private static Build fakeJava8Build(int buildId) {\n"
            + "        Build build = new Build();\n"
            + "        build.setId(buildId);\n"
            + "        build.buildTypeId = RUN_ALL_JAVA_8;\n"
            + "        build.branchName = BRANCH;\n"
            + "        build.status = BuildRef.STATUS_SUCCESS;\n"
            + "        build.state = BuildRef.STATE_FINISHED;\n"
            + "        build.defaultBranch = Boolean.FALSE;\n"
            + "        build.composite = Boolean.TRUE;\n"
            + "        build.webUrl = \"http://localhost/perf-test/teamcity/build/\" + buildId;\n"
            + "        build.setQueuedDateTs(1700000000000L + buildId);\n"
            + "        build.setStartDateTs(1700000010000L + buildId);\n"
            + "        build.setFinishDateTs(1700000070000L + buildId);\n"
            + "\n"
            + "        BuildType buildType = new BuildType();\n"
            + "        buildType.setId(RUN_ALL_JAVA_8);\n"
            + "        buildType.setName(\"Ignite Java 8 Run All\");\n"
            + "        buildType.setProjectId(\"IgniteTests24Java8\");\n"
            + "        buildType.setProjectName(\"Ignite Tests Java 8\");\n"
            + "        buildType.setWebUrl(\"http://localhost/perf-test/teamcity/buildConfiguration/\" + RUN_ALL_JAVA_8);\n"
            + "        build.setBuildType(buildType);\n"
            + "\n"
            + "        return build;\n"
            + "    }\n"
            + "\n"
            + "    private static class PersistentStringCompactor implements IStringCompactor {\n"
            + "        private final IgniteCache<String, org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity> stringsCache;\n"
            + "        private int nextId;\n"
            + "\n"
            + "        private PersistentStringCompactor(Ignite ignite) {\n"
            + "            CacheConfiguration<String, org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity> cfg = CacheConfigs.getCache8PartsConfig(org.apache.ignite.tcbot.persistence.IgniteStringCompactor.STRINGS_CACHE);\n"
            + "            cfg.setQueryEntities(Collections.singletonList(new QueryEntity(String.class, org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity.class)));\n"
            + "            stringsCache = ignite.getOrCreateCache(cfg);\n"
            + "            nextId = maxKnownId() + 1;\n"
            + "        }\n"
            + "\n"
            + "        @Override public int getStringId(String val) {\n"
            + "            if (val == null)\n"
            + "                return STRING_NULL;\n"
            + "            org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity entity = stringsCache.get(val);\n"
            + "            if (entity != null)\n"
            + "                return entity.id();\n"
            + "            int id = nextId++;\n"
            + "            stringsCache.put(val, new org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity(id, val));\n"
            + "            return id;\n"
            + "        }\n"
            + "\n"
            + "        @Override public String getStringFromId(int id) {\n"
            + "            if (id < 0)\n"
            + "                return null;\n"
            + "            Iterator<Cache.Entry<String, org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity>> it = stringsCache.query(new ScanQuery<String, org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity>((key, val) -> val.id() == id)).iterator();\n"
            + "            return it.hasNext() ? it.next().getValue().val() : null;\n"
            + "        }\n"
            + "\n"
            + "        @Override public Integer getStringIdIfPresent(String val) {\n"
            + "            if (val == null)\n"
            + "                return STRING_NULL;\n"
            + "            org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity entity = stringsCache.get(val);\n"
            + "            return entity == null ? null : entity.id();\n"
            + "        }\n"
            + "\n"
            + "        private int maxKnownId() {\n"
            + "            int max = 0;\n"
            + "            for (Cache.Entry<String, org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity> e : stringsCache)\n"
            + "                max = Math.max(max, e.getValue().id());\n"
            + "            return max;\n"
            + "        }\n"
            + "    }\n"
            + "}\n";
    }

    /**
     * @return Quiet Logback configuration for the injected old-source generator.
     */
    private String legacyGeneratorLogback() {
        return "<configuration>\n"
            + "    <appender name=\"STDOUT\" class=\"ch.qos.logback.core.ConsoleAppender\">\n"
            + "        <encoder><pattern>%msg%n</pattern></encoder>\n"
            + "    </appender>\n"
            + "    <root level=\"WARN\">\n"
            + "        <appender-ref ref=\"STDOUT\" />\n"
            + "    </root>\n"
            + "</configuration>\n";
    }

    /**
     * @return How much old Ignite should write to WAL, in megabytes.
     */
    private int legacyWalStressMb() {
        return Integer.getInteger("compat.legacy.wal.stress.mb", 704);
    }

    /**
     * @param workDir Test work dir.
     * @param port Discovery port.
     * @param failureHandler Failure handler.
     */
    private IgniteConfiguration currentNodeConfiguration(
        File workDir,
        int port,
        RecordingFailureHandler failureHandler) throws IOException {
        IgniteConfiguration cfg = new IgniteConfiguration();

        Ignite2Configurer.setIgniteHome(cfg, workDir);
        cfg.setWorkDirectory(new File(cfg.getIgniteHome(), "tcbot_srv").getCanonicalPath());
        cfg.setIgniteInstanceName("current-tcbot-db");
        cfg.setConsistentId("tcbot");
        cfg.setGridLogger(new Slf4jLogger());
        cfg.setFailureHandler(failureHandler);

        TcpDiscoverySpi spi = new TcpDiscoverySpi();
        spi.setLocalPort(port);
        spi.setLocalPortRange(1);
        spi.setIpFinder(new TcHelperDb.LocalOnlyTcpDiscoveryIpFinder(port));
        cfg.setDiscoverySpi(spi);

        DataRegionConfiguration regConf = Ignite2Configurer.getDataRegionConfiguration();
        regConf.setMaxSize(REGION_SIZE);

        cfg.setDataStorageConfiguration(Ignite2Configurer.getDataStorageConfiguration(regConf));

        return cfg;
    }

    /**
     * @param archive Zip archive.
     * @param target Target directory.
     */
    private void unzip(File archive, File target) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(target, entry.getName());
                String targetPath = target.getCanonicalPath();
                String outPath = out.getCanonicalPath();

                if (!outPath.startsWith(targetPath + File.separator) && !Objects.equals(outPath, targetPath))
                    throw new IOException("Zip entry escapes target dir: " + entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(out.toPath());

                    continue;
                }

                Files.createDirectories(out.getParentFile().toPath());

                try (FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int read;

                    while ((read = zip.read(buf)) > 0)
                        fos.write(buf, 0, read);
                }
            }
        }
    }

    /**
     * @return Gradle executable for building the old checkout.
     */
    private String legacyGradleCommand() {
        String prop = System.getProperty("legacy.gradle.cmd");

        if (prop != null && !prop.isEmpty())
            return prop;

        String env = System.getenv("LEGACY_GRADLE_CMD");

        if (env != null && !env.isEmpty())
            return env;

        File bundled = new File("C:/dev_env/gradle-7.6.6/bin/" + (isWindows() ? "gradle.bat" : "gradle"));

        if (!bundled.isFile())
            throw new IllegalStateException("Legacy Gradle is required. Set -Dlegacy.gradle.cmd or LEGACY_GRADLE_CMD.");

        return bundled.getAbsolutePath();
    }

    /**
     * @return Java home for building/running old bot code.
     */
    private String legacyJavaHome() {
        String prop = System.getProperty("legacy.java.home");

        if (prop != null && !prop.isEmpty())
            return prop;

        String env = System.getenv("LEGACY_JAVA_HOME");

        if (env != null && !env.isEmpty())
            return env;

        File bundled = new File("C:/dev_env/jdk-11.0.31.11-hotspot");

        if (!bundled.isDirectory())
            throw new IllegalStateException("Legacy Java 8/11 is required. Set -Dlegacy.java.home or LEGACY_JAVA_HOME.");

        return bundled.getAbsolutePath();
    }

    /**
     * @param javaHome Java home.
     * @return Major Java version.
     */
    private int javaMajor(String javaHome) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(javaHomeBin(javaHome, "java").getAbsolutePath(), "-XshowSettings:properties",
            "-version");
        pb.redirectErrorStream(true);

        Process proc = pb.start();
        String version = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(),
            StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf("java.version = ");

                if (idx >= 0)
                    version = line.substring(idx + "java.version = ".length()).trim();
            }
        }

        int code = proc.waitFor();

        if (code != 0)
            throw new AssertionError("Unable to detect legacy Java version from " + javaHome);

        if (version == null)
            throw new AssertionError("java.version was not printed by " + javaHome);

        if (version.startsWith("1."))
            return Integer.parseInt(version.substring(2, 3));

        int dot = version.indexOf('.');

        return Integer.parseInt(dot > 0 ? version.substring(0, dot) : version);
    }

    /**
     * @param javaHome Java home.
     * @param executable Executable name without extension.
     * @return Java executable.
     */
    private File javaHomeBin(String javaHome, String executable) {
        return new File(new File(javaHome, "bin"), executable + (isWindows() ? ".exe" : ""));
    }

    /**
     * @param dir Work dir.
     * @param prefix Prefix for process output.
     * @param cmd Command.
     */
    private void run(File dir, String prefix, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);

        run(pb, prefix);
    }

    /**
     * @param pb Process builder.
     * @param prefix Prefix for process output.
     */
    private void run(ProcessBuilder pb, String prefix) throws Exception {
        Process proc = pb.start();
        List<String> tail = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(),
            StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (shouldPrintProcessLine(prefix, line))
                    System.out.println(prefix + line);

                tail.add(line);

                if (tail.size() > 120)
                    tail.remove(0);
            }
        }

        int code = proc.waitFor();

        if (code != 0)
            throw new AssertionError("Command failed with code " + code + ": " + pb.command() + "\n"
                + String.join("\n", tail));
    }

    /**
     * @param prefix Process output prefix.
     * @param line Process output line.
     * @return {@code true} if the line is useful enough for normal successful test output.
     */
    private boolean shouldPrintProcessLine(String prefix, String line) {
        if (!"[legacy-gradle] ".equals(prefix))
            return true;

        return line.startsWith("> Task")
            || line.startsWith("BUILD ")
            || line.contains("Legacy generator")
            || line.contains("Legacy WAL stress")
            || line.contains("Legacy Ignite node version")
            || line.contains("Legacy DB generated")
            || line.contains("ver. " + LEGACY_IGNITE_VERSION)
            || line.contains("FAILED")
            || line.contains("ERROR")
            || line.contains("Exception");
    }

    /**
     * @param root Legacy checkout root.
     * @return Ignite version declared by the old checkout.
     */
    private String legacyIgniteVersion(Path root) throws IOException {
        String build = new String(Files.readAllBytes(root.resolve("build.gradle")), StandardCharsets.UTF_8);

        for (String line : build.split("\\R")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("ignVer")) {
                int start = trimmed.indexOf('\'');
                int end = trimmed.lastIndexOf('\'');

                if (start >= 0 && end > start)
                    return trimmed.substring(start + 1, end);
            }
        }

        throw new IOException("Unable to find ignVer in " + root.resolve("build.gradle"));
    }

    /**
     * @return Stable ignored work root where generated PDS is left for inspection.
     */
    private File compatibilityWorkRoot() {
        String prop = System.getProperty("compat.work.dir");

        if (prop != null && !prop.isEmpty())
            return new File(prop).getAbsoluteFile();

        return new File(repositoryRoot(), "migrator/src/test/work/ignite-db-compat").getAbsoluteFile();
    }

    /**
     * @param allowedRoot Root that may contain deleted files.
     * @param dir Directory to recreate.
     */
    private void recreateDirectory(File allowedRoot, File dir) throws IOException {
        String rootPath = allowedRoot.getCanonicalPath();
        String dirPath = dir.getCanonicalPath();

        if (!dirPath.startsWith(rootPath + File.separator))
            throw new IOException("Refusing to recreate directory outside compat work root: " + dirPath);

        if (dir.exists()) {
            Files.walk(dir.toPath())
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        Files.createDirectories(dir.toPath());
    }

    /**
     * @return Repository root. Gradle may run this test from a subproject directory.
     */
    private File repositoryRoot() {
        File cur = new File(System.getProperty("user.dir")).getAbsoluteFile();

        while (cur != null) {
            if (new File(cur, ".git").exists())
                return cur;

            cur = cur.getParentFile();
        }

        throw new IllegalStateException("Unable to locate git repository root from " + System.getProperty("user.dir"));
    }

    /**
     * @return {@code true} on Windows.
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Records startup/runtime failures without letting Ignite halt the Gradle worker during test shutdown.
     */
    private static class RecordingFailureHandler implements FailureHandler {
        /** */
        private final List<FailureContext> failures = Collections.synchronizedList(new ArrayList<>());

        /** */
        private volatile boolean stopping;

        /** {@inheritDoc} */
        @Override public boolean onFailure(Ignite ignite, FailureContext failureCtx) {
            if (!stopping)
                failures.add(failureCtx);

            return false;
        }

        /** */
        private void stopping() {
            stopping = true;
        }

        /** */
        private void assertNoFailure() {
            if (!failures.isEmpty())
                throw new AssertionError("Ignite failure before shutdown: " + failures);
        }
    }

    /**
     * WAL files left by a generated persistent store.
     */
    private static class WalLayout {
        /** */
        private final int activeFiles;

        /** */
        private final int archiveFiles;

        /** */
        private final long archiveBytes;

        /**
         * @param activeFiles Active WAL segment files.
         * @param archiveFiles Archived WAL segment files.
         * @param archiveBytes Archived WAL bytes.
         */
        private WalLayout(int activeFiles, int archiveFiles, long archiveBytes) {
            this.activeFiles = activeFiles;
            this.archiveFiles = archiveFiles;
            this.archiveBytes = archiveBytes;
        }
    }

    /**
     * Minimal persistent compactor for reading bot-shaped cache data without starting external services.
     */
    private static class PersistentStringCompactor implements IStringCompactor {
        /** */
        private final IgniteCache<String, org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity>
            stringsCache;

        /** */
        private int nextId;

        /**
         * @param ignite Ignite.
         */
        private PersistentStringCompactor(Ignite ignite) {
            org.apache.ignite.configuration.CacheConfiguration<String,
                org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity> cfg =
                CacheConfigs.getCache8PartsConfig(STRINGS_CACHE);

            cfg.setQueryEntities(Collections.singletonList(new QueryEntity(
                String.class,
                org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity.class)));

            stringsCache = ignite.getOrCreateCache(cfg);

            nextId = maxKnownId() + 1;
        }

        /** {@inheritDoc} */
        @Override public int getStringId(String val) {
            if (val == null)
                return STRING_NULL;

            org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity entity = stringsCache.get(val);

            if (entity != null)
                return entity.id();

            int id = nextId++;

            stringsCache.put(val, new org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity(
                id, val));

            return id;
        }

        /** {@inheritDoc} */
        @Override public String getStringFromId(int id) {
            if (id < 0)
                return null;

            Iterator<Cache.Entry<String, org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity>>
                it = stringsCache.query(new ScanQuery<String,
                org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity>(
                    (key, val) -> val.id() == id)).iterator();

            return it.hasNext() ? it.next().getValue().val() : null;
        }

        /** {@inheritDoc} */
        @Override public Integer getStringIdIfPresent(String val) {
            if (val == null)
                return STRING_NULL;

            org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity entity = stringsCache.get(val);

            return entity == null ? null : entity.id();
        }

        /** */
        private int maxKnownId() {
            int max = 0;

            for (Cache.Entry<String, org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity> e :
                stringsCache)
                max = Math.max(max, e.getValue().id());

            return max;
        }
    }
}
