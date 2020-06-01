package org.apache.ignite.tcbot.engine.cleaner;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.defect.DefectsStorage;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcbot.engine.issue.IssuesStorage;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
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
    @Inject ChangeDao changeDao;
    @Inject SuiteInvocationHistoryDao suiteInvocationHistoryDao;
    @Inject BuildLogCheckResultDao buildLogCheckResultDao;
    @Inject BuildRefDao buildRefDao;
    @Inject BuildStartTimeStorage buildStartTimeStorage;
    @Inject BuildConditionDao buildConditionDao;
    @Inject DefectsStorage defectsStorage;
    @Inject ITeamcityIgnitedProvider tcProv;
    @Inject DefectsStorage defectStorage;
    @Inject IScheduler scheduler;
    @Inject IStringCompactor compactor;
    @Inject BuildChainProcessor buildChainProcessor;
    @Inject IUserStorage userStorage;
    @Inject ITcBotConfig cfg;

    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(Cleaner.class);

    private final AtomicBoolean init = new AtomicBoolean();

    private ScheduledExecutorService executorService;

    public Cleaner() {
        suiteInvocationHistoryDao.init();
        buildLogCheckResultDao.init();
        buildRefDao.init();
        buildStartTimeStorage.init();
        buildConditionDao.init();
    }

    public void clean() {
        try {
            long period = 1;
            int countEntryToRemove = 1;
            long oldDate = System.currentTimeMillis() - period;
            Map<Long, FatBuildCompacted> oldBuilds = fatBuildDao.getOldBuilds(oldDate, countEntryToRemove);

            for (Map.Entry<Long, FatBuildCompacted> buildEntry : oldBuilds.entrySet()) {
                long buildCacheKey = buildEntry.getKey();
                int srvCode = BuildRefDao.cacheKeyToSrvId(buildCacheKey);
                int buildId = BuildRefDao.cacheKeyToBuildId(buildCacheKey);
                String strSrvCode = compactor.getStringFromId(srvCode);

                suiteInvocationHistoryDao.remove(buildCacheKey);
                buildLogCheckResultDao.remove(buildCacheKey);
                buildRefDao.remove(buildCacheKey);
                buildStartTimeStorage.remove(buildCacheKey);
                buildConditionDao.remove(buildCacheKey);

                defectsStorage.removeOldDefects(oldDate, countEntryToRemove);
                issuesStorage.removeOldIssues(oldDate, countEntryToRemove);

                final File workDir = TcBotWorkDir.resolveWorkDir();

                for (String srvId : cfg.getServerIds()) {
                    File srvIdLogDir = new File(workDir, cfg.getTeamcityConfig(srvId).logsDirectory());
                    for (File file : srvIdLogDir.listFiles()) {
                        if (file.lastModified() < oldDate)
                            file.delete();
                    }
                }

//            File logsDirectory = new File(cfg.getTeamcityConfig(strSrvCode).logsDirectory());
//            for (File file : logsDirectory.listFiles()) {
//                if (file.lastModified() < oldDate)
//                    file.delete();
//            }

                fatBuildDao.remove(buildCacheKey);
            }
        }
        catch (Exception e) {
            e.printStackTrace();

            logger.error("Failure periodic check failed: " + e.getMessage(), e);
        }
    }

    public void startBackgroundClean() {
        try {
            if (init.compareAndSet(false, true)) {

                executorService = Executors.newScheduledThreadPool(1);

                executorService.scheduleAtFixedRate(this::clean, 0, 10, TimeUnit.SECONDS);

            }
        }
        catch (Exception e) {
            e.printStackTrace();

            init.set(false);

            throw e;
        }
    }
}
