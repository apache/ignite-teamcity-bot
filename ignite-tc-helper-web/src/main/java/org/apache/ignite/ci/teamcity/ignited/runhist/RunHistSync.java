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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.teamcity.ignited.BuildRefDao;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.apache.ignite.internal.util.GridConcurrentHashSet;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class RunHistSync {
    /** Compactor. */
    @Inject private IStringCompactor compactor;

    @Inject private IScheduler scheduler;

    @Inject private RunHistCompactedDao histDao;

    /** Build reference DAO. */
    @Inject private BuildRefDao buildRefDao;

    /** Build DAO. */
    @Inject private FatBuildDao fatBuildDao;


    /** Build to save to history. */
    @GuardedBy("this")
    private final Map<Integer, SyncTask> buildToSave = new HashMap<>();

    /**
     * @param srvId Server id.
     * @param buildId Build id.
     * @param build Build.
     */
    public void saveToHistoryLater(int srvId, int buildId, FatBuildCompacted build) {
        if (!validForStatistics(build))
            return;

        synchronized (this) {
            final SyncTask syncTask = buildToSave.computeIfAbsent(srvId, s -> new SyncTask());

            syncTask.buildsToSave.putIfAbsent(build.id(), build);
        }

        scheduler.sheduleNamed(RunHistSync.class.getSimpleName() + ".saveBuildToHistory",
            () -> saveBuildToHistory(srvId), 2, TimeUnit.MINUTES);
    }

    @AutoProfiling
    @MonitoredTask(name="Save Builds To History")
    @SuppressWarnings("WeakerAccess")
    protected String saveBuildToHistory(int srvId) {
        Map<Integer, FatBuildCompacted> saveThisRun;

        synchronized (this) {
            final SyncTask syncTask = buildToSave.get(srvId);
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

                build.getAllTests().forEach(t -> {
                    Invocation inv = t.toInvocation(compactor, build);

                    final Boolean res = histDao.addInvocation(srvId, t, build.id(), build.branchName(), inv);

                    if(Boolean.FALSE.equals(res))
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
    @MonitoredTask(name = "Find missing Build History", nameExtArgsIndexes = {0})
    @AutoProfiling
    protected String findMissingHistFromBuildRef(String srvId ) {
        int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);

        final int[] buildRefKeys = buildRefDao.getAllIds(srvIdMaskHigh);

        List<Integer> buildsIdsToLoad = new ArrayList<>();
        int totalAskedToLoad = 0;

        for (int buildRefKey : buildRefKeys) {
            if (!fatBuildDao.containsKey(srvIdMaskHigh, buildRefKey))
                continue; // FAT Build not yet avaiable, skipping hist loading

            if (buildsIdsToLoad.size() >= 100) {
                totalAskedToLoad += buildsIdsToLoad.size();
                scheduleHistLoad(srvIdMaskHigh,  buildsIdsToLoad);
                buildsIdsToLoad.clear();
            }
            buildsIdsToLoad.add(buildRefKey);
        }

        if (!buildsIdsToLoad.isEmpty()) {
            totalAskedToLoad += buildsIdsToLoad.size();
            scheduleHistLoad(srvIdMaskHigh, buildsIdsToLoad);
        }

        return "Invoked later load for " + totalAskedToLoad + " builds from " + srvId;
    }

    private void scheduleHistLoad(int srvIdMaskHigh, List<Integer> load) {
        //todo implement
        System.err.println("scheduleHistLoad: " + load.toString());

        load.forEach(id -> {
            FatBuildCompacted fatBuild = fatBuildDao.getFatBuild(srvIdMaskHigh, id);

            if (validForStatistics(fatBuild))
                saveToHistoryLater(srvIdMaskHigh, fatBuild.id(), fatBuild);
            else
                System.err.println("Build is not valid for stat: " + fatBuild.toString());
        });
    }

    private boolean validForStatistics(FatBuildCompacted fatBuild) {
        return fatBuild != null
            && !fatBuild.isFakeStub()
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
