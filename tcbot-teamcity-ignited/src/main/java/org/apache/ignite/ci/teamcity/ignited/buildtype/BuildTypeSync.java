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

package org.apache.ignite.ci.teamcity.ignited.buildtype;

import com.google.common.base.Throwables;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcservice.model.conf.BuildType;
import org.apache.ignite.tcservice.model.conf.bt.BuildTypeFull;
import org.apache.ignite.tcservice.ITeamcityConn;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.tcignited.TeamcityIgnitedImpl.DEFAULT_PROJECT_ID;


public class BuildTypeSync {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(BuildTypeSync.class);

    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** BuildType reference DAO. */
    @Inject private BuildTypeRefDao buildTypeRefDao;

    /** BuildType DAO. */
    @Inject private BuildTypeDao buildTypeDao;

    /** Saved list of composite suites for "IgniteTests24Java8" project. */
    private List<String> compositeBuildTypesIdsForDfltProject = Collections.emptyList();

    /**
     * Return list of composite suite ids sorted by number of snapshot dependency.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @param conn Pure HTTP Connection API.
     * @return List of composite buildType ids.
     */
    public List<String> getCompositeBuildTypesIdsSortedByBuildNumberCounter(int srvIdMaskHigh, String projectId, ITeamcityConn conn) {
        ensureActualizeBuildTypeRefsRequested(srvIdMaskHigh, projectId, conn);
        ensureActualizeBuildTypesRequested(srvIdMaskHigh, projectId, conn);

        return (projectId.equals(DEFAULT_PROJECT_ID) && !compositeBuildTypesIdsForDfltProject.isEmpty()) ?
            compositeBuildTypesIdsForDfltProject :
            buildTypeDao.compositeBuildTypesIdsSortedByBuildNumberCounter(srvIdMaskHigh, projectId);
    }

    /**
     * Return list of compacted references to project suites.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @param conn Pure HTTP Connection API.
     * @return List of compacted references to buildTypes.
     */
    public List<BuildTypeRefCompacted> getAllBuildTypesCompacted(int srvIdMaskHigh, String projectId, ITeamcityConn conn) {
        ensureActualizeBuildTypeRefsRequested(srvIdMaskHigh, projectId, conn);

        return buildTypeRefDao.buildTypesCompacted(srvIdMaskHigh, projectId);
    }

    /**
     * Actualize saved list of composite suites for project.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     */
    private void actualizeSavedCompositeBuildTypesIds(int srvIdMaskHigh, String projectId) {
        if (projectId.equals(DEFAULT_PROJECT_ID)) {
            compositeBuildTypesIdsForDfltProject =
                buildTypeDao.compositeBuildTypesIdsSortedByBuildNumberCounter(srvIdMaskHigh, projectId);
        }
    }

    /**
     * Ensure actualize BuildTypeRefs requested. Add this task to scheduler.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @param conn Pure HTTP Connection API.
     */
    private void ensureActualizeBuildTypeRefsRequested(int srvIdMaskHigh, String projectId, ITeamcityConn conn) {
        scheduler.sheduleNamed(taskName("actualizeAllBuildTypeRefs", conn.serverCode(), projectId),
            () -> reindexBuildTypeRefs(srvIdMaskHigh, projectId, conn), 4, TimeUnit.HOURS);
    }

    /**
     * Ensure actualize BuildTypes requested. Add this task to scheduler.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @param conn Pure HTTP Connection API.
     */
    private void ensureActualizeBuildTypesRequested(int srvIdMaskHigh, String projectId, ITeamcityConn conn) {
        scheduler.sheduleNamed(taskName("actualizeAllBuildTypeRefs", "actualizeAllBuildTypes", conn.serverCode()),
            () -> reindexBuildTypes(srvIdMaskHigh, projectId, conn), 24, TimeUnit.HOURS);
    }

    /**
     * Re-index all references to "IgniteTests24Java8" suites.
     */
    private void reindexBuildTypeRefs(int srvIdMaskHigh, String projectId, ITeamcityConn conn) {
        runActualizeBuildTypeRefs(srvIdMaskHigh, projectId, conn);
    }

