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
package org.apache.ignite.tcbot.engine.cleaner;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.defect.DefectsStorage;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcbot.engine.newtests.NewTestsStorage;
import org.apache.ignite.tcbot.engine.process.ProgressReporter;
import org.apache.ignite.tcbot.persistence.scheduler.MaintenanceActionRegistry;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.buildlog.BuildLogCheckResultDao;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.history.BuildStartTimeStorage;
import org.apache.ignite.tcignited.history.SuiteInvocationHistoryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Cleaner {
    /** Progress reporting step for scanning old fat builds. */
    private static final int OLD_BUILD_SCAN_PROGRESS_STEP = 1_000;

    private final AtomicBoolean init = new AtomicBoolean();
    private final AtomicBoolean maintenanceActionRegistered = new AtomicBoolean();

    @Inject private IIssuesStorage issuesStorage;
    @Inject private FatBuildDao fatBuildDao;
    @Inject private SuiteInvocationHistoryDao suiteInvocationHistoryDao;
    @Inject private BuildLogCheckResultDao buildLogCheckResultDao;
    @Inject private BuildRefDao buildRefDao;
    @Inject private BuildStartTimeStorage buildStartTimeStorage;
    @Inject private BuildConditionDao buildConditionDao;
    @Inject private DefectsStorage defectsStorage;
    @Inject private NewTestsStorage newTestsStorage;
    @Inject private ITcBotConfig cfg;
    @Inject private ProgressReporter progress;
    @Inject private MaintenanceActionRegistry maintenanceActions;
    @Inject private Provider<Cleaner> self;

    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(Cleaner.class);

    private ScheduledExecutorService executorService;

    /** Cache cleanup maintenance action name. */
    public static final String CACHE_CLEAN_ACTION_NAME = "Cleaner.cleanCaches";

    /** Log cleanup maintenance action name. */
    public static final String LOG_CLEAN_ACTION_NAME = "Cleaner.cleanLogs";

    @AutoProfiling
    @MonitoredTask(name = "Clean old cache data and log files")
    public String clean() {
        try {
            if (cfg.getCleanerConfig().enabled()) {
                int numOfItemsToDel = cfg.getCleanerConfig().numOfItemsToDel();

                long safeDaysForCaches = cfg.getCleanerConfig().safeDaysForCaches();

                ZonedDateTime thresholdDateForCaches = ZonedDateTime.now().minusDays(safeDaysForCaches);

                long safeDaysForLogs = cfg.getCleanerConfig().safeDaysForLogs();

                ZonedDateTime thresholdDateForLogs = ZonedDateTime.now().minusDays(safeDaysForLogs);

                logger.info("Some data from caches (numOfItemsToDel=" + numOfItemsToDel + ") older than "
                    + thresholdDateForCaches + " will be removed.");

                logger.info("Some log files (numOfItemsToDel=" + numOfItemsToDel + ") older than " + thresholdDateForLogs + " will be removed.");

                CacheCleanResult cacheRes = removeCacheEntries(thresholdDateForCaches, numOfItemsToDel);
                LogCleanResult logRes = removeLogFiles(thresholdDateForLogs, numOfItemsToDel);
                String res = cacheRes.summary() + "; " + logRes.summary();

                report(res);

                return res;
            }
            else {
                logger.info("Periodic cache clean disabled.");

                return "Periodic cache clean disabled.";
            }
        }
        catch (Throwable e) {
            logger.error("Periodic cache and log clean failed: " + e.getMessage(), e);

            e.printStackTrace();

            return "Periodic cache and log clean failed: " + e.getMessage();
        }
    }

    @AutoProfiling
    @MonitoredTask(name = "Clean old cache data")
    public String cleanCaches() {
        if (!cfg.getCleanerConfig().enabled()) {
            logger.info("Periodic cache clean disabled.");

            return "Periodic cache clean disabled.";
        }

        int numOfItemsToDel = cfg.getCleanerConfig().numOfItemsToDel();
        long safeDaysForCaches = cfg.getCleanerConfig().safeDaysForCaches();
        ZonedDateTime thresholdDate = ZonedDateTime.now().minusDays(safeDaysForCaches);

        logger.info("Some data from caches (numOfItemsToDel=" + numOfItemsToDel + ") older than "
            + thresholdDate + " will be removed.");

        String res = removeCacheEntries(thresholdDate, numOfItemsToDel).summary();

        report(res);

        return res;
    }

    @AutoProfiling
    @MonitoredTask(name = "Clean old log files")
    public String cleanLogs() {
        if (!cfg.getCleanerConfig().enabled()) {
            logger.info("Periodic log clean disabled.");

            return "Periodic log clean disabled.";
        }

        int numOfItemsToDel = cfg.getCleanerConfig().numOfItemsToDel();
        long safeDaysForLogs = cfg.getCleanerConfig().safeDaysForLogs();
        ZonedDateTime thresholdDate = ZonedDateTime.now().minusDays(safeDaysForLogs);

        logger.info("Some log files (numOfItemsToDel=" + numOfItemsToDel + ") older than " + thresholdDate
            + " will be removed.");

        String res = removeLogFiles(thresholdDate, numOfItemsToDel).summary();

        report(res);

        return res;
    }

    private CacheCleanResult removeCacheEntries(ZonedDateTime thresholdDate, int numOfItemsToDel) {
        long thresholdEpochMilli = thresholdDate.toInstant().toEpochMilli();

        report("Checking " + FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME + " for builds older than " + thresholdDate);

        int totalPartitions = fatBuildDao.affinity().partitions();
        int selected = 0;
        int removed = 0;
        int scannedEntries = 0;
        int scannedPartitions = 0;
        boolean limitReached = false;

        for (int part = 0; part < totalPartitions && removed < numOfItemsToDel; part++) {
            scannedPartitions++;
            Set<Long> checkedInPartition = new HashSet<>();

            while (removed < numOfItemsToDel) {
                int remainingLimit = numOfItemsToDel - removed;

                report("Checking " + FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME + " partition " + (part + 1) + "/"
                    + totalPartitions + ", remaining delete limit " + remainingLimit + ", removed so far " + removed);

                FatBuildDao.OldBuildsSearchResult oldBuilds = fatBuildDao.getOldBuildsFromPartition(
                    thresholdEpochMilli,
                    part,
                    remainingLimit,
                    OLD_BUILD_SCAN_PROGRESS_STEP,
                    checkedInPartition,
                    this::report);

                selected += oldBuilds.selected();
                scannedEntries += oldBuilds.scanned();
                checkedInPartition.addAll(oldBuilds.keys());

                if (oldBuilds.keys().isEmpty()) {
                    report("Checked " + FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME + " partition " + (part + 1) + "/"
                        + totalPartitions + ": scanned " + oldBuilds.scanned()
                        + " entries, no more old build candidates");

                    break;
                }

                report("Checked " + FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME + " partition " + (part + 1) + "/"
                    + totalPartitions + ": cursor closed, scanned " + oldBuilds.scanned()
                    + " entries, selected " + oldBuilds.selected() + ", deleting "
                    + oldBuilds.keys().size() + " candidate records");

                int partitionRemoved = removeCacheEntriesForPartition(oldBuilds.keys(), part, totalPartitions);

                removed += partitionRemoved;

                report("Finished " + FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME + " partition " + (part + 1) + "/"
                    + totalPartitions + ": selected " + oldBuilds.selected() + ", removed " + partitionRemoved
                    + ", total selected " + selected + ", total removed " + removed);

                if (!oldBuilds.deleteLimitReached())
                    break;
            }
        }

        if (removed >= numOfItemsToDel && scannedPartitions < totalPartitions)
            limitReached = true;

        removeInconsistentRecords(thresholdDate, numOfItemsToDel);

        return new CacheCleanResult(selected, removed, scannedEntries, scannedPartitions, totalPartitions, limitReached);
    }

    private int removeCacheEntriesForPartition(List<Long> oldBuildsKeysList, int part, int totalPartitions) {
        Set<Long> oldBuildsKeys = new HashSet<>(oldBuildsKeysList);

        Map<Integer, List<Integer>> oldBuildsTeamCityAndBuildIds = oldBuildsKeys.stream()
            .map(FatBuildDao::cacheKeyToSrvIdAndBuildId)
            .collect(groupingBy(IgniteBiTuple::get1, mapping(IgniteBiTuple::get2, toList())));

        String partition = "partition " + (part + 1) + "/" + totalPartitions;

        report("Checking defects before cache cleanup " + partition + ": " + oldBuildsKeys.size()
            + " candidate builds");

        defectsStorage.checkIfPossibleToRemove(oldBuildsTeamCityAndBuildIds);

        oldBuildsKeys = oldBuildsTeamCityAndBuildIds.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream()
                .map(buildId -> FatBuildDao.buildIdToCacheKey(entry.getKey(), buildId)))
            .collect(toSet());

        logger.info("Builds will be removed (" + oldBuildsKeys.size() + ")");

        report("Removing " + partition + " from teamcitySuiteHistory: " + oldBuildsKeys.size()
            + " build records");
        suiteInvocationHistoryDao.removeAll(oldBuildsKeys);

        report("Removing " + partition + " from buildLogCheckResult: " + oldBuildsKeys.size()
            + " build records");
        buildLogCheckResultDao.removeAll(oldBuildsKeys);

        report("Removing " + partition + " from teamcityBuildRef: " + oldBuildsKeys.size()
            + " build records");
        buildRefDao.removeAll(oldBuildsKeys);

        report("Removing " + partition + " from teamcityBuildStartTime: " + oldBuildsKeys.size()
            + " build records");
        buildStartTimeStorage.removeAll(oldBuildsKeys);

        report("Removing " + partition + " from buildsConditions: " + oldBuildsKeys.size()
            + " build records");
        buildConditionDao.removeAll(oldBuildsKeys);

        report("Removing old defects " + partition + ": " + oldBuildsKeys.size() + " build records");
        defectsStorage.removeOldDefects(oldBuildsTeamCityAndBuildIds);

        report("Removing old issues " + partition + ": " + oldBuildsKeys.size() + " build records");
        issuesStorage.removeOldIssues(oldBuildsTeamCityAndBuildIds);

        report("Removing " + partition + " from " + FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME + ": "
            + oldBuildsKeys.size() + " build records");
        fatBuildDao.removeAll(oldBuildsKeys);

        return oldBuildsKeys.size();
    }

    private void removeInconsistentRecords(ZonedDateTime thresholdDate, int numOfItemsToDel) {
        int deleteLimit = Math.max(1, numOfItemsToDel);

        //Need to eventually delete data with broken consistency
        report("Removing inconsistent old defects older than " + thresholdDate.minusDays(60));
        defectsStorage.removeOldDefects(thresholdDate.minusDays(60).toInstant().toEpochMilli(), deleteLimit);

        report("Removing inconsistent old issues older than " + thresholdDate.minusDays(60));
        issuesStorage.removeOldIssues(thresholdDate.minusDays(60).toInstant().toEpochMilli(), deleteLimit);

        report("Removing old new-tests records");
        newTestsStorage.removeOldTests(ZonedDateTime.now().minusDays(5).toInstant().toEpochMilli());
    }

    private LogCleanResult removeLogFiles(ZonedDateTime thresholdDate, int numOfItemsToDel) {
        long thresholdEpochMilli = thresholdDate.toInstant().toEpochMilli();

        final File workDir = TcBotWorkDir.resolveWorkDir();

        LogCleanResult res = new LogCleanResult();

        for (String srvId : cfg.getServerIds()) {
            File srvIdLogDir = new File(workDir, cfg.getTeamcityConfig(srvId).logsDirectory());

            res.add(removeFiles(srvIdLogDir, thresholdEpochMilli, numOfItemsToDel));
        }

        File tcBotLogDir = new File(workDir, "tcbot_logs");

        res.add(removeFiles(tcBotLogDir, thresholdEpochMilli, numOfItemsToDel));

        return res;
    }

    private LogCleanResult removeFiles(File dir, long thresholdDate, int numOfItemsToDel) {
        report("Checking log directory " + dir);

        File[] logFiles = dir.listFiles();

        List<File> filesToRmv = new ArrayList<>(numOfItemsToDel);
        int checked = 0;

        if (logFiles != null) {
            for (File file : logFiles) {
                checked++;

                if (file.lastModified() < thresholdDate && numOfItemsToDel-- > 0)
                    filesToRmv.add(file);
            }
        }

        logger.info("In the directory " + dir + " files will be removed (" + filesToRmv.size() + ")");

        int removed = 0;

        for (File file : filesToRmv) {
            if (file.delete())
                removed++;
        }

        report("Checked log directory " + dir + ": removed " + removed + "/" + filesToRmv.size()
            + " old files, checked " + checked + " files");

        return new LogCleanResult(checked, filesToRmv.size(), removed);
    }

    private void report(String status) {
        progress.report(status);
    }

    private static class CacheCleanResult {
        private final int selected;

        private final int removed;

        private final int scannedEntries;

        private final int scannedPartitions;

        private final int totalPartitions;

        private final boolean limitReached;

        CacheCleanResult(int selected, int removed, int scannedEntries, int scannedPartitions, int totalPartitions,
            boolean limitReached) {
            this.selected = selected;
            this.removed = removed;
            this.scannedEntries = scannedEntries;
            this.scannedPartitions = scannedPartitions;
            this.totalPartitions = totalPartitions;
            this.limitReached = limitReached;
        }

        String summary() {
            return "Caches: removed " + removed + "/" + selected + " selected old build records"
                + ", scanned entries " + scannedEntries
                + ", scanned partitions " + scannedPartitions + "/" + totalPartitions
                + (limitReached ? ", delete limit reached, more old builds may remain" : "");
        }
    }

    private static class LogCleanResult {
        private int checked;

        private int oldFiles;

        private int removed;

        LogCleanResult() {
            // No-op.
        }

        LogCleanResult(int checked, int oldFiles, int removed) {
            this.checked = checked;
            this.oldFiles = oldFiles;
            this.removed = removed;
        }

        void add(LogCleanResult res) {
            checked += res.checked;
            oldFiles += res.oldFiles;
            removed += res.removed;
        }

        String summary() {
            return "Logs: removed " + removed + "/" + oldFiles + " old files, checked " + checked + " files";
        }
    }

    public void startBackgroundClean() {
        if (init.compareAndSet(false, true)) {
            registerMaintenanceAction();

            suiteInvocationHistoryDao.init();
            buildLogCheckResultDao.init();
            buildRefDao.init();
            buildStartTimeStorage.init();
            buildConditionDao.init();
            fatBuildDao.init();

            executorService = Executors.newScheduledThreadPool(2);

            executorService.scheduleAtFixedRate(() -> self.get().cleanLogs(), 5, cfg.getCleanerConfig().period(),
                TimeUnit.MINUTES);
            executorService.scheduleAtFixedRate(() -> self.get().cleanCaches(), 10, cfg.getCleanerConfig().period(),
                TimeUnit.MINUTES);
        }
    }

    /** Registers manual cleaner action for monitoring management UI. */
    private void registerMaintenanceAction() {
        if (maintenanceActionRegistered.compareAndSet(false, true)) {
            maintenanceActions.register(CACHE_CLEAN_ACTION_NAME,
                "Remove old build data from Ignite caches",
                () -> self.get().cleanCaches());
            maintenanceActions.register(LOG_CLEAN_ACTION_NAME,
                "Remove old downloaded build logs and technical log files",
                () -> self.get().cleanLogs());
        }
    }

    public void stop() {
        if (executorService != null)
            executorService.shutdownNow();
    }
}
