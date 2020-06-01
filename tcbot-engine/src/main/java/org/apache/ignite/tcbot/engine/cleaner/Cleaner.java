package org.apache.ignite.tcbot.engine.cleaner;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
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

    public void clean() {
        try {
            long safeDays = cfg.getCleanerConfig().getSafeDays();
            int numOfItemsToDel = cfg.getCleanerConfig().getNumOfItemsToDel();
            long thresholdDate = ZonedDateTime.now().minusDays(safeDays).toInstant().toEpochMilli();

            removeCacheEntries(thresholdDate, numOfItemsToDel);
            removeLogFiles(thresholdDate, numOfItemsToDel);
        }
        catch (Exception e) {
            e.printStackTrace();

            logger.error("Periodic cache clean failed: " + e.getMessage(), e);
        }

    }

    private void removeCacheEntries(long thresholdDate, int numOfItemsToDel) {
        Map<Long, FatBuildCompacted> oldBuilds = fatBuildDao.getOldBuilds(thresholdDate, numOfItemsToDel);

        for (Map.Entry<Long, FatBuildCompacted> buildEntry : oldBuilds.entrySet()) {
            long buildCacheKey = buildEntry.getKey();

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
            File[] logFiles = srvIdLogDir.listFiles();
            if (logFiles != null)
                for (File file : logFiles) {
                    if (file.lastModified() < thresholdDate && numOfItemsToDel-- > 0)
                        file.delete();
                }
        }
    }

    public void startBackgroundClean() {
        suiteInvocationHistoryDao.init();
        buildLogCheckResultDao.init();
        buildRefDao.init();
        buildStartTimeStorage.init();
        buildConditionDao.init();
        fatBuildDao.init();

        executorService = Executors.newScheduledThreadPool(1);

        executorService.scheduleAtFixedRate(this::clean, 30, 30, TimeUnit.MINUTES);
//        executorService.scheduleAtFixedRate(this::clean, 0, 10, TimeUnit.SECONDS);

    }
}