    /**
     * Re-index all "IgniteTests24Java8" suites.
     */
    private void reindexBuildTypes(int srvIdMaskHigh, String projectId, ITeamcityConn conn) {
        runActualizeBuildTypes(srvIdMaskHigh, projectId, conn);
    }

    /**
     * Re-index all project suites.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @param conn Pure HTTP Connection API.
     * @return Statistics with the number of updated and requested buildTypes.
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Reindex BuildTypes (projectId)", nameExtArgsIndexes = {1})
    @AutoProfiling
    protected String runActualizeBuildTypes(int srvIdMaskHigh, String projectId, ITeamcityConn conn) {
        Map<String, Boolean> buildTypeIds = buildTypeRefDao.allBuildTypeIds(srvIdMaskHigh, projectId);

        int updated = 0;
        int removed = 0;

        for (Map.Entry<String, Boolean> entry : buildTypeIds.entrySet()) {
            String id = entry.getKey();
            boolean rmv = entry.getValue();

            if (rmv)
                removed += markRemoved(srvIdMaskHigh, id) ? 1 : 0;
            else {
                try {
                    BuildTypeFull buildType = conn.getBuildType(id);

                    BuildTypeCompacted exBuildType = buildTypeDao.getFatBuildType(srvIdMaskHigh, id);

                    updated += (buildTypeDao.saveBuildType(srvIdMaskHigh, buildType, exBuildType) != null) ? 1 : 0;
                } catch (Exception e) {
                    if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                        logger.info("Loading buildType [" + id + "] for server [" + conn.serverCode() + "] failed:" +
                            e.getMessage(), e);

                        removed += markRemoved(srvIdMaskHigh, id) ? 1 : 0;
                    } else
                        throw ExceptionUtil.propagateException(e);
                }
            }

        }

        if (updated != 0 || removed != 0)
            actualizeSavedCompositeBuildTypesIds(srvIdMaskHigh, projectId);

        return "BuildTypes updated " + updated +
            (removed == 0 ? "" : " and mark as removed " + removed) +
            " from " + buildTypeIds.size() + " requested";
    }

    /**
     * Mark buildType removed from TC server.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeId Build type id.
     *
     * @return {@code True} if build mark removed, {@code False} if build already marked or not in cache.
     */
    private boolean markRemoved(int srvIdMaskHigh, String buildTypeId) {
        BuildTypeCompacted existingBuildType = buildTypeDao.getFatBuildType(srvIdMaskHigh, buildTypeId);

        if (existingBuildType != null) {
            existingBuildType.markRemoved();

            return buildTypeDao.save(srvIdMaskHigh, existingBuildType);
        }

        return false;
    }

    /**
     * Re-index all references to project suites.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @param conn Pure HTTP Connection API.
     * @return Statistics with the number of updated and requested buildTypeRefs.
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Reindex BuildTypeRefs (projectId)", nameExtArgsIndexes = {1})
    @AutoProfiling
    protected String runActualizeBuildTypeRefs(int srvIdMaskHigh, String projectId, ITeamcityConn conn) {
        List<BuildType> tcData = conn.getBuildTypes(projectId);

        Set<Long> buildsUpdated = buildTypeRefDao.saveChunk(srvIdMaskHigh, Collections.unmodifiableList(tcData));

        Set<String> rmvBuildTypes = buildTypeRefDao.markMissingBuildsAsRemoved(srvIdMaskHigh,
            tcData.stream().map(BuildType::getId).collect(Collectors.toList()), projectId);

        if (!(buildsUpdated.isEmpty() && rmvBuildTypes.isEmpty())) {
            actualizeSavedCompositeBuildTypesIds(srvIdMaskHigh, projectId);

            runActualizeBuildTypes(srvIdMaskHigh, projectId, conn);
        }

        return "BuildTypeRefs updated " + buildsUpdated.size() +
            (rmvBuildTypes.isEmpty() ? "" : " and mark as removed " + rmvBuildTypes.size()) +
            " from " + (tcData.size() + rmvBuildTypes.size()) + " requested";
    }

    /**
     * @param taskName Task name.
     * @param srvName Server name.
     * @param prjName Project name.
     */
    @Nonnull
    private String taskName(String taskName, String srvName, String prjName) {
        return BuildTypeSync.class.getSimpleName() + "." + taskName + "." + srvName + "." + prjName;
    }
}
