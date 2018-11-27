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

package org.apache.ignite.ci.teamcity.ignited.runhist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.teamcity.ignited.BuildRefDao;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.apache.ignite.internal.util.GridConcurrentHashSet;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class RunHistSync {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(RunHistSync.class);

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** Run History DAO. */
    @Inject private RunHistCompactedDao histDao;

    /** Build reference DAO. */
    @Inject private BuildRefDao buildRefDao;

    /** Build DAO. */
    @Inject private FatBuildDao fatBuildDao;

    /** Build to save to history. */
    @GuardedBy("this")
    private final Map<String, SyncTask> buildToSave = new HashMap<>();

    /**
     * @param srvVame Server id.
     * @param buildId Build id.
     * @param build Build.
     */
    public void saveToHistoryLater(String srvVame, int buildId, FatBuildCompacted build) {
        if (!validForStatistics(build))
            return;

        int srvId = ITeamcityIgnited.serverIdToInt(srvVame);
        if (histDao.buildWasProcessed(srvId, buildId))
            return;

        synchronized (this) {
            final SyncTask syncTask = buildToSave.computeIfAbsent(srvVame, s -> new SyncTask());

            syncTask.buildsToSave.putIfAbsent(build.id(), build);
        }

        scheduler.sheduleNamed(taskName("saveBuildToHistory", srvVame),
            () -> saveBuildToHistory(srvVame), 2, TimeUnit.MINUTES);
    }

    @AutoProfiling
    @MonitoredTask(name="Save Builds To History", nameExtArgIndex = 0)
    @SuppressWarnings("WeakerAccess")
    protected String saveBuildToHistory(String srvName) {
        int srvMask = ITeamcityIgnited.serverIdToInt(srvName);
        Map<Integer, FatBuildCompacted> saveThisRun;

        synchronized (this) {
            final SyncTask syncTask = buildToSave.get(srvName);
            if (syncTask == null)
                return "Nothing to sync";

            saveThisRun = syncTask.buildsToSave;

            syncTask.buildsToSave = new HashMap<>();
        }

        AtomicInteger builds = new AtomicInteger();
        AtomicInteger invocations = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();

        saveThisRun.values().forEach(
            build -> {
                builds.incrementAndGet();

                if (!histDao.setBuildProcessed(srvMask, build.id(), build.getStartDateTs())) {
                    duplicates.incrementAndGet();

                    return;
                }

                build.getAllTests().forEach(t -> {
                    Invocation inv = t.toInvocation(compactor, build);

                    final Boolean res = histDao.addInvocation(srvMask, t, build.id(), build.branchName(), inv);

                    if (Boolean.FALSE.equals(res))
                        duplicates.incrementAndGet();
                    else
                        invocations.incrementAndGet();
                });
            }
        );

        return "Builds: " + builds.get() + " processed " + invocations.get()
                + " invocations saved to DB " + duplicates.get() + " duplicates";
    }

    public void invokeLaterFindMissingHistory(String srvName) {
        scheduler.sheduleNamed(taskName("findMissingHistFromBuildRef", srvName),
            () -> findMissingHistFromBuildRef(srvName), 360, TimeUnit.MINUTES);
    }

    @NotNull
    private String taskName(String taskName, String srvName) {
        return RunHistSync.class.getSimpleName() +"." + taskName + "." + srvName;
    }

    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Find Missing Build History", nameExtArgsIndexes = {0})
    @AutoProfiling
    protected String findMissingHistFromBuildRef(String srvId) {
        int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);

        final int[] buildRefKeys = buildRefDao.getAllIds(srvIdMaskHigh);

        List<Integer> buildsIdsToLoad = new ArrayList<>();
        int totalAskedToLoad = 0;

        for (int buildId : buildRefKeys) {
            if (histDao.buildWasProcessed(srvIdMaskHigh, buildId))
                continue;

            if (buildsIdsToLoad.size() >= 100) {
                totalAskedToLoad += buildsIdsToLoad.size();
                scheduleHistLoad(srvId,  buildsIdsToLoad);
                buildsIdsToLoad.clear();
            }
            buildsIdsToLoad.add(buildId);
        }

        if (!buildsIdsToLoad.isEmpty()) {
            totalAskedToLoad += buildsIdsToLoad.size();
            scheduleHistLoad(srvId, buildsIdsToLoad);
        }

        return "Invoked later load for " + totalAskedToLoad + " builds from " + srvId;
    }

    /**
     * @param srvNme Server  name;
     * @param load Build IDs to be loaded into history cache later.
     */
    private void scheduleHistLoad(String srvNme, List<Integer> load) {
        load.forEach(id -> {
            FatBuildCompacted fatBuild = fatBuildDao.getFatBuild(ITeamcityIgnited.serverIdToInt(srvNme), id);

            if (validForStatistics(fatBuild))
                saveToHistoryLater(srvNme, fatBuild.id(), fatBuild);
            else
                logger.info("Build is not valid for stat: " +
                    (fatBuild != null ? fatBuild.getId() : null));
        });
    }

    private boolean validForStatistics(FatBuildCompacted fatBuild) {
        return fatBuild != null
            && !fatBuild.isFakeStub()
            && !fatBuild.isOutdatedEntityVersion()
            && !fatBuild.isCancelled(compactor)
            && fatBuild.isFinished(compactor);
    }

    /**
     * Scope of work: builds to be loaded from a connection.
     */
    private static class SyncTask {
        Map<Integer, FatBuildCompacted> buildsToSave = new HashMap<>();

        GridConcurrentHashSet<Integer> loadingBuilds = new GridConcurrentHashSet<>();
    }
}
