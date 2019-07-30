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
package org.apache.ignite.tcignited.buildref;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.common.util.TimeUtil;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.build.ProactiveFatBuildSync;
import org.apache.ignite.tcservice.ITeamcityConn;
import org.apache.ignite.tcservice.model.hist.BuildRef;

/**
 * This class checks all builds ocurred on a TC server.
 * All builds stored into build refs cache.
 * Modified builds are sheduled to be reloaded in build sync.
 */
public class BuildRefSync {
    /**
     * Max builds to check during incremental sync. If this value is reached (50 pages) and some stuck builds still not
     * found, then iteration stops
     */
    public static final int MAX_INCREMENTAL_BUILDS_TO_CHECK = 7000;

    /** Incremental builds WO modification to be found to stop iterating. */
    public static final int INCREMENTAL_BUILDS_WO_MODIFICATION_TO_STOP  = 1000;

    /** Build reference DAO. */
    @Inject private BuildRefDao buildRefDao;

    /** Build Sync. */
    @Inject private ProactiveFatBuildSync fatBuildSync;

    public enum SyncMode {
        ULTRAFAST,
        FULL_REINDEX,
        INCREMENTAL
    }

    /**
     * List all builds (first pages or all available).
     *
     * @param srvId Server id.
     * @param syncMode Reindex all builds from TC history; reindex latets, only find mandatory
     * @param mandatoryToReload [in/out] Build ID should be found before end of sync. Ignored if fullReindex mode.
     * @param conn Teamcity to check builds
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Actualize BuildRefs(srv, syncmode)", nameExtArgsIndexes = {0, 1})
    @AutoProfiling
    public String runActualizeBuildRefs(String srvId,
        SyncMode syncMode,
        @Nullable Set<Integer> mandatoryToReload,
        ITeamcityConn conn) {

        AtomicReference<String> outLinkNext = new AtomicReference<>();
        List<BuildRef> tcDataFirstPage = conn.getBuildRefsPage(null, outLinkNext);

        long start = System.currentTimeMillis();
        int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);
        Set<Long> buildsUpdated = buildRefDao.saveChunk(srvIdMaskHigh, tcDataFirstPage);
        int totalUpdated = buildsUpdated.size();
        fatBuildSync.scheduleBuildsLoad(conn, cacheKeysToBuildIds(buildsUpdated));

        int totalChecked = tcDataFirstPage.size();
        int neededToFind = 0;
        if (mandatoryToReload != null) {
            neededToFind = mandatoryToReload.size();

            tcDataFirstPage.stream().map(BuildRef::getId).forEach(mandatoryToReload::remove);
        }

        if (syncMode == SyncMode.ULTRAFAST && isEmpty(mandatoryToReload)) {
            return "Entries saved " +
                totalUpdated +
                " Builds checked " +
                totalChecked +
                " Needed to find " +
                neededToFind +
                " remained to find " +
                mandatoryToReload.size();
        }

        long lastTimeUpdateFound = System.currentTimeMillis();
        long maxMsWithoutChanges = Duration.ofHours(1).toMillis();

        //reason for end for full sync
        boolean timeoutForNewBuild = false;
        //reason for end for incremental sync: decrementing counter of builds to find without modification to stop search.
        int buildsCntrToStop = INCREMENTAL_BUILDS_WO_MODIFICATION_TO_STOP;

        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            outLinkNext.set(null);
            List<BuildRef> tcDataNextPage = conn.getBuildRefsPage(nextPageUrl, outLinkNext);
            Set<Long> curChunkBuildsSaved = buildRefDao.saveChunk(srvIdMaskHigh, tcDataNextPage);
            totalUpdated += curChunkBuildsSaved.size();
            fatBuildSync.scheduleBuildsLoad(conn, cacheKeysToBuildIds(curChunkBuildsSaved));

            int savedCurChunk = curChunkBuildsSaved.size();

            totalChecked += tcDataNextPage.size();
            if (savedCurChunk != 0) {
                lastTimeUpdateFound = System.currentTimeMillis();

                buildsCntrToStop = INCREMENTAL_BUILDS_WO_MODIFICATION_TO_STOP;
            } else
                buildsCntrToStop -= tcDataNextPage.size();

            if (syncMode == SyncMode.ULTRAFAST && isEmpty(mandatoryToReload))
                break;
            else if (syncMode==SyncMode.FULL_REINDEX) {
                timeoutForNewBuild = System.currentTimeMillis() > lastTimeUpdateFound + maxMsWithoutChanges;
                if (timeoutForNewBuild
                    && totalChecked > MAX_INCREMENTAL_BUILDS_TO_CHECK)
                    break;
            }
            else {
                boolean noMandatoryBuildsLeft = isEmpty(mandatoryToReload);
                if (!noMandatoryBuildsLeft)
                    tcDataNextPage.stream().map(BuildRef::getId).forEach(mandatoryToReload::remove);

                if (buildsCntrToStop <= 0
                    && (noMandatoryBuildsLeft || totalChecked > MAX_INCREMENTAL_BUILDS_TO_CHECK)) {
                    // There are no modification at current page, hopefully no modifications at all
                    break;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Entries saved ");
        sb.append(totalUpdated);
        sb.append(" Builds checked ");
        sb.append(totalChecked);
        if (mandatoryToReload != null) {
            sb.append(" Needed to find ");
            sb.append(neededToFind);
            int leftToFind = mandatoryToReload.size();
            sb.append(" remained to find ");
            sb.append(leftToFind);
        }

        sb.append(" Last time update found ");
        sb.append(TimeUtil.millisToDurationPrintable(System.currentTimeMillis()- lastTimeUpdateFound));
        sb.append(" ago");

        if(timeoutForNewBuild) {
            sb.append("TIMEOUT, total time: ");
            sb.append(TimeUtil.millisToDurationPrintable(System.currentTimeMillis()- start));
        }

        return sb.toString();
    }

    public boolean isEmpty(@Nullable Set<Integer> mandatoryToReload) {
        return mandatoryToReload == null || mandatoryToReload.isEmpty();
    }

    @Nonnull
    private List<Integer> cacheKeysToBuildIds(Collection<Long> cacheKeysUpdated) {
        return cacheKeysUpdated.stream().map(BuildRefDao::cacheKeyToBuildId).collect(Collectors.toList());
    }
}
