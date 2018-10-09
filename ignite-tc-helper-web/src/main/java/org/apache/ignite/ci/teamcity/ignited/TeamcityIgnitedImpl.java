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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.tcbot.chain.BuildChainProcessor;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.configuration.CacheConfiguration;
import org.jetbrains.annotations.NotNull;

//todo currently this implementation is shared between all users
public class TeamcityIgnitedImpl implements ITeamcityIgnited {

    //todo move to string compacter
    /** Cache name */
    public static final String STRING_CACHE_NAME = "strings";

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

        return allBuildsEver()
            .filter(e -> Objects.equals(e.buildTypeId, buildTypeId))
            .filter(e -> Objects.equals(e.branchName, bracnhNameQry))
            .collect(Collectors.toList());
    }

    @NotNull private Stream<BuildRef> allBuildsEver() {
        return buildRefDao.getAllBuilds(srvIdMaskHigh);
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

        List<BuildRef> tcData = conn.getFinishedBuilds(null, null);//todo, outLinkNext);
        int cntSaved = buildRefDao.saveChunk(srvIdMaskHigh, tcData);
        int totalChecked = tcData.size();
        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            tcData = conn.getFinishedBuilds(null, null); //todo nextPageUrl, outLinkNext);
            cntSaved += buildRefDao.saveChunk(srvIdMaskHigh, tcData);
            totalChecked += tcData.size();

            if (!fullReindex)
                break; // 2 pages
        }

        return "Entries saved " + cntSaved + " Builds checked " + totalChecked;
    }

}
