package org.apache.ignite.tcbot.engine.cleaner;

import java.io.File;
import java.util.Map;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
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

public class Cleaner {
    @Inject IIssuesStorage issuesStorage;
    @Inject FatBuildDao fatBuildDao;
    @Inject ChangeDao changeDao;
    @Inject SuiteInvocationHistoryDao suiteHistoryDao;
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

    public void clean() {
        long period = 1;
        int countEntryToRemove = 1;
        long oldDate = System.currentTimeMillis() - period;
        Map<Long, FatBuildCompacted> oldBuilds = fatBuildDao.getOldBuilds(oldDate, countEntryToRemove);

        for (Map.Entry<Long, FatBuildCompacted> buildEntry : oldBuilds.entrySet()) {
            long buildCacheKey = buildEntry.getKey();
            int srvCode = BuildRefDao.cacheKeyToSrvId(buildCacheKey);
            int buildId = BuildRefDao.cacheKeyToBuildId(buildCacheKey);
            String strSrvCode = compactor.getStringFromId(srvCode);

            suiteHistoryDao.remove(buildCacheKey);
            buildLogCheckResultDao.remove(buildCacheKey);
            buildRefDao.remove(buildCacheKey);
            buildStartTimeStorage.remove(buildCacheKey);
            buildConditionDao.remove(buildCacheKey);

            defectsStorage.removeOldDefects(oldDate, countEntryToRemove);
            issuesStorage.removeOldIssues(buildId, strSrvCode, countEntryToRemove);

            File logsDirectory = new File(cfg.getTeamcityConfig(strSrvCode).logsDirectory());
            File[] buildLogFiles = logsDirectory.listFiles((dir, name) -> name.contains(Integer.toString(buildId)));
            for (File file : buildLogFiles) {
                file.delete();
            }

            fatBuildDao.remove(buildCacheKey);
        }
    }
}
