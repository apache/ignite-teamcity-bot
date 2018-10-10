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

public class TeamcityIgnitedImpl implements ITeamcityIgnited {
    /** Server id. */
    private String srvId;

    /** Pure HTTP Connection API. */
    private ITeamcity conn;


    /** Scheduler. */
    @Inject private IScheduler scheduler;

    @Inject private BuildRefDao buildRefDao;

    /** Server ID mask for cache Entries. */
    private long srvIdMaskHigh;


    public void init(String srvId, ITeamcity conn) {
        this.srvId = srvId;
        this.conn = conn;

        srvIdMaskHigh = Math.abs(srvId.hashCode());
        buildRefDao.init(); //todo init somehow in auto
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

    private void actualizeRecentBuilds() {
        runAtualizeBuilds(srvId, false);

        // schedule full resync later
        scheduler.invokeLater(this::sheduleResync, 20, TimeUnit.SECONDS);
    }

    /**
     *
     */
    private void sheduleResync() {
        scheduler.sheduleNamed(ITeamcityIgnited.class.getSimpleName() + ".fullReindex",
            this::fullReindex, 60, TimeUnit.MINUTES);
    }

    /**
     *
     */
    private void fullReindex() {
        runAtualizeBuilds(srvId, true);
    }

    /**
     * @param srvId Server id.
     * @param fullReindex Reindex all open PRs
     */
    @MonitoredTask(name = "Actualize BuildRefs, full resync", nameExtArgIndex = 1)
    @AutoProfiling
    protected String runAtualizeBuilds(String srvId, boolean fullReindex) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();
        int totalPages = 0;
        List<BuildRef> tcData = conn.getBuildRefs(null, outLinkNext);
        int cntSaved = buildRefDao.saveChunk(srvIdMaskHigh, tcData);
        int totalChecked = tcData.size();
        totalPages++;
        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            outLinkNext.set(null);
            tcData = conn.getBuildRefs(nextPageUrl, outLinkNext);
            cntSaved += buildRefDao.saveChunk(srvIdMaskHigh, tcData);
            totalChecked += tcData.size();
            totalPages++;

            if (!fullReindex && totalPages >= 7)
                break; // 7 pages, 700 builds
        }

        return "Entries saved " + cntSaved + " Builds checked " + totalChecked;
    }

}
