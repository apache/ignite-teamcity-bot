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

package org.apache.ignite.ci.tcbot.visa;

import com.google.common.base.Strings;
import com.google.inject.Provider;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.ws.rs.QueryParam;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.pure.ITcServerProvider;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.github.GitHubUser;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.github.pure.IGitHubConnectionProvider;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.internal.util.typedef.F;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides method for TC Bot Visa obtaining
 */
public class TcBotTriggerAndSignOffService {

    @Inject Provider<BuildObserver> buildObserverProvider;

    @Inject IGitHubConnectionProvider gitHubConnectionProvider;
    @Inject IGitHubConnIgnitedProvider gitHubConnIgnitedProvider;

    @Inject ITcServerProvider tcServerProvider;

    @Inject IJiraIntegration jiraIntegration;

    @Inject ITeamcityIgnitedProvider teamcityIgnitedProvider;

    /**
     * @param pr Pull Request.
     * @return JIRA ticket full name or empty string.
     */
    @NotNull public static String getTicketFullName(PullRequest pr) {
        String ticketId = "";

        if (pr.getTitle().startsWith("IGNITE-")) {
            int beginIdx = 7;
            int endIdx = 7;

            while (endIdx < pr.getTitle().length() && Character.isDigit(pr.getTitle().charAt(endIdx)))
                endIdx++;

            ticketId = "IGNITE-" + pr.getTitle().substring(beginIdx, endIdx);
        }

        return ticketId;
    }


    @NotNull public String triggerBuildsAndObserve(
        @Nullable String srvId,
        @Nullable String branchForTc,
        @Nullable String suiteIdList,
        @Nullable Boolean top,
        @Nullable Boolean observe,
        @Nullable String ticketId,
        ICredentialsProv prov) {
        String jiraRes = "";

        final ITeamcity teamcity = tcServerProvider.server(srvId, prov);

        String[] suiteIds = Objects.requireNonNull(suiteIdList).split(",");

        Build[] builds = new Build[suiteIds.length];

        for (int i = 0; i < suiteIds.length; i++)
            builds[i] = teamcity.triggerBuild(suiteIds[i], branchForTc, false, top != null && top);

        if (observe != null && observe)
            jiraRes = observeJira(srvId, branchForTc, ticketId, prov, builds);

        return jiraRes;
    }

    /**
     * @param srvId Server id.
     * @param branchForTc Branch for TeamCity.
     * @param ticketFullName JIRA ticket number.
     * @param prov Credentials.
     * @param builds Builds.
     * @return Message with result.
     */
    private String observeJira(
        String srvId,
        String branchForTc,
        @Nullable String ticketFullName,
        ICredentialsProv prov,
        Build... builds
    ) {
        if (F.isEmpty(ticketFullName)) {
            try {
                IGitHubConnection gitHubConnection = gitHubConnectionProvider.server(srvId);

                PullRequest pr = gitHubConnection.getPullRequest(branchForTc);

                ticketFullName = getTicketFullName(pr);

                if (ticketFullName.isEmpty()) {
                    return "JIRA ticket will not be notified after the tests are completed - " +
                        "PR title \"" + pr.getTitle() + "\" should starts with \"IGNITE-XXXX\"." +
                        " Please, rename PR according to the" +
                        " <a href='https://cwiki.apache.org/confluence/display/IGNITE/How+to+Contribute" +
                        "#HowtoContribute-1.CreateGitHubpull-request'>contributing guide</a>.";
                }
            }
            catch (Exception e) {
                return "JIRA ticket will not be notified after the tests are completed - " +
                    "exception happened when server tried to get ticket ID from Pull Request [errMsg=" +
                    e.getMessage() + ']';
            }
        } else {
            //todo remove once every ticket is with IGnite prefix
            ticketFullName = ticketFullName.toUpperCase().startsWith("IGNITE-") ? ticketFullName : "IGNITE-" + ticketFullName;
        }

        buildObserverProvider.get().observe(srvId, prov, "ignite-" + ticketFullName, builds);

        return "JIRA ticket IGNITE-" + ticketFullName + " will be notified after the tests are completed.";
    }

