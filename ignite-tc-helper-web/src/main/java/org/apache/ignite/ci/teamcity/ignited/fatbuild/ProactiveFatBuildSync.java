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
package org.apache.ignite.ci.teamcity.ignited.fatbuild;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;
import org.apache.ignite.ci.teamcity.ignited.BuildRefDao;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.pure.ITeamcityConn;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ProactiveFatBuildSync {
    public static final int FAT_BUILD_PROACTIVE_TASKS = 4;

    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(ProactiveFatBuildSync.class);

    /** Build reference DAO. */
    @Inject
    private BuildRefDao buildRefDao;

    /** Build DAO. */
    @Inject private FatBuildDao fatBuildDao;

    /** Scheduler. */
    @Inject private IScheduler scheduler;

    @Inject private IStringCompactor compactor;


    @GuardedBy("this")
    private Set<Integer> buildToLoad = new HashSet<>();


    /**
     * Invoke load fat builds later, re-load provided builds.
     * @param srvNme
     * @param buildsToAskFromTc Builds to ask from tc.
     */
    public void scheduleBuildsLoad(String srvNme , Collection<Integer> buildsToAskFromTc) {
        if (buildsToAskFromTc.isEmpty())
            return;

        synchronized (this) {
            buildToLoad.addAll(buildsToAskFromTc);
        }

        int ldrToActivate = ThreadLocalRandom.current().nextInt(FAT_BUILD_PROACTIVE_TASKS);

        scheduler.sheduleNamed(taskName("loadFatBuilds" + ldrToActivate, srvNme),
                () -> loadFatBuilds(ldrToActivate, srvNme), 2, TimeUnit.MINUTES);

    }


    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Find missing builds", nameExtArgsIndexes = {0})
    @AutoProfiling
    protected String findMissingBuildsFromBuildRef(String srvId) {
        int  srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);

        final int[] buildRefKeys = buildRefDao.getAllIds(srvIdMaskHigh);
        final int[] fatBuildKeys = fatBuildDao.getAllIds(srvIdMaskHigh);

        Arrays.parallelSort(buildRefKeys);
        Arrays.parallelSort(fatBuildKeys);
        /* ;
         */

        return "";
    }



    /** */
    private void loadFatBuilds(int ldrNo, String srvNme) {
        Set<Integer> load;

        synchronized (this) {
            load = buildToLoad;
            buildToLoad = new HashSet<>();
        }

        doLoadBuilds(ldrNo, srvNme, conn, load);
    }

    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Proactive Builds Loading (agent,server)", nameExtArgsIndexes = {0, 1})
    @AutoProfiling
    protected String doLoadBuilds(int ldrNo, String srvId, ITeamcityConn conn, Set<Integer> load) {
        if(load.isEmpty())
            return "Nothing to load";

        final int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);

        AtomicInteger err = new AtomicInteger();
        AtomicInteger ld = new AtomicInteger();

        Map<Long, FatBuildCompacted> builds = fatBuildDao.getAllFatBuilds(srvIdMaskHigh, load);

        load.forEach(
                buildId -> {
                    try {
                        FatBuildCompacted existingBuild = builds.get(FatBuildDao.buildIdToCacheKey(srvIdMaskHigh, buildId));

                        FatBuildCompacted savedVer = reloadBuild(conn, buildId, existingBuild);

                        if (savedVer != null)
                            ld.incrementAndGet();
                    }
                    catch (Exception e) {
                        logger.error("", e);
                        err.incrementAndGet();
                    }
                }
        );

        return "Builds updated " + ld.get() + " from " + load.size() + " requested, errors: " + err;
    }

    @NotNull
    private String taskName(String taskName, String srvName) {
        return ProactiveFatBuildSync.class.getSimpleName() +"." + taskName + "." + srvName;
    }

    public void invokeLaterFindMissingByBuildRef(String srvName) {
        scheduler.sheduleNamed(taskName("findMissingBuildsFromBuildRef", srvName),
                () -> findMissingBuildsFromBuildRef(srvName), 360, TimeUnit.MINUTES);
    }

    /**
     *
     * @param conn
     * @param buildId
     * @param existingBuild
     * @return new build if it was updated or null if no updates detected
     */
    @SuppressWarnings({"WeakerAccess"})
    @AutoProfiling
    public FatBuildCompacted reloadBuild(ITeamcityConn conn, int buildId, @Nullable FatBuildCompacted existingBuild) {
        //  System.err.println(Thread.currentThread().getName()+ ": Build " + buildId);
        //todo some sort of locking to avoid double requests

        final String srvNme = conn.serverId();
        final int srvIdMask = ITeamcityIgnited.serverIdToInt(srvNme);

        Build build;
        List<TestOccurrencesFull> tests = new ArrayList<>();
        try {
            build = conn.getBuild(buildId);

            String nextHref = null;
            do {
                boolean testDtls = !build.isComposite(); // don't query test details for compoite
                TestOccurrencesFull page = conn.getTestsPage(buildId, nextHref, testDtls);
                nextHref = page.nextHref();

                tests.add(page);
            }
            while (!Strings.isNullOrEmpty(nextHref));
        }
        catch (Exception e) {
            if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                logger.info("Loading build [" + buildId + "] for server [" + srvNme + "] failed:" + e.getMessage(), e);

                if (existingBuild != null) {
                    build = existingBuild.toBuild(compactor);

                    if(build.isRunning() || build.isQueued())
                        build.setCancelled();

                    tests = Collections.singletonList(existingBuild.getTestOcurrences(compactor));
                }
                else
                    build = Build.createFakeStub();
            } else {
                logger.error("Loading build [" + buildId + "] for server [" + srvNme + "] failed:" + e.getMessage(), e);

                e.printStackTrace();

                throw ExceptionUtil.propagateException(e);
            }
        }

        //if we are here because of some sort of outdated version of build,
        // new save will be performed with new entity version for compacted build
        return fatBuildDao.saveBuild(srvIdMask, buildId, build, tests, existingBuild);
    }
}
