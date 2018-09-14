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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.analysis.LogCheckResult;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangeRef;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.issues.IssuesUsagesList;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrences;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.util.Base64Util;
import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.db.DbMigrations.TESTS_COUNT_7700;

/**
 * API for calling methods from REST service:
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public interface ITeamcity extends AutoCloseable {

    String DEFAULT = "<default>";
    long DEFAULT_BUILDS_COUNT = 1000;

    CompletableFuture<List<BuildType>> getProjectSuites(String projectId);

    String serverId();

    /**
     * @param projectId suite ID (string without spaces)
     * @param branch
     * @return list of builds in historical order, recent builds coming last
     */
    default List<BuildRef> getFinishedBuilds(String projectId, String branch) {
        return getFinishedBuilds(projectId, branch, null);
    };

    /**
     * @param projectId suite ID (string without spaces)
     * @param branch
     * @param cnt builds count
     * @return list of builds in historical order, recent builds coming last
     */
    List<BuildRef> getFinishedBuilds(String projectId, String branch, Long cnt);

    /**
     * Includes snapshot dependencies failed builds into list
     *
     * @param projectId suite ID (string without spaces)
     * @param branch branch in TC identification
     * @return list of builds in historical order, recent builds coming last
     */
    default List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch){
        return getFinishedBuildsIncludeSnDepFailed(projectId, branch, null);
    };

    /**
     * Includes snapshot dependencies failed builds into list
     *
     * @param projectId suite ID (string without spaces)
     * @param branch branch in TC identification
     * @param cnt builds count
     * @return list of builds in historical order, recent builds coming last
     */
    List<BuildRef> getFinishedBuildsIncludeSnDepFailed(String projectId, String branch, Long cnt);

    /**   */
    CompletableFuture<List<BuildRef>> getRunningBuilds(@Nullable String branch);

    /**   */
    CompletableFuture<List<BuildRef>> getQueuedBuilds(@Nullable String branch);

    default int[] getBuildNumbersFromHistory(String projectId, String branchNameForHist) {
        return getBuildNumbersFromHistory(projectId, branchNameForHist, null);
    }

    default int[] getBuildNumbersFromHistory(String projectId, String branchNameForHist, Long cnt) {
        return getFinishedBuilds(projectId, branchNameForHist, cnt).stream().mapToInt(BuildRef::getId).toArray();
    }

    Build getBuild(String href);

    default Build getBuild(int id) {
        return getBuild(getBuildHrefById(id));
    }

    @NotNull default String getBuildHrefById(int id) {
        return "app/rest/latest/builds/id:" + Integer.toString(id);
    }

    /**
     * @return Normalized Host address, ends with '/'.
     */
    public String host();

    /**
     * @param build
     * @return
     */
    ProblemOccurrences getProblems(Build build);

    TestOccurrences getTests(String href, String normalizedBranch);

    Statistics getBuildStatistics(String href);

    CompletableFuture<TestOccurrenceFull> getTestFull(String href);

    Change getChange(String href);

    ChangesList getChangesList(String href);

    IssuesUsagesList getIssuesUsagesList(String href);

    /**
     * Runs deep collection of all related statistics for particular build
     *
     * @param build build from history with references to tests
     * @return full context
     */
    @Nonnull default MultBuildRunCtx loadTestsAndProblems(@Nonnull Build build) {
        MultBuildRunCtx ctx = new MultBuildRunCtx(build);
        loadTestsAndProblems(build, ctx);
        return ctx;
    }

    default SingleBuildRunCtx loadTestsAndProblems(@Nonnull Build build, @Deprecated MultBuildRunCtx mCtx) {
        SingleBuildRunCtx ctx = new SingleBuildRunCtx(build);
        if (build.problemOccurrences != null) {
            List<ProblemOccurrence> problems = getProblems(build).getProblemsNonNull();

            mCtx.addProblems(problems);
            ctx.setProblems(problems);
        }

        if (build.lastChanges != null) {
            for (ChangeRef next : build.lastChanges.changes) {
                if(!isNullOrEmpty(next.href)) {
                    // just to cache this change
                    getChange(next.href);
                }
            }
        }

        if (build.changesRef != null) {
            ChangesList changeList = getChangesList(build.changesRef.href);
            // System.err.println("changes: " + changeList);
            if (changeList.changes != null) {
                for (ChangeRef next : changeList.changes) {
                    if (!isNullOrEmpty(next.href)) {
                        // just to cache this change
                        ctx.addChange(getChange(next.href));
                    }
                }
            }
        }

        if (build.testOccurrences != null && !build.isComposite()) {
            String normalizedBranch = BuildChainProcessor.normalizeBranch(build);

            List<TestOccurrence> tests
                = getTests(build.testOccurrences.href + TESTS_COUNT_7700, normalizedBranch).getTests();

            ctx.setTests(tests);

            mCtx.addTests(tests);

            for (TestOccurrence next : tests) {
                if (next.href != null && next.isFailedTest()) {
                    CompletableFuture<TestOccurrenceFull> testFullFut = getTestFull(next.href);

                    String testInBuildId = next.getId();

                    mCtx.addTestInBuildToTestFull(testInBuildId, testFullFut);
                }
            }
        }

        if (build.statisticsRef != null)
            mCtx.setStat(getBuildStatistics(build.statisticsRef.href));

        return ctx;
    }

    @Override void close();

    CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut);

    CompletableFuture<File> downloadBuildLogZip(int id);

    /**
     * Returns log analysis. Does not keep not zipped logs on disk
     * @param buildId biuld ID
     * @param ctx build results
     * @return
     */
    CompletableFuture<LogCheckResult> analyzeBuildLog(Integer buildId, SingleBuildRunCtx ctx);

    void setExecutor(ExecutorService pool);

    /**
     * Trigger build.
     *
     * @param id Build identifier.
     * @param name Branch name.
     * @param cleanRebuild Rebuild all dependencies.
     * @param queueAtTop Put at the top of the build queue.
     */
    Build triggerBuild(String id, String name, boolean cleanRebuild, boolean queueAtTop);

    /**
     * @param tok TeamCity authorization token.
     */
    void setAuthToken(String tok);

    /**
     * @return {@code True} if TeamCity authorization token is available.
     */
    boolean isTeamCityTokenAvailable();

    /**
     * @param token GitHub authorization token.
     */
    void setGitToken(String token);

    /**
     * @return {@code True} if GitHub authorization token is available.
     */
    boolean isGitTokenAvailable();

    /**
     * @param tok Jira authorization token.
     */
    void setJiraToken(String tok);

    /**
     * @return {@code True} if JIRA authorization token is available.
     */
    boolean isJiraTokenAvailable();

    /**
     * Send POST request with given body.
     *
     * @param url Url.
     * @param body Request body.
     * @return {@code True} - if GitHub was notified. {@code False} - otherwise.
     */
    boolean notifyGit(String url, String body);

    /**
     * @param branch TeamCity's branch name. Looks like "pull/123/head".
     * @return Pull Request.
     */
    PullRequest getPullRequest(String branch);

    /**
     * @param ticket JIRA ticket full name.
     * @param comment Comment to be placed in the ticket conversation.
     * @return {@code True} if ticket was succesfully commented. Otherwise - {@code false}.
     */
    boolean sendJiraComment(String ticket, String comment);


    default void setAuthData(String user, String password) {
        setAuthToken(
                Base64Util.encodeUtf8String(user + ":" + password));
    }

    /**
     * Get list of teamcity agents.
     *
     * @param connected Connected flag.
     * @param authorized Authorized flag.
     * @return List of teamcity agents.
     */
    List<Agent> agents(boolean connected, boolean authorized);
}
