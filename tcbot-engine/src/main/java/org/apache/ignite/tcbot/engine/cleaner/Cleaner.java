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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.defect.DefectsStorage;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcbot.engine.newtests.NewTestsStorage;
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
    private final AtomicBoolean init = new AtomicBoolean();

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

    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(Cleaner.class);

    private ScheduledExecutorService executorService;

    @AutoProfiling
    @MonitoredTask(name = "Clean old cache data and log files")
    public void clean() {
        try {
            if (cfg.getCleanerConfig().enabled()) {
                int numOfItemsToDel = cfg.getCleanerConfig().numOfItemsToDel();

                long safeDaysForCaches = cfg.getCleanerConfig().safeDaysForCaches();

                ZonedDateTime thresholdDateForCaches = ZonedDateTime.now().minusDays(safeDaysForCaches);

                long safeDaysForLogs = cfg.getCleanerConfig().safeDaysForLogs();

                ZonedDateTime thresholdDateForLogs = ZonedDateTime.now().minusDays(safeDaysForLogs);

                logger.info("Some data from caches (numOfItemsToDel=" + numOfItemsToDel + ") older than " + thresholdDateForCaches + " will be removed.");

                logger.info("Some log files (numOfItemsToDel=" + numOfItemsToDel + ") older than " + thresholdDateForLogs + " will be removed.");

                removeCacheEntries(thresholdDateForCaches, numOfItemsToDel);

                removeLogFiles(thresholdDateForLogs, numOfItemsToDel);
            }
            else
                logger.info("Periodic cache clean disabled.");
        }
        catch (Throwable e) {
            logger.error("Periodic cache and log clean failed: " + e.getMessage(), e);

            e.printStackTrace();
        }
    }

    private int removeCacheEntries(ZonedDateTime thresholdDate, int numOfItemsToDel) {
        long thresholdEpochMilli = thresholdDate.toInstant().toEpochMilli();

        Set<Long> oldBuildsKeys = fatBuildDao.getOldBuilds(thresholdEpochMilli, numOfItemsToDel);

        Map<Integer, List<Integer>> oldBuildsTeamCityAndBuildIds = oldBuildsKeys.stream()
            .map(FatBuildDao::cacheKeyToSrvIdAndBuildId)
            .collect(groupingBy(IgniteBiTuple::get1, mapping(IgniteBiTuple::get2, toList())));

        defectsStorage.checkIfPossibleToRemove(oldBuildsTeamCityAndBuildIds);

        oldBuildsKeys = oldBuildsTeamCityAndBuildIds.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream()
                .map(buildId -> FatBuildDao.buildIdToCacheKey(entry.getKey(), buildId)))
            .collect(toSet());

        logger.info("Builds will be removed (" + oldBuildsKeys.size() + ")");

        suiteInvocationHistoryDao.removeAll(oldBuildsKeys);
        buildLogCheckResultDao.removeAll(oldBuildsKeys);
        buildRefDao.removeAll(oldBuildsKeys);
        buildStartTimeStorage.removeAll(oldBuildsKeys);
        buildConditionDao.removeAll(oldBuildsKeys);
        defectsStorage.removeOldDefects(oldBuildsTeamCityAndBuildIds);
        issuesStorage.removeOldIssues(oldBuildsTeamCityAndBuildIds);
        fatBuildDao.removeAll(oldBuildsKeys);

        //Need to eventually delete data with broken consistency
        defectsStorage.removeOldDefects(thresholdDate.minusDays(60).toInstant().toEpochMilli(), numOfItemsToDel);
        issuesStorage.removeOldIssues(thresholdDate.minusDays(60).toInstant().toEpochMilli(), numOfItemsToDel);

        newTestsStorage.removeOldTests(ZonedDateTime.now().minusDays(5).toInstant().toEpochMilli());

        return oldBuildsKeys.size();
    }

    private void removeLogFiles(ZonedDateTime thresholdDate, int numOfItemsToDel) {
        long thresholdEpochMilli = thresholdDate.toInstant().toEpochMilli();

        final File workDir = TcBotWorkDir.resolveWorkDir();

        for (String srvId : cfg.getServerIds()) {
            File srvIdLogDir = new File(workDir, cfg.getTeamcityConfig(srvId).logsDirectory());

            removeFiles(srvIdLogDir, thresholdEpochMilli, numOfItemsToDel);
        }

        File tcBotLogDir = new File(workDir, "tcbot_logs");

        removeFiles(tcBotLogDir, thresholdEpochMilli, numOfItemsToDel);
    }

    private void removeFiles(File dir, long thresholdDate, int numOfItemsToDel) {
        File[] logFiles = dir.listFiles();

        List<File> filesToRmv = new ArrayList<>(numOfItemsToDel);

        if (logFiles != null)
            for (File file : logFiles)
                if (file.lastModified() < thresholdDate && numOfItemsToDel-- > 0)
                    filesToRmv.add(file);

        logger.info("In the directory " + dir + " files will be removed (" + filesToRmv.size() + ")");

        for (File file : filesToRmv) {
            file.delete();
        }

    }

    public void startBackgroundClean() {
        if (init.compareAndSet(false, true)) {
            suiteInvocationHistoryDao.init();
            buildLogCheckResultDao.init();
            buildRefDao.init();
            buildStartTimeStorage.init();
            buildConditionDao.init();
            fatBuildDao.init();

            executorService = Executors.newSingleThreadScheduledExecutor();

            executorService.scheduleAtFixedRate(this::clean, 5, cfg.getCleanerConfig().period(), TimeUnit.MINUTES);
        }
    }

    public void stop() {
        if (executorService != null)
            executorService.shutdownNow();
    }
}
