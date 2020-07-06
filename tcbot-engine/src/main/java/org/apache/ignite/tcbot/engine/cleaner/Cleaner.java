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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.defect.DefectsStorage;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.buildlog.BuildLogCheckResultDao;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.history.BuildStartTimeStorage;
import org.apache.ignite.tcignited.history.SuiteInvocationHistoryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cleaner {
    private final AtomicBoolean init = new AtomicBoolean();

    @Inject IIssuesStorage issuesStorage;
    @Inject FatBuildDao fatBuildDao;
    @Inject SuiteInvocationHistoryDao suiteInvocationHistoryDao;
    @Inject BuildLogCheckResultDao buildLogCheckResultDao;
    @Inject BuildRefDao buildRefDao;
    @Inject BuildStartTimeStorage buildStartTimeStorage;
    @Inject BuildConditionDao buildConditionDao;
    @Inject DefectsStorage defectsStorage;
    @Inject ITcBotConfig cfg;

    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(Cleaner.class);

    private ScheduledExecutorService executorService;

    @AutoProfiling
    @MonitoredTask(name = "Clean old cache data and log files")
    public void clean() {
        try {
            if (cfg.getCleanerConfig().enabled()) {
                long safeDays = cfg.getCleanerConfig().safeDays();

                int numOfItemsToDel = cfg.getCleanerConfig().numOfItemsToDel();

                ZonedDateTime thresholdDate = ZonedDateTime.now().minusDays(safeDays);

                logger.info("Some data (numOfItemsToDel=" + numOfItemsToDel + ") older than " + thresholdDate + " will be removed.");

                long thresholdEpochMilli = thresholdDate.toInstant().toEpochMilli();

                removeCacheEntries(thresholdEpochMilli, numOfItemsToDel);

                removeLogFiles(thresholdEpochMilli, numOfItemsToDel);
            }
            else
                logger.info("Periodic cache clean disabled.");
        }
        catch (Throwable e) {
            logger.error("Periodic cache clean failed: " + e.getMessage(), e);

            e.printStackTrace();
        }

    }

    private void removeCacheEntries(long thresholdDate, int numOfItemsToDel) {
        List<Long> oldBuildsKeys = fatBuildDao.getOldBuilds(thresholdDate, numOfItemsToDel);

        List<String> strOldBuildsKeys = oldBuildsKeys.stream().map(compositeId -> {
                IgniteBiTuple<Integer, Integer> idTuple = FatBuildDao.cacheKeyToSrvIdAndBuildId(compositeId);
                return "TeamCity id: " + idTuple.get1() + " build id: " + idTuple.get2();
            })
            .collect(Collectors.toList());

        logger.info("Builds will be removed (" + strOldBuildsKeys.size() + "): " + strOldBuildsKeys);

        for (Long buildCacheKey : oldBuildsKeys) {
            suiteInvocationHistoryDao.remove(buildCacheKey);

            buildLogCheckResultDao.remove(buildCacheKey);

            buildRefDao.remove(buildCacheKey);

            buildStartTimeStorage.remove(buildCacheKey);

            buildConditionDao.remove(buildCacheKey);

            fatBuildDao.remove(buildCacheKey);
        }

        defectsStorage.removeOldDefects(thresholdDate, numOfItemsToDel);

        issuesStorage.removeOldIssues(thresholdDate, numOfItemsToDel);
    }

    private void removeLogFiles(long thresholdDate, int numOfItemsToDel) {
        final File workDir = TcBotWorkDir.resolveWorkDir();

        for (String srvId : cfg.getServerIds()) {
            File srvIdLogDir = new File(workDir, cfg.getTeamcityConfig(srvId).logsDirectory());

            removeFiles(srvIdLogDir, thresholdDate, numOfItemsToDel);
        }

        File tcBotLogDir = new File(workDir, "tcbot_logs");

        removeFiles(tcBotLogDir, thresholdDate, numOfItemsToDel);
    }

    private void removeFiles(File dir, long thresholdDate, int numOfItemsToDel) {
        File[] logFiles = dir.listFiles();

        List<File> filesToRmv = new ArrayList<>(numOfItemsToDel);

        if (logFiles != null)
            for (File file : logFiles)
                if (file.lastModified() < thresholdDate && numOfItemsToDel-- > 0)
                    filesToRmv.add(file);

        logger.info("In the directory " + dir + " files will be removed (" +
            filesToRmv.size() + "): " + filesToRmv.stream().map(File::getName).collect(Collectors.toList())
        );

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

            executorService.scheduleAtFixedRate(this::clean, 5, 30, TimeUnit.MINUTES);
        }
    }
}
