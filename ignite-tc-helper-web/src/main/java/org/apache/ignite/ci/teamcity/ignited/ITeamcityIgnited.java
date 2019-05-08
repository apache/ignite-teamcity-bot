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

import com.google.common.base.Strings;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.tcbot.conf.ITcServerConfig;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.mute.MuteInfo;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildCondition;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.change.RevisionCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public interface ITeamcityIgnited {
    /**
     * @return Internal server ID as string
     */
    public String serverId();

    /**
     * @return TeamCity configuration.
     */
    public ITcServerConfig config();

    /**
     * @return Normalized Host address, ends with '/'.
     */
    default public String host() {return config().host();}

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
     * @param creds Credentials.
     * @return Mutes for associated server and given project pair.
     */
    public Set<MuteInfo> getMutes(String projectId, ICredentialsProv creds);

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
     *
     * @param buildTypeId Build type identifier.
     * @param branchName Branch name.
     * @param cleanRebuild Rebuild all dependencies.
     * @param queueAtTop Put at the top of the build queue.
     * @param buildParms addtitional build parameters, for example Java home or test suite. Use
     *      * <code>put("testSuite", "org.apache.ignite.spi.discovery.tcp.ipfinder.elb.TcpDiscoveryElbIpFinderSelfTest");</code>
     *      * to specify test suite to run.
     */
    public Build triggerBuild(String buildTypeId, String branchName, boolean cleanRebuild, boolean queueAtTop,
        Map<String, Object> buildParms);

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
    @NotNull public List<Integer> getLastNBuildsFromHistory(String btId, String branchForTc, int cnt);

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

    @Nullable public IRunHistory getTestRunHist(TestInBranch testInBranch);

    @Nullable public IRunHistory getSuiteRunHist(SuiteInBranch branch);

    /**
     * @param suiteBuildTypeId Suite id.
     * @return run statistics of recent runls on all branches.
     */
    @Nullable public IRunStat getSuiteRunStatAllBranches(String suiteBuildTypeId);

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
}
