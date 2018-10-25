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


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import com.google.common.collect.Sets;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.pure.ITeamcityConn;

public class TeamcityIgnitedImpl implements ITeamcityIgnited {
    /** Server id. */
    private String srvId;

    /** Pure HTTP Connection API. */
    private ITeamcityConn conn;

    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** Build reference DAO. */
    @Inject private BuildRefDao buildRefDao;

    /** Build DAO. */
    @Inject private FatBuildDao fatBuildDao;

    /** Server ID mask for cache Entries. */
    private int srvIdMaskHigh;


    public void init(String srvId, ITeamcityConn conn) {
        this.srvId = srvId;
        this.conn = conn;

        srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);
        buildRefDao.init(); //todo init somehow in auto
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
        scheduler.sheduleNamed(ITeamcityIgnited.class.getSimpleName() + ".actualizeRecentBuilds",
            this::actualizeRecentBuilds, 2, TimeUnit.MINUTES);

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
        runActualizeBuilds(srvId, false, Sets.newHashSet(build.getId()));

        return build;
    }

    @Override public FatBuildCompacted getFatBuild(int buildId) {
        FatBuildCompacted build = fatBuildDao.getFatBuild(srvIdMaskHigh, buildId);
        if (build != null)
            return build;

        //todo some sort of locking to avoid double requests
        Build build1 = conn.getBuild(buildId);

        return fatBuildDao.saveBuild(srvIdMaskHigh, build1, new ArrayList<>());
    }

    /**
     *
     */
    void actualizeRecentBuilds() {
        List<BuildRefCompacted> running = buildRefDao.getQueuedAndRunning(srvIdMaskHigh);

        Set<Integer> collect = running.stream().map(BuildRefCompacted::id).collect(Collectors.toSet());

        runActualizeBuilds(srvId, false, collect);

        // schedule full resync later
        scheduler.invokeLater(this::sheduleResync, 60, TimeUnit.SECONDS);
    }

    /**
     *
     */
    private void sheduleResync() {
        scheduler.sheduleNamed(ITeamcityIgnited.class.getSimpleName() + ".fullReindex",
            this::fullReindex, 120, TimeUnit.MINUTES);
    }

    /**
     *
     */
    void fullReindex() {
        runActualizeBuilds(srvId, true, null);
    }

    /**
     * @param srvId Server id. todo to be added as composite name extend
     * @param fullReindex Reindex all builds from TC history.
     * @param mandatoryToReload Build ID can be used as end of syncing. Ignored if fullReindex mode.
     */
    @MonitoredTask(name = "Actualize BuildRefs, full resync", nameExtArgIndex = 1)
    @AutoProfiling
    protected String runActualizeBuilds(String srvId, boolean fullReindex,
        @Nullable Set<Integer> mandatoryToReload) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();
        List<BuildRef> tcDataFirstPage = conn.getBuildRefs(null, outLinkNext);

        int cntSaved = buildRefDao.saveChunk(srvIdMaskHigh, tcDataFirstPage);
        int totalChecked = tcDataFirstPage.size();

        final Set<Integer> stillNeedToFind =
            mandatoryToReload == null ? Collections.emptySet() : Sets.newHashSet(mandatoryToReload);

        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            outLinkNext.set(null);
            List<BuildRef> tcDataNextPage = conn.getBuildRefs(nextPageUrl, outLinkNext);
            int savedCurChunk = buildRefDao.saveChunk(srvIdMaskHigh, tcDataNextPage);

            cntSaved += savedCurChunk;
            totalChecked += tcDataNextPage.size();

            if (!fullReindex) {
                if (!stillNeedToFind.isEmpty())
                    tcDataNextPage.stream().map(BuildRef::getId).forEach(stillNeedToFind::remove);

                if (savedCurChunk == 0 && stillNeedToFind.isEmpty())
                    break; // There are no modification at current page, hopefully no modifications at all
            }
        }

        return "Entries saved " + cntSaved + " Builds checked " + totalChecked;
    }

}
