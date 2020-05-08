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

package org.apache.ignite.tcservice;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.ignite.tcservice.model.agent.Agent;
import org.apache.ignite.tcservice.model.changes.Change;
import org.apache.ignite.tcservice.model.changes.ChangesList;
import org.apache.ignite.tcservice.model.conf.BuildType;
import org.apache.ignite.tcservice.model.conf.Project;
import org.apache.ignite.tcservice.model.conf.bt.BuildTypeFull;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrences;
import org.apache.ignite.tcservice.model.result.stat.Statistics;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrencesFull;
import org.apache.ignite.tcbot.common.exeption.ServiceConflictException;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;

/**
 * Pure Teamcity Connection API for calling methods from REST service: <br>
 * https://confluence.jetbrains.com/display/TCD10/REST+API.<br><br>
 *
 *
 * HTTP methods return following errors in case HTTP communication failures:
 * <ul><li>{@link UncheckedIOException} caused by {@link FileNotFoundException} - If not found (404) was returned from service.</li>
 * <li>{@link ServiceConflictException} If conflict (409) was returned from service.</li>
 * <li>{@link IllegalStateException} if some unexpected HTTP error returned.</li>
 * <li>{@link UncheckedIOException} in case communication failed.</ul>
 */
public interface ITeamcityConn {
    /**
     * @return Internal server ID as string
     */
    @Nullable
    public String serverCode();

    /**
     * @return TeamCity configuration.
     */
    public ITcServerConfig config();

    /**
     * @return Normalized Host address, ends with '/'.
     */
    default String host() {
        return config().host();
    }

    /**
     * @param buildId Build id.
     *
     * @throws RuntimeException in case loading failed, see details in {@link ITeamcityConn}.
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
     * @param buildTypeId Build type (suite) identifier.
     * @param branchName Branch name.
     * @param cleanRebuild Rebuild all dependencies.
     * @param queueAtTop Put at the top of the build queue.
     * @param buildParms addtitional build parameters, for example Java home or test suite. Use
* <code>put("testSuite", "org.apache.ignite.spi.discovery.tcp.ipfinder.elb.TcpDiscoveryElbIpFinderSelfTest");</code>
     * @param freeTextComments
     */
    public Build triggerBuild(String buildTypeId, @Nonnull String branchName, boolean cleanRebuild, boolean queueAtTop,
        @Nullable Map<String, Object> buildParms, @Nullable String freeTextComments);

    /**
     * @param buildId Build id.
     *
     * @throws RuntimeException in case loading failed, see details in {@link ITeamcityConn}.
     */
    public ProblemOccurrences getProblems(int buildId);

    /**
     * @param buildId Build id.
     *
     * @throws RuntimeException in case loading failed, see details in {@link ITeamcityConn}.
     */
    public Statistics getStatistics(int buildId);

    /**
     * @param buildId Build id.
     *
     * @throws RuntimeException in case loading failed, see details in {@link ITeamcityConn}.
     */
    public ChangesList getChangesList(int buildId);

    /**
     * @param changeId Change id.
     *
     * @throws RuntimeException in case loading failed, see details in {@link ITeamcityConn}.
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

    /**
     * @param buildId
     * @return
     *
     * @throws UncheckedIOException caused by IOException If communication failed.
     * @throws UncheckedIOException caused by FileNotFoundException If not found (404) was returned from service.
     * @throws ServiceConflictException If conflict (409) was returned from service.
     * @throws IllegalStateException if some unexpected HTTP error returned.
     */
    @Nullable public File downloadAndCacheBuildLog(int buildId);
}
