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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.teamcity.ignited.buildref.BuildRefDao;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class RunHistSync {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(RunHistSync.class);
    public static final int MAX_TESTS_QUEUE = 100000;
    public static final int HIST_LDR_TASKS = 4;

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
     * @param branchName Branch name.
     */
    @NotNull public static String normalizeBranch(@Nullable String branchName) {
        String branch = branchName == null ? ITeamcity.DEFAULT : branchName;

        if (ITeamcity.REFS_HEADS_MASTER.equals(branch))
            return ITeamcity.DEFAULT;

        if ("master".equals(branch))
            return ITeamcity.DEFAULT;

        return branch;
    }

    /**
     * @param srvVame Server id.
     * @param build Build.
     */
    public void saveToHistoryLater(String srvVame, FatBuildCompacted build) {
        if (!validForStatistics(build))
            return;

        int srvId = ITeamcityIgnited.serverIdToInt(srvVame);
        if (histDao.buildWasProcessed(srvId, build.id()))
            return;

        boolean saveNow = false;

        int branchNameNormalized = compactor.getStringId(normalizeBranch(build.branchName(compactor)));

        AtomicInteger cntTests = new AtomicInteger();
        Map<RunHistKey, List<Invocation>> testInvMap = new HashMap<>();
        build.getAllTests().forEach(t -> {
            RunHistKey histKey = new RunHistKey(srvId, t.testName(), branchNameNormalized);
            List<Invocation> list = testInvMap.computeIfAbsent(histKey, k -> new ArrayList<>());
            list.add(t.toInvocation(compactor, build));

            cntTests.incrementAndGet();
        });

        RunHistKey buildInvKey = new RunHistKey(srvId, build.buildTypeId(), branchNameNormalized);
        Invocation buildInv = build.toInvocation(compactor);

        int cnt = cntTests.get();

        synchronized (this) {
            final SyncTask syncTask = buildToSave.computeIfAbsent(srvVame, s -> new SyncTask());

            if (syncTask.sheduledTestsCnt() + cnt <= MAX_TESTS_QUEUE)
                syncTask.addLater(testInvMap, cnt, buildInvKey, buildInv);
            else
                saveNow = true;
        }

        if (saveNow) {
            saveInvocationsMap(
                Collections.singletonMap(buildInvKey,
                    Collections.singletonList(buildInv)
                ),
                testInvMap);
        }
        else {
            int ldrToActivate = ThreadLocalRandom.current().nextInt(HIST_LDR_TASKS) + 1;

            scheduler.sheduleNamed(taskName("saveBuildToHistory." + ldrToActivate, srvVame),
                () -> saveBuildToHistory(srvVame, ldrToActivate), 1, TimeUnit.MINUTES);
        }
    }

    @MonitoredTask(name="Save Builds To History(srv, runner)", nameExtArgsIndexes = {0,1})
    @SuppressWarnings("WeakerAccess")
    protected String saveBuildToHistory(String srvName, int ldrToActivate) {
        Map<RunHistKey, List<Invocation>> testsSaveThisRun;
        Map<RunHistKey, List<Invocation>> buildsSaveThisRun;

        synchronized (this) {
            final SyncTask syncTask = buildToSave.get(srvName);
            if (syncTask == null)
                return "Nothing to sync";

            buildsSaveThisRun = syncTask.takeSuites();
            testsSaveThisRun = syncTask.takeTests();
        }

        if(buildsSaveThisRun.isEmpty() && testsSaveThisRun.isEmpty())
            return "Nothing to sync";

        return saveInvocationsMap(buildsSaveThisRun, testsSaveThisRun);
    }

    @AutoProfiling
    @NotNull protected String saveInvocationsMap(
        Map<RunHistKey, List<Invocation>> buildsSaveThisRun,
        Map<RunHistKey, List<Invocation>> saveThisRun) {
        Set<Integer> confirmedNewBuild = new HashSet<>();
        Set<Integer> confirmedDuplicate = new HashSet<>();

        AtomicInteger cntTestInvocations = new AtomicInteger();
        AtomicInteger duplicateOrExpired = new AtomicInteger();
        AtomicInteger cntSuiteInvocations = new AtomicInteger();

        saveThisRun.forEach(
            (histKey, invocationList) -> {
                saveInvocationList(confirmedNewBuild,
                    confirmedDuplicate,
                    cntTestInvocations,
                    duplicateOrExpired,
                    histKey, invocationList);
            }
        );

        buildsSaveThisRun.forEach(
            (histKey, suiteList) -> {
                List<Invocation> invocationsToSave = suiteList.stream()
                    .filter(inv -> confirmedNewBuild.contains(inv.buildId()))
                    .filter(inv -> !InvocationData.isExpired(inv.startDate()))
                    .collect(Collectors.toList());

                Integer cntAdded = histDao.addSuiteInvocations(histKey, invocationsToSave);
                cntSuiteInvocations.addAndGet(cntAdded);
            }
        );

        String res = "History test entries: " + saveThisRun.size() + " processed " + cntTestInvocations.get()
            + " invocations saved to DB " + duplicateOrExpired.get() + " duplicates/expired";

        System.out.println(Thread.currentThread().getName() + ":" + res);

        return res;
    }

    private void saveInvocationList(Set<Integer> confirmedNewBuild,
        Set<Integer> confirmedDuplicate,
        AtomicInteger invocations,
        AtomicInteger duplicateOrExpired,
        RunHistKey histKey,
        List<Invocation> invocationList) {
        List<Invocation> invocationsToSave = new ArrayList<>();
        invocationList.forEach(
            inv -> {
                int buildId = inv.buildId();

                if (confirmedNewBuild.contains(buildId)) {
                    if (!InvocationData.isExpired(inv.startDate()))
                        invocationsToSave.add(inv);

                    return;
                }

                if (confirmedDuplicate.contains(buildId))
                    return;

                if (histDao.setBuildProcessed(histKey.srvId(), buildId, inv.startDate())) {
                    confirmedNewBuild.add(buildId);

                    if (!InvocationData.isExpired(inv.startDate()))
                        invocationsToSave.add(inv);
                }
                else
                    confirmedDuplicate.add(buildId);
            }
        );

        Integer cntAdded = histDao.addTestInvocations(histKey, invocationsToSave);

        invocations.addAndGet(cntAdded);
        duplicateOrExpired.addAndGet(invocationList.size() - cntAdded);
    }

    public void invokeLaterFindMissingHistory(String srvName) {
        scheduler.sheduleNamed(taskName("findMissingHistFromBuildRef", srvName),
            () -> findMissingHistFromBuildRef(srvName), 12, TimeUnit.HOURS);
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

            if (buildsIdsToLoad.size() >= 50) {
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

        return "Invoked later load for history for " + totalAskedToLoad + " builds from " + srvId;
    }

    /**
     * @param srvNme Server  name;
     * @param load Build IDs to be loaded into history cache later.
     */
    private void scheduleHistLoad(String srvNme, List<Integer> load) {
        load.forEach(id -> {
            FatBuildCompacted fatBuild = fatBuildDao.getFatBuild(ITeamcityIgnited.serverIdToInt(srvNme), id);

            if (validForStatistics(fatBuild))
                saveToHistoryLater(srvNme, fatBuild);
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
            //todo support not finished build reloading usign fat build sync or similar.
            && fatBuild.isFinished(compactor);
    }

    /**
     * Scope of work: builds to be loaded from a connection.
     */
    private static class SyncTask {
        private Map<RunHistKey, List<Invocation>> suites = new HashMap<>();
        private AtomicInteger testCnt = new AtomicInteger();
        private Map<RunHistKey, List<Invocation>> tests = new HashMap<>();

        public int sheduledTestsCnt() {
            return testCnt.get();
        }

        public void addLater(Map<RunHistKey, List<Invocation>> testInvMap,
            int testCnt, RunHistKey buildInvKey,
            Invocation buildInv) {
            suites
                .computeIfAbsent(buildInvKey, k -> new ArrayList<>())
                .add(buildInv);
            tests.putAll(testInvMap);
            this.testCnt.addAndGet(testCnt);
        }

        private Map<RunHistKey, List<Invocation>> takeTests() {
            Map<RunHistKey, List<Invocation>> saveThisRun  = tests;

            tests = new HashMap<>();
            testCnt.set(0);

            return saveThisRun;
        }

        private Map<RunHistKey, List<Invocation>> takeSuites() {
            Map<RunHistKey, List<Invocation>> buildsSaveThisRun = suites;

            suites = new HashMap<>();

            return buildsSaveThisRun;
        }
    }
}
