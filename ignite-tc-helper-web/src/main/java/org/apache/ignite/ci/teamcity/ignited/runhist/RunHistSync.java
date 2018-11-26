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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.internal.util.GridConcurrentHashSet;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class RunHistSync {
    /** Compactor. */
    @Inject private IStringCompactor compactor;

    @Inject private IScheduler scheduler;

    @Inject private RunHistCompactedDao dao;

    /** Build to save to history. */
    @GuardedBy("this")
    private final Map<Integer, SyncTask> buildToSave = new HashMap<>();

    /**
     * @param srvId Server id.
     * @param buildId Build id.
     * @param build Build.
     */
    public void saveToHistoryLater(int srvId, int buildId, FatBuildCompacted build) {
        if (!build.isFinished(compactor))
            return;

        if (build.isCancelled(compactor))
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

                    final Boolean res = dao.addInvocation(srvId, t, build.id(), build.branchName(), inv);

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

    /**
     * Scope of work: builds to be loaded from a connection.
     */
    private static class SyncTask {
        Map<Integer, FatBuildCompacted> buildsToSave = new HashMap<>();

        GridConcurrentHashSet<Integer> loadingBuilds = new GridConcurrentHashSet<>();
    }
}
