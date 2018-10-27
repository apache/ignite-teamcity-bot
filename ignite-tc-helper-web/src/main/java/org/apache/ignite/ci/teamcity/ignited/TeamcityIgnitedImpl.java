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
package org.apache.ignite.ci.teamcity.ignited;


import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.tcbot.condition.BuildCondition;
import org.apache.ignite.ci.tcbot.condition.BuildConditionDao;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProactiveFatBuildSync;
import org.apache.ignite.ci.teamcity.pure.ITeamcityConn;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class TeamcityIgnitedImpl implements ITeamcityIgnited {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TeamcityIgnitedImpl.class);

    /** Max build id diff to enforce reload during incremental refresh. */
    public static final int MAX_ID_DIFF_TO_ENFORCE_CONTINUE_SCAN = 3000;

    /** Server id. */
    private String srvNme;

    /** Pure HTTP Connection API. */
    private ITeamcityConn conn;

    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** Build reference DAO. */
    @Inject private BuildRefDao buildRefDao;

    /** Build condition DAO. */
    @Inject private BuildConditionDao buildConditionDao;

    /** Build DAO. */
    @Inject private FatBuildDao fatBuildDao;

    @Inject private ProactiveFatBuildSync buildSync;

    /** Server ID mask for cache Entries. */
    private int srvIdMaskHigh;

    public void init(String srvId, ITeamcityConn conn) {
        this.srvNme = srvId;
        this.conn = conn;

        srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);
        buildRefDao.init(); //todo init somehow in auto
        buildConditionDao.init();
        fatBuildDao.init();

        buildSync.invokeLaterFindMissingByBuildRef(srvNme);
    }


    @NotNull
    private String taskName(String taskName) {
        return ITeamcityIgnited.class.getSimpleName() +"." + taskName + "." + srvNme;
    }

    /** {@inheritDoc} */
    @Override public String host() {
        return conn.host();
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRef> getBuildHistory(
        @Nullable String buildTypeId,
        @Nullable String branchName) {
        scheduler.sheduleNamed(taskName("actualizeRecentBuildRefs"), this::actualizeRecentBuildRefs, 2, TimeUnit.MINUTES);

        String bracnhNameQry ;
        if (ITeamcity.DEFAULT.equals(branchName))
            bracnhNameQry = "refs/heads/master";
        else
            bracnhNameQry = branchName;

        return buildRefDao.findBuildsInHistory(srvIdMaskHigh, buildTypeId, bracnhNameQry);
    }

    /** {@inheritDoc} */
    @Override public Build triggerBuild(String buildTypeId, String branchName, boolean cleanRebuild, boolean queueAtTop) {
        Build build = conn.triggerBuild(buildTypeId, branchName, cleanRebuild, queueAtTop);

        //todo may add additional parameter: load builds into DB in sync/async fashion
        runActualizeBuildRefs(srvNme, false, Sets.newHashSet(build.getId()));

        return build;
    }

    /** {@inheritDoc} */
    @Override public boolean buildIsValid(int buildId) {
        BuildCondition cond = buildConditionDao.getBuildCondition(srvIdMaskHigh, buildId);

        return cond == null || cond.isValid;
    }

    /** {@inheritDoc} */
    @Override public boolean setBuildCondition(BuildCondition cond) {
        return buildConditionDao.setBuildCondition(srvIdMaskHigh, cond);
    }

    @Override public FatBuildCompacted getFatBuild(int buildId) {
        FatBuildCompacted existingBuild = fatBuildDao.getFatBuild(srvIdMaskHigh, buildId);

        //todo additionally check queued and running builds, refesh builds if they are queued.
        if (existingBuild != null && !existingBuild.isOutdatedEntityVersion())
            return existingBuild;

        FatBuildCompacted savedVer = buildSync.reloadBuild(conn, buildId, existingBuild);

        //build was modified, probably we need also to update reference accordindly
        if (savedVer != null)
            buildRefDao.save(srvIdMaskHigh, new BuildRefCompacted(savedVer));

        return savedVer == null ? existingBuild : savedVer;
    }



    /**
     *
     */
    void actualizeRecentBuildRefs() {
        List<BuildRefCompacted> running = buildRefDao.getQueuedAndRunning(srvIdMaskHigh);

        Set<Integer> paginateUntil = new HashSet<>();
        Set<Integer> directUpload = new HashSet<>();

        List<Integer> runningIds = running.stream().map(BuildRefCompacted::id).collect(Collectors.toList());
        OptionalInt max = runningIds.stream().mapToInt(i -> i).max();
        if (max.isPresent()) {
            runningIds.forEach(id->{
                if(id > (max.getAsInt() - MAX_ID_DIFF_TO_ENFORCE_CONTINUE_SCAN))
                    paginateUntil.add(id);
                else
                    directUpload.add(id);
            });
        }
        //schedule direct reload for Fat Builds for all queued too-old builds
        buildSync.scheduleBuildsLoad(srvNme, directUpload);

        runActualizeBuildRefs(srvNme, false, paginateUntil);

        if(!paginateUntil.isEmpty()) {
            //some builds may stuck in the queued or running, enforce loading as well
            buildSync.scheduleBuildsLoad(srvNme, paginateUntil);
        }

        // schedule full resync later
        scheduler.invokeLater(this::sheduleResyncBuildRefs, 15, TimeUnit.MINUTES);
    }

    /**
     *
     */
    private void sheduleResyncBuildRefs() {
        scheduler.sheduleNamed(taskName("fullReindex"), this::fullReindex, 120, TimeUnit.MINUTES);
    }

    /**
     *
     */
    void fullReindex() {
        runActualizeBuildRefs(srvNme, true, null);

        buildSync.invokeLaterFindMissingByBuildRef(srvNme);
    }


    /**
     * @param srvId Server id.
     * @param fullReindex Reindex all builds from TC history.
     * @param mandatoryToReload [in/out] Build ID should be found before end of sync. Ignored if fullReindex mode.
     *
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Actualize BuildRefs(srv, full resync)", nameExtArgsIndexes = {0, 1})
    @AutoProfiling
    protected String runActualizeBuildRefs(String srvId, boolean fullReindex,
                                           @Nullable Set<Integer> mandatoryToReload) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();
        List<BuildRef> tcDataFirstPage = conn.getBuildRefs(null, outLinkNext);

        Set<Long> buildsUpdated = buildRefDao.saveChunk(srvIdMaskHigh, tcDataFirstPage);
        int totalUpdated = buildsUpdated.size();
        buildSync.scheduleBuildsLoad(srvNme, cacheKeysToBuildIds(buildsUpdated));

        int totalChecked = tcDataFirstPage.size();
        int neededToFind = 0;
        if (mandatoryToReload != null) {
            neededToFind = mandatoryToReload.size();

            tcDataFirstPage.stream().map(BuildRef::getId).forEach(mandatoryToReload::remove);
        }

        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            outLinkNext.set(null);
            List<BuildRef> tcDataNextPage = conn.getBuildRefs(nextPageUrl, outLinkNext);
            Set<Long> curChunkBuildsSaved = buildRefDao.saveChunk(srvIdMaskHigh, tcDataNextPage);
            totalUpdated += curChunkBuildsSaved.size();
            buildSync.scheduleBuildsLoad(srvNme, cacheKeysToBuildIds(curChunkBuildsSaved));

            int savedCurChunk = curChunkBuildsSaved.size();

            totalChecked += tcDataNextPage.size();

            if (!fullReindex) {
                if (mandatoryToReload!=null && !mandatoryToReload.isEmpty())
                    tcDataNextPage.stream().map(BuildRef::getId).forEach(mandatoryToReload::remove);

                if (savedCurChunk == 0 && (mandatoryToReload==null || mandatoryToReload.isEmpty()))
                    break; // There are no modification at current page, hopefully no modifications at all
            }
        }

        int leftToFind = mandatoryToReload == null ? 0 : mandatoryToReload.size();
        return "Entries saved " + totalUpdated + " Builds checked " + totalChecked + " Needed to find " + neededToFind   + " remained to find " + leftToFind;
    }

    @NotNull private List<Integer> cacheKeysToBuildIds(Collection<Long> cacheKeysUpdated) {
        return cacheKeysUpdated.stream().map(BuildRefDao::cacheKeyToBuildId).collect(Collectors.toList());
    }
}
