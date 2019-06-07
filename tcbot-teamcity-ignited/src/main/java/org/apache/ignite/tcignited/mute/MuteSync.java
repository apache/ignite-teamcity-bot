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

package org.apache.ignite.tcignited.mute;

import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;

import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.apache.ignite.tcservice.ITeamcityConn;
import org.apache.ignite.internal.util.typedef.F;

/**
 *
 */
public class MuteSync {
    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** Mute DAO. */
    @Inject private MuteDao muteDao;

    /**
     * Start named task to refresh mutes for given project.
     */
    public void ensureActualizeMutes(String taskName, String projectId, int srvIdMaskHigh, ITeamcityConn conn) {
        scheduler.sheduleNamed(taskName, () -> actualizeMuteRefs(projectId, srvIdMaskHigh, conn), 15, TimeUnit.MINUTES);
    }

    /**
     * Refresh mutes for given project.
     *
     * @param projectId Project id.
     * @param srvIdMaskHigh Server id mask high.
     * @param conn TeamCity connection.
     * @return Message with loading result.
     */
    @MonitoredTask(name = "Actualize Mute", nameExtArgsIndexes = {0})
    protected String actualizeMuteRefs(String projectId, int srvIdMaskHigh, ITeamcityConn conn) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();
        SortedSet<MuteInfo> tcDataPage = conn.getMutesPage(projectId, null, outLinkNext);

        if (F.isEmpty(tcDataPage))
            return "No mutes found. Nothing to save.";

        muteDao.saveChunk(srvIdMaskHigh, tcDataPage);

        int mutesSaved = tcDataPage.size();
        int mutesDeleted = removeMutes(srvIdMaskHigh, tcDataPage);
        int lastId = tcDataPage.last().id;

        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            outLinkNext.set(null);

            tcDataPage = conn.getMutesPage(projectId, nextPageUrl, outLinkNext);

            if (F.isEmpty(tcDataPage))
                break;

            muteDao.saveChunk(srvIdMaskHigh, tcDataPage);

            mutesSaved += tcDataPage.size();
            mutesDeleted += removeMutes(srvIdMaskHigh, tcDataPage);

            lastId = tcDataPage.last().id;
        }

        mutesDeleted += muteDao.removeAllAfter(srvIdMaskHigh, lastId);

        return "Mutes saved " + mutesSaved + ", removed " + mutesDeleted + " for " + projectId;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param page Page.
     */
    private int removeMutes(int srvIdMaskHigh, SortedSet<MuteInfo> page) {
        int checkId = page.first().id;
        int rmv = 0;

        for (MuteInfo mute : page) {
            while (checkId++ != mute.id) {
                if (muteDao.remove(srvIdMaskHigh, checkId - 1))
                    rmv++;
            }
        }

        return rmv;
    }
}