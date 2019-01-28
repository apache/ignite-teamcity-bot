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

package org.apache.ignite.ci.teamcity.pure;

import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.Project;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildTypeFull;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.mute.MuteInfo;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;

/**
 * Pure Teamcity Connection API for calling methods from REST service: <br>
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public interface ITeamcityConn {
    /**
     * @return Internal server ID as string
     */
    @Nullable
    public String serverId();

    /**
     * @return Normalized Host address, ends with '/'.
     */
    public String host();

    /**
     * @param buildId Build id.
     */
    public Build getBuild(int buildId);

    /**
     * @param fullUrl Full url.
     * @param nextPage Next page.
     */
    public List<BuildRef> getBuildRefsPage(String fullUrl, AtomicReference<String> nextPage);

    /**
     * @param buildTypeId Build type id.
     * @param fullUrl Full url.
     * @param nextPage Next page.
     * @return Set of mutes from given page or default page.
     */
    public SortedSet<MuteInfo> getMutesPage(String buildTypeId, @Nullable String fullUrl,
        AtomicReference<String> nextPage);

    /**
     * @param buildId Build id.
     * @param href Href. Null activates first page loaded.
     * @param testDtls Query test details.
     */
    public TestOccurrencesFull getTestsPage(int buildId, @Nullable String href, boolean testDtls);

    /**
     * Trigger build.
     *
     * @param buildTypeId Build identifier.
     * @param branchName Branch name.
     * @param cleanRebuild Rebuild all dependencies.
     * @param queueAtTop Put at the top of the build queue.
     */
    public Build triggerBuild(String buildTypeId, @Nonnull String branchName, boolean cleanRebuild, boolean queueAtTop);

    /**
     * @param buildId Build id.
     */
    public ProblemOccurrences getProblems(int buildId);

    /**
     * @param buildId Build id.
     */
    public Statistics getStatistics(int buildId);

    /**
     * @param buildId Build id.
     */
    public ChangesList getChangesList(int buildId);

    /**
     * @param changeId Change id.
     */
    public Change getChange(int changeId);

    /**
     * List of project suites.
     *
     * @param projectId Project id.
     * @return List of buildType's references.
     */
    public List<BuildType> getBuildTypes(String projectId);

    /**
     * @param buildTypeId BuildType id.
     * @return BuildType.
     */
    public BuildTypeFull getBuildType(String buildTypeId);

    /**
     * @return List of all project available at Teamcity server.
     */
    public List<Project> getProjects();

    /**
     * Get list of teamcity agents.
     *
     * @param connected Connected flag.
     * @param authorized Authorized flag.
     * @return List of teamcity agents.
     */
    public List<Agent> agents(boolean connected, boolean authorized);
}
