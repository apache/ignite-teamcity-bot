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

import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.build.ProactiveFatBuildSync;
import org.apache.ignite.tcservice.ITeamcityConn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    public static final int MAX_INCREMENTAL_BUILDS_TO_CHECK = 5000;

    /** Build reference DAO. */
    @Inject private BuildRefDao buildRefDao;

    /** Build Sync. */
    @Inject private ProactiveFatBuildSync fatBuildSync;

    /**
     * List all builds (first pages or all available).
     *
     * @param srvId Server id.
     * @param fullReindex Reindex all builds from TC history.
     * @param mandatoryToReload [in/out] Build ID should be found before end of sync. Ignored if fullReindex mode.
     * @param conn Teamcity to check builds
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Actualize BuildRefs(srv, full resync)", nameExtArgsIndexes = {0, 1})
    @AutoProfiling
    public String runActualizeBuildRefs(String srvId, boolean fullReindex,
                                        @Nullable Set<Integer> mandatoryToReload, ITeamcityConn conn) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();
        List<BuildRef> tcDataFirstPage = conn.getBuildRefsPage(null, outLinkNext);

        final int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);
        Set<Long> buildsUpdated = buildRefDao.saveChunk(srvIdMaskHigh, tcDataFirstPage);
        int totalUpdated = buildsUpdated.size();
        fatBuildSync.scheduleBuildsLoad(conn, cacheKeysToBuildIds(buildsUpdated));

        int totalChecked = tcDataFirstPage.size();
        int neededToFind = 0;
        if (mandatoryToReload != null) {
            neededToFind = mandatoryToReload.size();

            tcDataFirstPage.stream().map(BuildRef::getId).forEach(mandatoryToReload::remove);
        }

        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            outLinkNext.set(null);
            List<BuildRef> tcDataNextPage = conn.getBuildRefsPage(nextPageUrl, outLinkNext);
            Set<Long> curChunkBuildsSaved = buildRefDao.saveChunk(srvIdMaskHigh, tcDataNextPage);
            totalUpdated += curChunkBuildsSaved.size();
            fatBuildSync.scheduleBuildsLoad(conn, cacheKeysToBuildIds(curChunkBuildsSaved));

            int savedCurChunk = curChunkBuildsSaved.size();

            totalChecked += tcDataNextPage.size();

            if (!fullReindex) {
                if (mandatoryToReload != null && !mandatoryToReload.isEmpty())
                    tcDataNextPage.stream().map(BuildRef::getId).forEach(mandatoryToReload::remove);

                if (savedCurChunk == 0 &&
                        (mandatoryToReload == null
                                || mandatoryToReload.isEmpty()
                                || totalChecked > MAX_INCREMENTAL_BUILDS_TO_CHECK)
                ) {
                    // There are no modification at current page, hopefully no modifications at all
                    break;
                }
            }
        }

        int leftToFind = mandatoryToReload == null ? 0 : mandatoryToReload.size();
        return "Entries saved " + totalUpdated + " Builds checked " + totalChecked + " Needed to find " + neededToFind + " remained to find " + leftToFind;
    }


    @Nonnull
    private List<Integer> cacheKeysToBuildIds(Collection<Long> cacheKeysUpdated) {
        return cacheKeysUpdated.stream().map(BuildRefDao::cacheKeyToBuildId).collect(Collectors.toList());
    }
}
