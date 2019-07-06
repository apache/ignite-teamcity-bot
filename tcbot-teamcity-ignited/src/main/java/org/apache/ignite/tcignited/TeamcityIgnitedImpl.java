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
package org.apache.ignite.tcignited;

import com.google.common.collect.Sets;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildCondition;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeDao;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefDao;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeSync;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeDao;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeSync;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.GuavaCached;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.build.ProactiveFatBuildSync;
import org.apache.ignite.tcignited.buildlog.BuildLogCheckResultDao;
import org.apache.ignite.tcignited.buildref.BranchEquivalence;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.buildref.BuildRefSync;
import org.apache.ignite.tcignited.history.HistoryCollector;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.history.IRunStat;
import org.apache.ignite.tcignited.history.ISuiteRunHistory;
import org.apache.ignite.tcignited.history.RunHistCompactedDao;
import org.apache.ignite.tcignited.history.RunHistSync;
import org.apache.ignite.tcignited.history.SuiteInvocationHistoryDao;
import org.apache.ignite.tcignited.mute.MuteDao;
import org.apache.ignite.tcignited.mute.MuteSync;
import org.apache.ignite.tcservice.ITeamcityConn;
import org.apache.ignite.tcservice.model.agent.Agent;
import org.apache.ignite.tcservice.model.conf.Project;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.apache.ignite.tcservice.model.result.Build;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.tcservice.model.hist.BuildRef.STATUS_UNKNOWN;

/**
 *
 */