    /**
     * @param srvId Server id.
     * @param branchForTc Branch for tc.
     * @param suiteId Suite id.
     * @param ticketFullName Ticket full name with IGNITE- prefix.
     * @param prov Prov.
     */
    @NotNull
    public SimpleResult commentJiraEx(
        @QueryParam("serverId") @Nullable String srvId,
        @QueryParam("branchName") @Nullable String branchForTc,
        @QueryParam("suiteId") @Nullable String suiteId,
        @QueryParam("ticketId") @Nullable String ticketFullName,
        ICredentialsProv prov) {
        String jiraRes = "";

        if (Strings.isNullOrEmpty(ticketFullName)) {
            try {
                IGitHubConnection gitHubConn = gitHubConnectionProvider.server(srvId);
                PullRequest pr = gitHubConn.getPullRequest(branchForTc);

                ticketFullName = getTicketFullName(pr);

                if (ticketFullName.isEmpty()) {
                    jiraRes = "JIRA ticket can't be commented - " +
                        "PR title \"" + pr.getTitle() + "\" should starts with \"IGNITE-XXXX\"." +
                        " Please, rename PR according to the" +
                        " <a href='https://cwiki.apache.org/confluence/display/IGNITE/How+to+Contribute" +
                        "#HowtoContribute-1.CreateGitHubpull-request'>contributing guide</a>" +
                        " or enter ticket id in the form.";
                }
            }
            catch (RuntimeException e) {
                jiraRes = "Exception happened when server tried to get ticket ID from Pull Request - " + e.getMessage();
            }
        } else {
            //todo remove once every ticket is with IGnite prefix
            ticketFullName = ticketFullName.toUpperCase().startsWith("IGNITE-") ? ticketFullName : "IGNITE-" + ticketFullName;
        }

        if (!Strings.isNullOrEmpty(ticketFullName)) {
            jiraRes = jiraIntegration.notifyJira(srvId, prov, suiteId, branchForTc, ticketFullName);

            return new SimpleResult(jiraRes);
        }
        else
            return new SimpleResult("JIRA wasn't commented." + (!jiraRes.isEmpty() ? "<br>" + jiraRes : ""));
    }

    public List<ContributionToCheck> getContributionsToCheck(String srvId) {
        List<PullRequest> requests = gitHubConnIgnitedProvider.server(srvId).getPullRequests();
        if (requests == null)
            return null;

        return requests.stream().map(pr -> {
            ContributionToCheck check = new ContributionToCheck();
            check.prNumber = pr.getNumber();
            check.prTitle = pr.getTitle();
            check.prHtmlUrl = pr.htmlUrl();

            GitHubUser user = pr.gitHubUser();
            if (user != null) {
                check.prAuthor = user.login();
                check.prAuthorAvatarUrl = user.avatarUrl();
            }

            check.jiraIssueId = Strings.emptyToNull(getTicketFullName(pr));

            return check;
        }).collect(Collectors.toList());
    }

    @Nonnull private List<BuildRef> findRunAllsForPr(String srvId, ICredentialsProv prov, String suiteId, String prId) {
        ITeamcityIgnited srv = teamcityIgnitedProvider.server(srvId, prov);

        String branchName = branchForTcA(prId);
        List<BuildRef> buildHist = srv.getBuildHistory(suiteId, branchName);

        if (!buildHist.isEmpty())
            return buildHist;


        //todo multibranch requestst
        buildHist = srv.getBuildHistory(suiteId, branchForTcB(prId));

        if (!buildHist.isEmpty())
            return buildHist;

        return Collections.emptyList();
    }

    String branchForTcA(String prId) {
        return "pull/" + prId + "/head";
    }

    String branchForTcB(String prId) {
        return "pull/" + prId + "/merge";
    }

    /**
     * @param srvId Server id.
     * @param prov Prov.
     * @param suiteId Suite id.
     * @param prId Pr id.
     */
    public ContributionCheckStatus contributionStatus(String srvId, ICredentialsProv prov, String suiteId,
        String prId) {
        ContributionCheckStatus status = new ContributionCheckStatus();

        List<BuildRef> allRunAlls = findRunAllsForPr(srvId, prov, suiteId, prId);

        boolean finishedRunAllPresent = allRunAlls.stream().filter(BuildRef::isNotCancelled).anyMatch(BuildRef::isFinished);

        status.branchWithFinishedRunAll = finishedRunAllPresent ? allRunAlls.get(0).branchName : null;

        if (status.branchWithFinishedRunAll == null) {
            if (!allRunAlls.isEmpty())
                status.resolvedBranch = allRunAlls.get(0).branchName;
            else
                status.resolvedBranch = branchForTcA(prId);
        }
        else
            //todo take into account running/queued
            status.resolvedBranch = status.branchWithFinishedRunAll;


        //todo take into accounts not only run alls:
        status.queuedBuilds = (int)allRunAlls.stream().filter(BuildRef::isNotCancelled).filter(BuildRef::isQueued).count();
        status.runningBuilds = (int)allRunAlls.stream().filter(BuildRef::isNotCancelled).filter(BuildRef::isRunning).count();

        return status;
    }
}
