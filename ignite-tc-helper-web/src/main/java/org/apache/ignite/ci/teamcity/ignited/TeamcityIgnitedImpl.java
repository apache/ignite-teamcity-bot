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
package org.apache.ignite.ci.teamcity.ignited;


import com.google.common.collect.Sets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildCondition;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildConditionDao;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeDao;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeSync;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProactiveFatBuildSync;
import org.apache.ignite.ci.teamcity.pure.ITeamcityConn;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.apache.ignite.ci.tcmodel.hist.BuildRef.STATUS_UNKNOWN;

public class TeamcityIgnitedImpl implements ITeamcityIgnited {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TeamcityIgnitedImpl.class);

    /** Max build id diff to enforce reload during incremental refresh. */
    public static final int MAX_ID_DIFF_TO_ENFORCE_CONTINUE_SCAN = 3000;

    /**
     * Max builds to check during incremental sync. If this value is reached (50 pages) and some stuck builds still not
     * found, then iteration stops
     */
    public static final int MAX_INCREMENTAL_BUILDS_TO_CHECK = 5000;

    /** Server id. */
    private String srvNme;

    /** Pure HTTP Connection API. */
    private ITeamcityConn conn;

    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** Build reference DAO. */
    @Inject private BuildRefDao buildRefDao;

    /** Build condition DAO. */
    @Inject private BuildConditionDao buildConditionDao;

    /** Build DAO. */
    @Inject private FatBuildDao fatBuildDao;

    @Inject private ProactiveFatBuildSync buildSync;

    /** Changes DAO. */
    @Inject private ChangeDao changesDao;

    /** Changes DAO. */
    @Inject private ChangeSync changeSync;

    /** Changes DAO. */
    @Inject private IStringCompactor compactor;

    /** Server ID mask for cache Entries. */
    private int srvIdMaskHigh;

    public void init(String srvId, ITeamcityConn conn) {
        this.srvNme = srvId;
        this.conn = conn;

        srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);
        buildRefDao.init(); //todo init somehow in auto
        buildConditionDao.init();
        fatBuildDao.init();
        changesDao.init();
    }


    @NotNull
    private String taskName(String taskName) {
        return ITeamcityIgnited.class.getSimpleName() +"." + taskName + "." + srvNme;
    }

    /** {@inheritDoc} */
    @Override public String serverId() {
        return srvNme;
    }

    /** {@inheritDoc} */
    @Override public String host() {
        return conn.host();
    }

    /** {@inheritDoc} */
    public List<BuildRefCompacted> getFinishedBuildsCompacted(
        @Nullable String buildTypeId,
        @Nullable String branchName,
        @Nullable Date sinceDate,
        @Nullable Date untilDate) {
        final int allDatesOutOfBounds = -1;
        final int someDatesOutOfBounds = -2;
        final int invalidVal = -3;
        final int unknownStatus = compactor.getStringId(STATUS_UNKNOWN);

        List<BuildRefCompacted> buildRefs = getBuildHistoryCompacted(buildTypeId, branchName)
            .stream().filter(b -> b.status() != unknownStatus).collect(Collectors.toList());

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

                    FatBuildCompacted build = getFatBuild(b.id());

                    if (build == null || build.isFakeStub())
                        return false;

                    Date date = build.getStartDate();

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

        FatBuildCompacted highBuild = getFatBuild(buildRefs.get(high).id());
        FatBuildCompacted lowBuild = getFatBuild(buildRefs.get(low).id());

        if (highBuild != null && !highBuild.isFakeStub()){
            if (highBuild.getStartDate().before(key))
                return since ? allDatesOutOfBounds : someDatesOutOfBounds;
        }

        if (lowBuild != null && !lowBuild.isFakeStub()){
            if (lowBuild.getStartDate().after(key))
                return since ? someDatesOutOfBounds : allDatesOutOfBounds;
        }

        while (low <= high) {
            int mid = (low + high) >>> 1;
            FatBuildCompacted midVal = getFatBuild(buildRefs.get(mid).id());

            if (midVal != null && !midVal.isFakeStub()) {
                if (midVal.getStartDate().after(key))
                    high = mid - 1;
                else if (midVal.getStartDate().before(key))
                    low = mid + 1;
                else
                    return mid;

                temp = midVal.getStartDate().getTime() - key.getTime();

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
    @Override public List<BuildRef> getBuildHistory(
            @Nullable String buildTypeId,
            @Nullable String branchName) {
        ensureActualizeRequested();

        String bracnhNameQry = branchForQuery(branchName);

        return buildRefDao.findBuildsInHistoryCompacted(srvIdMaskHigh, buildTypeId, bracnhNameQry)
            .stream().map(compacted -> compacted.toBuildRef(compactor))
            .collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRefCompacted> getBuildHistoryCompacted(
            @Nullable String buildTypeId,
            @Nullable String branchName) {
        ensureActualizeRequested();

        String bracnhNameQry = branchForQuery(branchName);

        return buildRefDao.findBuildsInHistoryCompacted(srvIdMaskHigh, buildTypeId, bracnhNameQry);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override @NotNull public List<Integer> getLastNBuildsFromHistory(String btId, String branchForTc, int cnt) {
        List<BuildRefCompacted> hist = getBuildHistoryCompacted(btId, branchForTc);

        List<Integer> chains = hist.stream()
            .filter(ref -> !ref.isFakeStub())
            .filter(t -> t.isNotCancelled(compactor))
            .sorted(Comparator.comparing(BuildRefCompacted::id).reversed())
            .limit(cnt)
            .map(BuildRefCompacted::id)
            .collect(Collectors.toList());

        if (chains.isEmpty()) {
            // probably there are no not-cacelled builds at all
            chains = hist.stream()
                .filter(ref -> !ref.isFakeStub())
                .sorted(Comparator.comparing(BuildRefCompacted::id).reversed())
                .map(BuildRefCompacted::id)
                .limit(cnt)
                .collect(Collectors.toList());
        }
        return chains;
    }

    public String branchForQuery(@Nullable String branchName) {
        String bracnhNameQry;
        if (ITeamcity.DEFAULT.equals(branchName))
            bracnhNameQry = "refs/heads/master";
        else
            bracnhNameQry = branchName;
        return bracnhNameQry;
    }

    public void ensureActualizeRequested() {
        scheduler.sheduleNamed(taskName("actualizeRecentBuildRefs"), this::actualizeRecentBuildRefs, 2, TimeUnit.MINUTES);
    }

    /** {@inheritDoc} */
    @Override public Build triggerBuild(String buildTypeId, String branchName, boolean cleanRebuild, boolean queueAtTop) {
        Build build = conn.triggerBuild(buildTypeId, branchName, cleanRebuild, queueAtTop);

        //todo may add additional parameter: load builds into DB in sync/async fashion
        runActualizeBuildRefs(srvNme, false, Sets.newHashSet(build.getId()));

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

    /** {@inheritDoc} */
    @Override public FatBuildCompacted getFatBuild(int buildId, boolean acceptQueued) {
        ensureActualizeRequested();
        FatBuildCompacted existingBuild = fatBuildDao.getFatBuild(srvIdMaskHigh, buildId);

        FatBuildCompacted savedVer = buildSync.loadBuild(conn, buildId, existingBuild, acceptQueued);

        //build was modified, probably we need also to update reference accordindly
        if (savedVer != null)
            buildRefDao.save(srvIdMaskHigh, new BuildRefCompacted(savedVer));

        return savedVer == null ? existingBuild : savedVer;
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


    /**
     *
     */
    void actualizeRecentBuildRefs() {
        // schedule find missing later
        buildSync.invokeLaterFindMissingByBuildRef(srvNme, conn);

        List<BuildRefCompacted> running = buildRefDao.getQueuedAndRunning(srvIdMaskHigh);

        Set<Integer> paginateUntil = new HashSet<>();
        Set<Integer> directUpload = new HashSet<>();

        List<Integer> runningIds = running.stream().map(BuildRefCompacted::id).collect(Collectors.toList());
        OptionalInt max = runningIds.stream().mapToInt(i -> i).max();
        if (max.isPresent()) {
            runningIds.forEach(id->{
                if(id > (max.getAsInt() - MAX_ID_DIFF_TO_ENFORCE_CONTINUE_SCAN))
                    paginateUntil.add(id);
                else
                    directUpload.add(id);
            });
        }
        //schedule direct reload for Fat Builds for all queued too-old builds
        buildSync.scheduleBuildsLoad(conn, directUpload);

        runActualizeBuildRefs(srvNme, false, paginateUntil);

        if(!paginateUntil.isEmpty()) {
            //some builds may stuck in the queued or running, enforce loading now
            buildSync.doLoadBuilds(-1, srvNme, conn, paginateUntil);
        }

        // schedule full resync later
        scheduler.invokeLater(this::sheduleResyncBuildRefs, 15, TimeUnit.MINUTES);
    }

    /**
     *
     */
    private void sheduleResyncBuildRefs() {
        scheduler.sheduleNamed(taskName("fullReindex"), this::fullReindex, 120, TimeUnit.MINUTES);
    }

    /**
     *
     */
    void fullReindex() {
        runActualizeBuildRefs(srvNme, true, null);
    }


    /**
     * @param srvId Server id.
     * @param fullReindex Reindex all builds from TC history.
     * @param mandatoryToReload [in/out] Build ID should be found before end of sync. Ignored if fullReindex mode.
     *
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Actualize BuildRefs(srv, full resync)", nameExtArgsIndexes = {0, 1})
    @AutoProfiling
    protected String runActualizeBuildRefs(String srvId, boolean fullReindex,
                                           @Nullable Set<Integer> mandatoryToReload) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();
        List<BuildRef> tcDataFirstPage = conn.getBuildRefsPage(null, outLinkNext);

        Set<Long> buildsUpdated = buildRefDao.saveChunk(srvIdMaskHigh, tcDataFirstPage);
        int totalUpdated = buildsUpdated.size();
        buildSync.scheduleBuildsLoad(conn, cacheKeysToBuildIds(buildsUpdated));

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
            buildSync.scheduleBuildsLoad(conn, cacheKeysToBuildIds(curChunkBuildsSaved));

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

    @NotNull private List<Integer> cacheKeysToBuildIds(Collection<Long> cacheKeysUpdated) {
        return cacheKeysUpdated.stream().map(BuildRefDao::cacheKeyToBuildId).collect(Collectors.toList());
    }
}