public class TeamcityIgnitedImpl implements ITeamcityIgnited {
    /** Default project id. */
    public static final String DEFAULT_PROJECT_ID = "IgniteTests24Java8";

    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TeamcityIgnitedImpl.class);

    /** Max build id diff to enforce reload during incremental refresh. */
    public static final int MAX_ID_DIFF_TO_ENFORCE_CONTINUE_SCAN = 3000;

    /** Server (service) code. */
    private String srvCode;

    /** Pure HTTP Connection API. */
    private ITeamcityConn conn;

    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** Build reference DAO. */
    @Inject private BuildRefDao buildRefDao;

    /** Build reference (short version of build data) sync. */
    @Inject private BuildRefSync buildRefSync;

    /** Build condition DAO. */
    @Inject private BuildConditionDao buildConditionDao;

    /** Build DAO. */
    @Inject private FatBuildDao fatBuildDao;

    /** Build Sync. */
    @Inject private ProactiveFatBuildSync fatBuildSync;

    /** Mute DAO. */
    @Inject private MuteDao muteDao;

    /** Mute Sync. */
    @Inject private MuteSync muteSync;

    /** Changes DAO. */
    @Inject private ChangeDao changesDao;

    /** Changes sync class. */
    @Inject private ChangeSync changeSync;

    /** BuildType reference DAO. */
    @Inject private BuildTypeRefDao buildTypeRefDao;

    /** BuildType DAO. */
    @Inject private BuildTypeDao buildTypeDao;

    /** BuildType DAO. */
    @Inject private BuildTypeSync buildTypeSync;

    /** Run history DAO. */
    @Inject private RunHistCompactedDao runHistCompactedDao;

    /** Run history sync. */
    @Inject private RunHistSync runHistSync;

    /** Logger check result DAO. */
    @Inject private BuildLogCheckResultDao logCheckResDao;

    /** History DAO. */
    @Inject private SuiteInvocationHistoryDao histDao;

    /** History collector. */
    @Inject private HistoryCollector histCollector;

    /** Strings compactor. */
    @Inject private IStringCompactor compactor;

    @Inject private BranchEquivalence branchEquivalence;

    /** Server ID mask for cache Entries. */
    private int srvIdMaskHigh;

    public void init(ITeamcityConn conn) {
        String srvCode = conn.serverCode();

        this.srvCode = srvCode;
        this.conn = conn;

        srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvCode);
        buildRefDao.init(); //todo init somehow in auto
        buildConditionDao.init();
        fatBuildDao.init();
        changesDao.init();
        runHistCompactedDao.init();
        muteDao.init();
        logCheckResDao.init();
        histDao.init();
    }

    /**
     * @param taskName Task name.
     * @return Task name concatenated with server name.
     */
    @Nonnull
    private String taskName(String taskName) {
        return ITeamcityIgnited.class.getSimpleName() +"." + taskName + "." + srvCode;
    }

    /** {@inheritDoc} */
    @Override public String serverCode() {
        return srvCode;
    }

    /** {@inheritDoc} */
    @Override public ITcServerConfig config() {
        return conn.config();
    }

    /** {@inheritDoc} */
    @Override public List<BuildRefCompacted> getFinishedBuildsCompacted(
        @Nullable String buildTypeId,
        @Nullable String branchName,
        @Nullable Date sinceDate,
        @Nullable Date untilDate) {
        final int allDatesOutOfBounds = -1;
        final int someDatesOutOfBounds = -2;
        final int invalidVal = -3;
        final int unknownStatus = compactor.getStringId(STATUS_UNKNOWN);

        List<BuildRefCompacted> buildRefs = getAllBuildsCompacted(buildTypeId, branchName)
            .stream()
            .filter(b -> b.isFinished(compactor))
            .filter(b -> b.status() != unknownStatus) //check build is not cancelled
            .sorted(Comparator.comparing(BuildRefCompacted::id))
            .collect(Collectors.toList());

        if (buildRefs.isEmpty())
            return Collections.emptyList();

        int idSince = 0;
        int idUntil = buildRefs.size() - 1;

        if (sinceDate != null) {
            idSince = binarySearchDate(buildRefs, 0, buildRefs.size(), sinceDate, true);
            idSince = (idSince == someDatesOutOfBounds) ? 0 : idSince;
        }

        if (untilDate != null) {
            idUntil = (idSince < 0) ? allDatesOutOfBounds :
                binarySearchDate(buildRefs, idSince, buildRefs.size(), untilDate, false);
            idUntil = (idUntil == someDatesOutOfBounds) ? buildRefs.size() - 1 : idUntil;
        }

        if (idSince == invalidVal || idUntil == invalidVal) {
            AtomicBoolean stopFilter = new AtomicBoolean();
            AtomicBoolean addBuild = new AtomicBoolean();

            return buildRefs.stream()
                .filter(b -> {
                    if (stopFilter.get())
                        return addBuild.get();

                    Date date = getBuildStartDate(b.id());

                    if (date == null)
                        return false;

                    if (sinceDate != null && untilDate != null)
                        if ((date.after(sinceDate) || date.equals(sinceDate)) &&
                            (date.before(untilDate) || date.equals(untilDate)))
                            return true;
                        else {
                            if (date.after(untilDate)) {
                                stopFilter.set(true);
                                addBuild.set(false);
                            }

                            return false;
                        }
                    else if (sinceDate != null) {
                        if (date.after(sinceDate) || date.equals(sinceDate)) {
                            stopFilter.set(true);
                            addBuild.set(true);

                            return true;
                        }

                        return false;
                    }
                    else {
                        if (date.after(untilDate)) {
                            stopFilter.set(true);
                            addBuild.set(false);

                            return false;
                        }

                        return true;
                    }
                })
                .collect(Collectors.toList());
        } else if (idSince == allDatesOutOfBounds || idUntil == allDatesOutOfBounds)
            return Collections.emptyList();
        else
            return buildRefs.subList(idSince, idUntil + 1);
    }

    /**
     * @param buildRefs Build refs list.
     * @param fromIdx From index.
     * @param toIdx To index.
     * @param key Key.
     * @param since {@code true} If key is sinceDate, {@code false} is untilDate.
     *
     * @return {@value >= 0} Build id from list with min interval between key. If since {@code true}, min interval
     * between key and same day or later. If since {@code false}, min interval between key and same day or earlier;
     * {@value -1} All dates out of bounds. If sinceDate after last list element date or untilDate before first list
     * element;
     * {@value -2} Some dates out of bounds. If sinceDate before first list element or untilDate after last list
     * element;
     * {@value -3} Invalid value. If method get null or fake stub build.
     */
    private int binarySearchDate(List<BuildRefCompacted> buildRefs, int fromIdx, int toIdx, Date key, boolean since) {
        final int allDatesOutOfBounds = -1;
        final int someDatesOutOfBounds = -2;
        final int invalidVal = -3;

        int low = fromIdx;
        int high = toIdx - 1;
        long minDiff = key.getTime();
        int minDiffId = since ? low : high;
        long temp;

        Date highBuildStartDate = getBuildStartDate(buildRefs.get(high).id());
        Date lowBuildStartDate = getBuildStartDate(buildRefs.get(low).id());

        if (highBuildStartDate != null) {
            if (highBuildStartDate.before(key))
                return since ? allDatesOutOfBounds : someDatesOutOfBounds;
        }

        if (lowBuildStartDate != null) {
            if (lowBuildStartDate.after(key))
                return since ? someDatesOutOfBounds : allDatesOutOfBounds;
        }

        while (low <= high) {
            int mid = (low + high) >>> 1;
            Date midValStartDate = getBuildStartDate(buildRefs.get(mid).id());

            if (midValStartDate != null) {
                if (midValStartDate.after(key))
                    high = mid - 1;
                else if (midValStartDate.before(key))
                    low = mid + 1;
                else
                    return mid;

                temp = midValStartDate.getTime() - key.getTime();

                if ((temp > 0 == since) && (Math.abs(temp) < minDiff)) {
                    minDiff = Math.abs(temp);
                    minDiffId = mid;
                }
            } else
                return invalidVal;
        }
        return minDiffId;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRefCompacted> getAllBuildsCompacted(
            @Nullable String buildTypeId,
            @Nullable String branchName) {
        ensureActualizeRequested();

        return buildRefDao.getAllBuildsCompacted(srvIdMaskHigh, buildTypeId, branchEquivalence.branchForQuery(branchName));
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRefCompacted> getQueuedBuildsCompacted(
        @Nullable String branchName) {
        ensureActualizeRequested();

        Integer stateQueuedId = compactor.getStringIdIfPresent(BuildRef.STATE_QUEUED);
        if (stateQueuedId == null)
            return Collections.emptyList();

        Set<Integer> branchNameIds = branchEquivalence.branchForQuery(branchName).stream().map(str -> compactor.getStringIdIfPresent(str))
            .filter(Objects::nonNull).collect(Collectors.toSet());

        List<BuildRefCompacted> res = new ArrayList<>();

        branchNameIds.forEach(br -> {
            List<BuildRefCompacted> builds = buildRefDao.getBuildsForBranch(srvIdMaskHigh, br);

            if(builds.isEmpty())
                return;

            builds.stream().filter(buildRef -> buildRef.state() == stateQueuedId).forEach(res::add);
        });

        return res;
    }


    /** {@inheritDoc} */
    @Override public Set<MuteInfo> getMutes(String projectId) {
        muteSync.ensureActualizeMutes(taskName("actualizeMutes"), projectId, srvIdMaskHigh, conn);

        return muteDao.getMutes(srvIdMaskHigh);
    }



    /** {@inheritDoc} */
    @AutoProfiling
    @Override @Nonnull public List<Integer> getLastNBuildsFromHistory(String btId, String branchForTc, int cnt) {
        List<BuildRefCompacted> hist = getAllBuildsCompacted(btId, branchForTc);

        List<Integer> chains = hist.stream()
            .filter(ref -> !ref.isFakeStub())
            .filter(t -> !t.isCancelled(compactor))
            .filter(t -> t.isFinished(compactor))
            .sorted(Comparator.comparing(BuildRefCompacted::id).reversed())
            .limit(cnt)
            .map(BuildRefCompacted::id)
            .collect(Collectors.toList());

        if (chains.isEmpty()) {
            // probably there are no not-cacelled builds at all, check for cancelled
            chains = hist.stream()
                .filter(ref -> !ref.isFakeStub())
                .filter(t -> t.isFinished(compactor))
                .sorted(Comparator.comparing(BuildRefCompacted::id).reversed())
                .map(BuildRefCompacted::id)
                .limit(cnt)
                .collect(Collectors.toList());
        }

        return chains;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public IRunHistory getTestRunHist(String testName, @Nullable String branch) {
        return runHistCompactedDao.getTestRunHist(srvIdMaskHigh, testName, branch);
    }

    /** {@inheritDoc} */
    @Nullable
    @AutoProfiling
    @Override public IRunHistory getSuiteRunHist(String suiteId, @Nullable String branch){
        return runHistCompactedDao.getSuiteRunHist(srvIdMaskHigh, suiteId, branch);
    }

    /** {@inheritDoc} */
    @Nullable @Override public ISuiteRunHistory getSuiteRunHist(@Nullable Integer buildTypeId, @Nullable Integer normalizedBaseBranch) {
        if (buildTypeId == null || normalizedBaseBranch == null)
            return null;

        if (buildTypeId < 0 || normalizedBaseBranch < 0)
            return null;

        return histCollector.getSuiteRunHist(srvIdMaskHigh, buildTypeId, normalizedBaseBranch);
    }

    /** {@inheritDoc} */
    @Nullable @Override public IRunHistory getTestRunHist(int testName, @Nullable Integer buildTypeId,
        @Nullable Integer normalizedBaseBranch) {
        if (buildTypeId == null || normalizedBaseBranch == null)
            return null;

        if (testName < 0 || buildTypeId < 0 || normalizedBaseBranch < 0)
            return null;

        return histCollector.getTestRunHist(srvIdMaskHigh, testName, buildTypeId, normalizedBaseBranch);
    }

    /** {@inheritDoc} */
    @Nullable @Override public IRunStat getSuiteRunStatAllBranches(String suiteBuildTypeId) {
        return runHistCompactedDao.getSuiteRunStatAllBranches(srvIdMaskHigh, suiteBuildTypeId);
    }

    /** {@inheritDoc} */
    @Override public List<String> getAllProjectsIds() {
        return conn.getProjects().stream().map(Project::id).collect(Collectors.toList());
    }

    @Override
    public List<Agent> agents(boolean connected, boolean authorized) {
        return conn.agents(connected, authorized);
    }

    @Nullable
    @Override
    public File downloadAndCacheBuildLog(int buildId) {
        return conn.downloadAndCacheBuildLog(buildId);
    }

    /** {@inheritDoc} */
    @Override public List<String> getCompositeBuildTypesIdsSortedByBuildNumberCounter(String projectId) {
        return buildTypeSync.getCompositeBuildTypesIdsSortedByBuildNumberCounter(srvIdMaskHigh, projectId, conn);
    }

    /** {@inheritDoc} */
    @Override public List<BuildTypeRefCompacted> getAllBuildTypesCompacted(String projectId) {
        return buildTypeSync.getAllBuildTypesCompacted(srvIdMaskHigh, projectId, conn);
    }

    /** {@inheritDoc} */
    @Override public BuildTypeRefCompacted getBuildTypeRef(String buildTypeId) {
        return buildTypeRefDao.getBuildTypeRef(srvIdMaskHigh, buildTypeId);
    }

    /** {@inheritDoc} */
    @Override public BuildTypeCompacted getBuildType(String buildTypeId) {

        return buildTypeDao.getFatBuildType(srvIdMaskHigh, buildTypeId);
    }

    /**
     * Enables scheduling for build refs/builds/history sync
     */
    public void ensureActualizeRequested() {
        scheduler.sheduleNamed(taskName("actualizeRecentBuildRefs"), () -> actualizeRecentBuildRefs(srvCode), 2, TimeUnit.MINUTES);

        buildRefSync.ensureActualizeRequested();

        // schedule find missing later
        fatBuildSync.ensureActualizationRequested(srvCode, conn);

        //todo remove unused code
        // runHistSync.invokeLaterFindMissingHistory(srvCode);
    }

    /** {@inheritDoc} */
    @Override public Build triggerBuild(String buildTypeId, String branchName, boolean cleanRebuild, boolean queueAtTop,
        Map<String, Object> buildParms) {
        Build build = conn.triggerBuild(buildTypeId, branchName, cleanRebuild, queueAtTop, buildParms);

        //todo may add additional parameter: load builds into DB in sync/async fashion
        buildRefSync.runActualizeBuildRefs(srvCode, false, Sets.newHashSet(build.getId()), conn);

        return build;
    }

    /** {@inheritDoc} */
    @Override public boolean buildIsValid(int buildId) {
        BuildCondition cond = buildConditionDao.getBuildCondition(srvIdMaskHigh, buildId);

        return cond == null || cond.isValid;
    }

    /** {@inheritDoc} */
    @Override public boolean setBuildCondition(BuildCondition cond) {
        return buildConditionDao.setBuildCondition(srvIdMaskHigh, cond);
    }

    /**
     * @param buildId Build id.
     * @return build start date or null if build is fake stub or start date is not specified.
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable public Date getBuildStartDate(int buildId) {
        final Long buildStartTime = getBuildStartTs(buildId);

        return buildStartTime != null ? new Date(buildStartTime) : null;
    }

    @GuavaCached(maximumSize = 5000, cacheNullRval = false)
    @AutoProfiling
    public Long getBuildStartTs(int buildId) {
        Long buildStartTime = getBuildStartTime(buildId);
        if (buildStartTime != null)
            return buildStartTime;

        if(logger.isDebugEnabled()) {
            String msg = "Loading build [" + buildId + "] start date";
            logger.debug(msg);
        }

        FatBuildCompacted highBuild = getFatBuild(buildId, SyncMode.LOAD_NEW);
        if (highBuild == null || highBuild.isFakeStub())
            return null;

        long ts = highBuild.getStartDateTs();

        if (ts > 0) {
            runHistCompactedDao.setBuildStartTime(srvIdMaskHigh, buildId, ts);

            return ts;
        } else
            return null;
    }

    //@GuavaCached(maximumSize = 100000, expireAfterAccessSecs = 90, softValues = true)
    public Long getBuildStartTime(int buildId) {
        return histCollector.getBuildStartTime(srvIdMaskHigh, buildId);
    }

    /** {@inheritDoc} */
    @GuavaCached(maximumSize = 500, expireAfterAccessSecs = 30, softValues = true)
    @Override public FatBuildCompacted getFatBuild(int buildId, SyncMode mode) {
        FatBuildCompacted existingBuild = getFatBuildFromIgnite(buildId);

        if (mode == SyncMode.NONE) {
            // providing fake builds
            return existingBuild != null ? existingBuild : new FatBuildCompacted().setFakeStub(true);
        }

        FatBuildCompacted savedVer = fatBuildSync.loadBuild(conn, buildId, existingBuild, mode);

        //build was modified, probably we need also to update reference accordingly
        if (savedVer == null)
            return existingBuild;

        return savedVer;
    }

    protected FatBuildCompacted getFatBuildFromIgnite(int buildId) {
        ensureActualizeRequested();

        return fatBuildDao.getFatBuild(srvIdMaskHigh, buildId);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public Collection<ChangeCompacted> getAllChanges(int[] changeIds) {
        final Map<Long, ChangeCompacted> all = changesDao.getAll(srvIdMaskHigh, changeIds);

        final Map<Integer, ChangeCompacted> changes = new HashMap<>();

        //todo support change version upgrade
        all.forEach((k, v) -> {
            final int changeId = ChangeDao.cacheKeyToChangeId(k);

            changes.put(changeId, v);
        });

        for (int changeId : changeIds) {
            if (!changes.containsKey(changeId)) {
                final ChangeCompacted change = changeSync.reloadChange(srvIdMaskHigh, changeId, conn);

                changes.put(changeId, change);
            }
        }

        return changes.values();
    }

    String actualizeRecentBuildRefs() {
        return actualizeRecentBuildRefs(srvCode);
    }

    /**
     *
     * @param srvNme TC service name
     */
    @SuppressWarnings("WeakerAccess")
    @MonitoredTask(name = "Prepare Actualize BuildRefs(srv, full resync)", nameExtArgsIndexes = {0})
    protected String actualizeRecentBuildRefs(String srvNme) {
        List<BuildRefCompacted> running = buildRefDao.getQueuedAndRunning(srvIdMaskHigh);

        Set<Integer> paginateUntil = new HashSet<>();
        Set<Integer> directUpload = new HashSet<>();

        List<Integer> runningIds = running.stream().map(BuildRefCompacted::id).collect(Collectors.toList());
        OptionalInt max = runningIds.stream().mapToInt(i -> i).max();
        if (max.isPresent()) {
            runningIds.forEach(id -> {
                if (id > (max.getAsInt() - MAX_ID_DIFF_TO_ENFORCE_CONTINUE_SCAN))
                    paginateUntil.add(id);
                else
                    directUpload.add(id);
            });
        }

        int cntFreshBuilds = paginateUntil.size();

        //schedule direct reload for Fat Builds for all queued too-old builds
        fatBuildSync.scheduleBuildsLoad(conn, directUpload);

        buildRefSync.runActualizeBuildRefs(srvCode, false, paginateUntil, conn);

        int freshButNotFoundByBuildsRefsScan = paginateUntil.size();
        if (!paginateUntil.isEmpty()) {
            //some builds may stuck in the queued or running, enforce loading now
            fatBuildSync.doLoadBuilds(-1, srvCode, conn, paginateUntil);
        }

        // schedule full resync later
        scheduler.invokeLater(this::sheduleResyncBuildRefs, 15, TimeUnit.MINUTES);

        return "Build queue " + running.size() + ", relatively fresh: " + cntFreshBuilds +
             ", fresh but not found by scan: " + freshButNotFoundByBuildsRefsScan +
            ", old builds sheduled " + directUpload.size();
    }

    /**
     *
     */
    private void sheduleResyncBuildRefs() {
        scheduler.sheduleNamed(taskName("fullReindex"), this::fullReindex, 2, TimeUnit.HOURS);
    }

    /**
     *
     */
    void fullReindex() {
        buildRefSync.runActualizeBuildRefs(srvCode, true, null, conn);
    }
}
