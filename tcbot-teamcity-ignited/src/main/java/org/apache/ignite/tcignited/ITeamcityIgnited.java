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

import com.google.common.base.Strings;
import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildCondition;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.RevisionCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.internal.util.typedef.T2;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.history.ISuiteRunHistory;
import org.apache.ignite.tcservice.model.agent.Agent;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.apache.ignite.tcservice.model.result.Build;

/**
 *
 */
public interface ITeamcityIgnited {
    /**
     * @return Internal serverID (Code) as string
     */
    public String serverCode();

    /**
     * @return TeamCity configuration.
     */
    public ITcServerConfig config();

    /**
     * @return Normalized Host address, ends with '/'.
     */
    public default String host() {return config().host();}

    /**
     * Return all builds for branch and suite, without relation to its status.
     *
     * @param buildTypeId Build type identifier.
     * @param branchName Branch name.
     * @return list of builds in history, includes all statuses: queued, running, etc
     */
    public List<BuildRefCompacted> getAllBuildsCompacted(
            @Nullable String buildTypeId,
            @Nullable String branchName);

    /**
     * Return queued builds for branch and suite, without relation to its status.
     *
     * @param branchName Branch name.
     * @return list of builds in history, includes all statuses: queued, running, etc
     */
    public List<BuildRefCompacted> getQueuedBuildsCompacted(String branchName);

    /**
     * @param projectId Project id.
     * @return Mutes for associated server and given project pair.
     */
    public Set<MuteInfo> getMutes(String projectId);

    /**
     * Return all builds for branch and suite with finish status.
     *
     * @param buildTypeId Build type identifier.
     * @param branchName Branch name.
     * @param sinceDate Since date.
     * @param untilDate Until date.
     * @return list of builds in history in finish status.
     */
    public List<BuildRefCompacted> getFinishedBuildsCompacted(
        @Nullable String buildTypeId,
        @Nullable String branchName,
        @Nullable Date sinceDate,
        @Nullable Date untilDate);

    /**
     * Trigger build. Enforces TC Bot to load all builds related to this triggered one.
     * @param buildTypeId Build type identifier.
     * @param branchName Branch name.
     * @param cleanRebuild Rebuild all dependencies.
     * @param queueAtTop Put at the top of the build queue.
     * @param buildParms addtitional build parameters, for example Java home or test suite. Use
*      * <code>put("testSuite", "org.apache.ignite.spi.discovery.tcp.ipfinder.elb.TcpDiscoveryElbIpFinderSelfTest");</code>
     * @param actualizeReferences enforce loading of triggered builds from TC now. Otherwise, return these IDS.
     * @param freeTextComments some additional comments to be added to build (chain).
     */
    public T2<Build, Set<Integer>> triggerBuild(String buildTypeId, String branchName, boolean cleanRebuild, boolean queueAtTop,
        Map<String, Object> buildParms, boolean actualizeReferences, @Nullable  String freeTextComments);

    /**
     * Runs synchronization of provided builds.
     * @param collect Collect.
     */
    public void fastBuildsSync(Set<Integer> collect);

    /**
     * @param srvId Server id.
     * @return integer representation of server ID.
     */
    public static int serverIdToInt(@Nullable final String srvId) {
        if (srvId == null)
            return 0;

        return Math.abs(srvId.hashCode());
    }

    /**
     * Check build condition.
     *
     * @param buildId Build id.
     */
    public boolean buildIsValid(int buildId);

    /**
     * Set build condition.
     *
     * @param cond Condition.
     */
    public boolean setBuildCondition(BuildCondition cond);

    /**
     * @param buildId Build id.
     * @return  build start timestamp or null.
     */
    public Long getBuildStartTs(int buildId);

    /**
     * @param id Id.
     */
    public default FatBuildCompacted getFatBuild(int id) {
        return getFatBuild(id, SyncMode.RELOAD_QUEUED);
    }

    /**
     * @param id Id.
     * @param mode Refresh mode.
     */
    public FatBuildCompacted getFatBuild(int id, SyncMode mode);

    public Collection<ChangeCompacted> getAllChanges(int[] changeIds);

    /**
     * Returns IDs of N. most recent builds in build history.
     *
     * @param btId Build type ID.
     * @param branchForTc Branch for tc.
     * @param cnt Count.
     */
    @Nonnull public List<Integer> getLastNBuildsFromHistory(String btId, String branchForTc, int cnt);

    /**
     * Return list of composite suite ids sorted by number of snapshot dependency.
     *
     * @param projectId Project id.
     * @return List of composite buildType ids.
     */
    public List<String> getCompositeBuildTypesIdsSortedByBuildNumberCounter(String projectId);

    /**
     * Return list of compacted references to project suites.
     *
     * @param projectId Project id.
     * @return List of compacted references to buildTypes.
     */
    public List<BuildTypeRefCompacted> getAllBuildTypesCompacted(String projectId);


    /**
     * @param buildTypeId BuildType id.
     * @return Compacted reference to BuildType.
     */
    public BuildTypeRefCompacted getBuildTypeRef(String buildTypeId);

    /**
     * @param buildTypeId BuildType id.
     * @return BuildType compacted.
     */
    public BuildTypeCompacted getBuildType(String buildTypeId);

    @Nullable public ISuiteRunHistory getSuiteRunHist(@Nullable Integer buildTypeId, @Nullable Integer normalizedBaseBranch);

    /**
     * V.3.0 run history implementation based on scan of fat builds.
     *
     * @param testName Test name.
     * @param buildTypeId Suite (Build Type) ID, ID for compactor. Null suite name means suite not found.
     * @param normalizedBaseBranch Branch name. This branch name does not support branches equivalence, only exact query will work.
     */
    @Nullable public IRunHistory getTestRunHist(int testName, @Nullable Integer buildTypeId, @Nullable Integer normalizedBaseBranch);

    public List<String> getAllProjectsIds();

    /**
     * Get list of teamcity agents. Never cached, request goes directly to pure TC.
     *
     * @param connected Connected flag.
     * @param authorized Authorized flag.
     * @return List of teamcity agents.
     */
    public List<Agent> agents(boolean connected, boolean authorized);

    /**
     * @param build Build.
     */
    @Nullable
    public default String getLatestCommitVersion(FatBuildCompacted build) {
        List<RevisionCompacted> revisions = build.revisions();
        if (revisions != null) {
            Optional<String> any = revisions.stream()
                .map(RevisionCompacted::commitFullVersion)
                .filter(s -> !Strings.isNullOrEmpty(s))
                .findAny();

            if (any.isPresent())
                return any.get(); // Not so good for several VCS roots, probably should use collection here and concatenate.
        }

        //fallback version of commit hash extraction
        int changeMax = -1;
        int[] changes = build.changes();
        for (int i = 0; i < changes.length; i++) {
            int change = changes[i];
            if (change > changeMax)
                changeMax = change;
        }

        if (changeMax > 0) {
            final Collection<ChangeCompacted> allChanges = getAllChanges(new int[] {changeMax});
            return allChanges.stream().findAny().map(ChangeCompacted::commitFullVersion).orElse(null);
        }

        return null;
    }

    @Nullable File downloadAndCacheBuildLog(int buildId);

    /**
     * Enforce reloading of recent build references for this server. At least queued/running builds from TC Bot DB
     * should be re-synced.
     */
    public void actualizeRecentBuildRefs();

    public Long getBuildStartTime(int buildId);

    public Integer getBorderForAgeForBuildId(int days);
}
