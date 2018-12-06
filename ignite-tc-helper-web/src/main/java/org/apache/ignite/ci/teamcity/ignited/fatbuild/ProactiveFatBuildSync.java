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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import java.util.stream.Stream;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.BuildRefDao;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.SyncMode;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeSync;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistSync;
import org.apache.ignite.ci.teamcity.pure.ITeamcityConn;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.internal.util.GridConcurrentHashSet;
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
    public static final int FAT_BUILD_PROACTIVE_TASKS = 5;

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

    /** Change sync. */
    @Inject private ChangeSync changeSync;

    /** Run history sync. */
    @Inject private RunHistSync runHistSync;

    @GuardedBy("this")
    private Map<String, SyncTask> buildToLoad = new HashMap<>();

    public void doLoadBuilds(int i, String srvNme, ITeamcityConn conn, Set<Integer> paginateUntil) {
        doLoadBuilds(i, srvNme, conn, paginateUntil, getSyncTask(conn).loadingBuilds);
    }

    /**
     * Scope of work: builds to be loaded from a connection.
     */
    private static class SyncTask {
        ITeamcityConn conn;
        Set<Integer> ids = new HashSet<>();

        GridConcurrentHashSet<Integer> loadingBuilds = new GridConcurrentHashSet<>();
    }

    /**
     * Invoke load fat builds later, re-load provided builds.
     * @param conn
     * @param buildsToAskFromTc Builds to ask from tc.
     */
    public void scheduleBuildsLoad(ITeamcityConn conn, Collection<Integer> buildsToAskFromTc) {
        if (buildsToAskFromTc.isEmpty())
            return;

        synchronized (this) {
            final SyncTask syncTask = getSyncTask(conn);

            buildsToAskFromTc.stream()
                    .filter(id -> !syncTask.loadingBuilds.contains(id))
                    .forEach(syncTask.ids::add);
        }

        int ldrToActivate = ThreadLocalRandom.current().nextInt(FAT_BUILD_PROACTIVE_TASKS);

        scheduler.sheduleNamed(taskName("loadFatBuilds" + ldrToActivate, conn.serverId()),
                () -> loadFatBuilds(ldrToActivate, conn.serverId()), 2, TimeUnit.MINUTES);

    }

    @NotNull
    public synchronized SyncTask getSyncTask(ITeamcityConn conn) {
        final SyncTask syncTask = buildToLoad.computeIfAbsent(conn.serverId(), s -> new SyncTask());

        syncTask.conn = conn;

        return syncTask;
    }

    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Find missing builds", nameExtArgsIndexes = {0})
    @AutoProfiling
    protected String findMissingBuildsFromBuildRef(String srvId, ITeamcityConn conn) {
        int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);

        Stream<BuildRefCompacted> buildRefs = buildRefDao.compactedBuildsForServer(srvIdMaskHigh);

        List<Integer> buildsIdsToLoad = new ArrayList<>();
        AtomicInteger totalAskedToLoad = new AtomicInteger();

        buildRefs.forEach(buildRef -> {
            Integer buildId = buildRef.getId();
            if (buildId == null)
                return;

            if (buildRef.isRunning(compactor)
                || buildRef.isQueued(compactor)
                || !fatBuildDao.containsKey(srvIdMaskHigh, buildId))
                buildsIdsToLoad.add(buildId);

            if (buildsIdsToLoad.size() >= 100) {
                totalAskedToLoad.addAndGet(buildsIdsToLoad.size());
                scheduleBuildsLoad(conn, buildsIdsToLoad);
                buildsIdsToLoad.clear();
            }
        });

        if (!buildsIdsToLoad.isEmpty()) {
            totalAskedToLoad.addAndGet(buildsIdsToLoad.size());
            scheduleBuildsLoad(conn, buildsIdsToLoad);
        }

        return "Invoked later load for " + totalAskedToLoad.get() + " builds from " + srvId;
    }

    /** */
    private void loadFatBuilds(int ldrNo, String srvId) {
        Set<Integer> load;
        ITeamcityConn conn;
        final GridConcurrentHashSet<Integer> loadingBuilds;

        synchronized (this) {
            final SyncTask syncTask = buildToLoad.get(srvId);
            if (syncTask == null)
                return;

            if (syncTask.ids.isEmpty()) {
                syncTask.conn = null;
                return;
            }

            if (syncTask.conn == null)
                return;

            load = syncTask.ids;
            //marking that builds are in progress

            loadingBuilds = syncTask.loadingBuilds;
            loadingBuilds.addAll(load);

            syncTask.ids = new HashSet<>();

            conn = syncTask.conn;
            syncTask.conn = null;
        }

        doLoadBuilds(ldrNo, srvId, conn, load,  loadingBuilds);
    }

    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Proactive Builds Loading (srv,agent)", nameExtArgsIndexes = {1, 0})
    @AutoProfiling
    public String doLoadBuilds(int ldrNo, String srvId, ITeamcityConn conn, Set<Integer> load,
                               GridConcurrentHashSet<Integer> loadingBuilds) {
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

                        FatBuildCompacted savedVer = loadBuild(conn, buildId, existingBuild, SyncMode.RELOAD_QUEUED);

                        if (savedVer != null)
                            ld.incrementAndGet();

                        loadingBuilds.remove(buildId);
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

    /**
     * Schedule missing builds into Fat builds cache. Sync is based by BuildRefs cache.
     * @param srvName Server name.
     * @param conn Connection.
     */
    public void invokeLaterFindMissingByBuildRef(String srvName, ITeamcityConn conn) {
        scheduler.sheduleNamed(taskName("findMissingBuildsFromBuildRef", srvName),
                () -> findMissingBuildsFromBuildRef(srvName, conn), 360, TimeUnit.MINUTES);
    }

    /**
     *
     * @param conn TC connection to load data
     * @param buildId build ID (TC identification).
     * @param existingBuild build from DB.
     * @return null if nothing was saved, use existing build. Non null value indicates that
     * new build if it was updated.
     */
    @Nullable
    public FatBuildCompacted loadBuild(ITeamcityConn conn, int buildId,
        @Nullable FatBuildCompacted existingBuild,
        SyncMode mode) {
        if (existingBuild != null && !existingBuild.isOutdatedEntityVersion()) {
            boolean finished =
                existingBuild.state(compactor) != null // don't count old fake builds as finished
                    && !existingBuild.isRunning(compactor)
                    && !existingBuild.isQueued(compactor);

            if (finished || mode != SyncMode.RELOAD_QUEUED)
                return null;
        }

        FatBuildCompacted savedVer = reloadBuild(conn, buildId, existingBuild);

        if (savedVer == null)
            return null;

        BuildRefCompacted refCompacted = new BuildRefCompacted(savedVer);
        if (savedVer.isFakeStub())
            refCompacted.setId(buildId); //to provide possiblity to save the build

        final String srvName = conn.serverId();
        final int srvIdMask = ITeamcityIgnited.serverIdToInt(srvName);

        buildRefDao.save(srvIdMask, refCompacted);

        runHistSync.saveToHistoryLater(srvName, savedVer);

        return savedVer;
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
    @Nullable public FatBuildCompacted reloadBuild(ITeamcityConn conn, int buildId, @Nullable FatBuildCompacted existingBuild) {
        //todo some sort of locking to avoid double requests

        final String srvName = conn.serverId();
        final int srvIdMask = ITeamcityIgnited.serverIdToInt(srvName);

        if (existingBuild != null && existingBuild.isOutdatedEntityVersion()) {
            int ver = existingBuild.version();
            if (ver == FatBuildCompacted.VER_FULL_DATA_BUT_ID_CONFLICTS_POSSIBLE) {
                if (Objects.equals(buildId, existingBuild.id()))
                    existingBuild.setVersion(FatBuildCompacted.LATEST_VERSION);
                else {
                    logger.warn("Build inconsistency found in the DB, removing build " + existingBuild.getId());

                    existingBuild = null;

                    fatBuildDao.removeFatBuild(srvIdMask, buildId);
                }
            }
        }

        Build build;
        List<TestOccurrencesFull> tests = new ArrayList<>();
        List<ProblemOccurrence> problems = null;
        Statistics statistics = null;
        ChangesList changesList = null;
        try {
            build = conn.getBuild(buildId);

            if (build.isFakeStub())
                build.setCancelled(); // probably now it will not happen because of direct connection to TC.
            else
                Preconditions.checkState(Objects.equals(build.getId(), buildId),
                    "Build IDs are not consistent: returned " + build.getId() + " queued is " + buildId);

            if (build.testOccurrences != null && !build.isComposite()) { // don't query tests for compoite
                String nextHref = null;
                do {
                    TestOccurrencesFull page = conn.getTestsPage(buildId, nextHref, true);
                    nextHref = page.nextHref();

                    tests.add(page);
                }
                while (!Strings.isNullOrEmpty(nextHref));
            }

            if (build.problemOccurrences != null)
                problems = conn.getProblemsAndRegisterCritical(build).getProblemsNonNull();

            if (build.statisticsRef != null)
                statistics = conn.getStatistics(buildId);

            if (build.changesRef != null) {
                changesList = conn.getChangesList(buildId);

                for (int changeId : FatBuildDao.extractChangeIds(changesList)) {
                    // consult change sync for provided changes data

                    changeSync.change(srvIdMask, changeId, conn);
                }
            }
        }
        catch (Exception e) {
            if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                logger.info("Loading build [" + buildId + "] for server [" + srvName + "] failed:" + e.getMessage(), e);

                if (existingBuild != null) {
                    build = existingBuild.toBuild(compactor);

                    if(build.isRunning() || build.isQueued())
                        build.setCancelled();

                    tests = Collections.singletonList(existingBuild.getTestOcurrences(compactor));

                    problems = existingBuild.problems(compactor);
                }
                else {
                    build = Build.createFakeStub();

                    build.setCancelled();
                }
            } else {
                logger.error("Loading build [" + buildId + "] for server [" + srvName + "] failed:" + e.getMessage(), e);

                e.printStackTrace();

                throw ExceptionUtil.propagateException(e);
            }
        }

        //if we are here because of some sort of outdated version of build,
        // new save will be performed with new entity version for compacted build
        return fatBuildDao.saveBuild(srvIdMask, buildId, build, tests, problems, statistics, changesList, existingBuild);
    }
}
