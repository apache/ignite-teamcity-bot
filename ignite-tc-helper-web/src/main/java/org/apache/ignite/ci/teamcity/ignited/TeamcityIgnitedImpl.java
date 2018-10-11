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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
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

    /** Server ID mask for cache Entries. */
    private long srvIdMaskHigh;


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
        runActializeBuildRefs(srvId, false, build.getId());

        return build;
    }

    /**
     *
     */
    private void actualizeRecentBuilds() {
        runActializeBuildRefs(srvId, false, null);

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
    private void fullReindex() {
        runActializeBuildRefs(srvId, true, null);
    }

    /**
     * @param srvId Server id.
     * @param fullReindex Reindex all builds from TC history. Ignored if particular ID provided.
     * @param buildIdCanFinish Build ID can be used as end of syncing.
     */
    @MonitoredTask(name = "Actualize BuildRefs, full resync", nameExtArgIndex = 1)
    @AutoProfiling
    protected String runActializeBuildRefs(String srvId, boolean fullReindex,
        @Nullable Integer buildIdCanFinish) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();
        List<BuildRef> tcDataFirstPage = conn.getBuildRefs(null, outLinkNext);

        int cntSaved = buildRefDao.saveChunk(srvIdMaskHigh, tcDataFirstPage);
        int totalChecked = tcDataFirstPage.size();

        boolean noRequiredBuild = buildIdCanFinish == null;
        boolean requiredBuildFound = false;

        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            outLinkNext.set(null);
            List<BuildRef> tcDataNextPage = conn.getBuildRefs(nextPageUrl, outLinkNext);
            int savedCurChunk = buildRefDao.saveChunk(srvIdMaskHigh, tcDataNextPage);

            cntSaved += savedCurChunk;
            totalChecked += tcDataNextPage.size();

            if (buildIdCanFinish != null) {
                if (tcDataNextPage.stream().map(BuildRef::getId).anyMatch(buildIdCanFinish::equals))
                    requiredBuildFound = true; // Syncing till specific build ID.
            }

            if (savedCurChunk == 0 && ((requiredBuildFound) || (noRequiredBuild && !fullReindex)))
                break; // There are no modification at current page, hopefully no modifications at all
        }

        return "Entries saved " + cntSaved + " Builds checked " + totalChecked;
    }

}
