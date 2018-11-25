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

import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.pure.ITeamcityConn;
import org.apache.ignite.internal.util.GridConcurrentHashSet;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class RunHistSync {
    @Inject
    IStringCompactor compactor;

    @Inject IScheduler scheduler;

    @Inject RunHistCompactedDao dao;

    public void syncLater(int srvId, int buildId, FatBuildCompacted build) {
        if(!build.isFinished(compactor))
            return;

        build.getAllTests().forEach(t -> {
            dao.addInvocation(srvId, t, build.id(), build.getStartDateTs(), build.branchName());
        });
    }

    /**
     * Scope of work: builds to be loaded from a connection.
     */
    private static class SyncTask {
        ITeamcityConn conn;
        Set<Integer> ids = new HashSet<>();

        GridConcurrentHashSet<Integer> loadingBuilds = new GridConcurrentHashSet<>();
    }

    @GuardedBy("this")
    private Map<String, SyncTask> buildToLoad = new HashMap<>();
}
