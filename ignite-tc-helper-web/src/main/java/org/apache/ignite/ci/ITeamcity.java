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

package org.apache.ignite.ci;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
import org.apache.ignite.ci.analysis.LogCheckResult;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.Configurations;
import org.apache.ignite.ci.tcmodel.result.issues.IssuesUsagesList;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.tcmodel.result.tests.TestRef;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.teamcity.pure.ITeamcityConn;
import org.apache.ignite.ci.util.Base64Util;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.jetbrains.annotations.NotNull;

/**
 * API for calling methods from REST service:
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public interface ITeamcity extends ITeamcityConn {

    String DEFAULT = "<default>";

    @Deprecated
    long DEFAULT_BUILDS_COUNT = 1000;

    CompletableFuture<List<BuildType>> getProjectSuites(String projectId);

    /**
     * @param projectId suite ID (string without spaces).
     * @param branch Branch name in TC identification.
     * @param sinceDate Since date.
     * @param untilDate Until date.
     * @param sinceBuildId Some build ID in the past to to use as minimal build to export.
     * @return list of builds in historical order, recent builds coming last.
     */
    @Deprecated
    List<BuildRef> getFinishedBuilds(String projectId, String branch, Date sinceDate, Date untilDate, Integer sinceBuildId);

    /**
     * Includes snapshot dependencies failed builds into list.
     *
     * @param projectId suite ID (string without spaces).
     * @param branch branch in TC identification.
     * @return list of builds in historical order, recent builds coming last.
     */
    @Deprecated
    default List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch){
        return getFinishedBuildsIncludeSnDepFailed(projectId, branch, null);
    };

    /**
     * Includes 'snapshot dependencies failed' builds into list.
     * loads build history with following parameter: defaultFilter:false,state:finished
     *
     * @param projectId suite ID (string without spaces).
     * @param branch branch in TC identification.
     * @param sinceBuildId limit builds export with some build number, not operational for Persistent connection.
     * @return list of builds in historical order, recent builds coming last.
     */
    @Deprecated
    List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch, Integer sinceBuildId);

    /**   */
    @Deprecated
    CompletableFuture<List<BuildRef>> getQueuedBuilds(@Nullable String branch);

    /**
     * @param projectId Suite ID (string without spaces).
     * @param branchNameForHist Branch in TC identification.
     * @param sinceDate Since date.
     * @param untilDate Until date.
     * @return List of build numbers in historical order in date interval, recent builds coming last.
     */
    @Deprecated
    default int[] getBuildNumbersFromHistory(String projectId, String branchNameForHist, Date sinceDate, Date untilDate) {
        return getFinishedBuilds(projectId, branchNameForHist, sinceDate, untilDate, null).stream().mapToInt(BuildRef::getId).toArray();
    }

    @Deprecated
    Build getBuild(String href);

    default Build getBuild(int buildId) {
        return getBuild(getBuildHrefById(buildId));
    }

    @NotNull default String getBuildHrefById(int id) {
        return buildHref(id);
    }

    @NotNull static String buildHref(int id) {
        return "app/rest/latest/builds/id:" + Integer.toString(id);
    }

    @Deprecated
    ProblemOccurrences getProblems(BuildRef build);

    @Deprecated
    public TestOccurrences getTests(String fullUrl);

    @Deprecated
    TestOccurrences getFailedTests(String href, int count, String normalizedBranch);

    @Deprecated
    CompletableFuture<TestOccurrenceFull> getTestFull(String href);

    @Deprecated
    Change getChange(String href);

    @Deprecated
    ChangesList getChangesList(String href);

    @Deprecated
    CompletableFuture<TestRef> getTestRef(FullQueryParams key);

    Configurations getConfigurations(FullQueryParams key);

    /**
     * List of build's related issues.
     *
     * @param href IssuesUsagesList href.
     */
    IssuesUsagesList getIssuesUsagesList(String href);

    CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut);

    CompletableFuture<File> downloadBuildLogZip(int id);

    /**
     * Returns log analysis. Does not keep not zipped logs on disk.
     * @param buildId Build ID.
     * @param ctx Build results.
     * @return
     */
    CompletableFuture<LogCheckResult> analyzeBuildLog(Integer buildId, SingleBuildRunCtx ctx);

    void setExecutor(ExecutorService pool);

    Executor getExecutor();


    /**
     * @param tok TeamCity authorization token.
     */
    void setAuthToken(String tok);

    /**
     * @return {@code True} if TeamCity authorization token is available.
     */
    boolean isTeamCityTokenAvailable();


    /**
     * @param tok Jira authorization token.
     */
    void setJiraToken(String tok);

    /**
     * @return {@code True} if JIRA authorization token is available.
     */
    boolean isJiraTokenAvailable();

    /**
     * @param ticket JIRA ticket full name. E.g 'IGNITE-5555'.
     * @param comment Comment to be placed in the ticket conversation.
     * @return {@code True} if ticket was succesfully commented. Otherwise - {@code false}.
     *
     * @throws IOException If failed to comment JIRA ticket.
     * @throws IllegalStateException If can't find URL to the JIRA.
     */
    public String sendJiraComment(String ticket, String comment) throws IOException;

    /**
     * @param url URL for JIRA integration.
     */
    void setJiraApiUrl(String url);

    /**
     * @return URL for JIRA integration.
     */
    String getJiraApiUrl();

    default void setAuthData(String user, String pwd) {
        setAuthToken(
                Base64Util.encodeUtf8String(user + ":" + pwd));
    }

    /**
     * Get list of teamcity agents.
     *
     * @param connected Connected flag.
     * @param authorized Authorized flag.
     * @return List of teamcity agents.
     */
    List<Agent> agents(boolean connected, boolean authorized);

    void init(String serverId);

    User getUserByUsername(String username);

}
