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
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.ws.rs.QueryParam;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.pure.ITcServerProvider;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.github.GitHubUser;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnited;
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
     * @return JIRA ticket number.
     */
    @NotNull public static String getTicketId(PullRequest pr) {
        String ticketId = "";

        if (pr.getTitle().startsWith("IGNITE-")) {
            int beginIdx = 7;
            int endIdx = 7;

            while (endIdx < pr.getTitle().length() && Character.isDigit(pr.getTitle().charAt(endIdx)))
                endIdx++;

            ticketId = pr.getTitle().substring(beginIdx, endIdx);
        }

        return ticketId;
    }

    @NotNull public String triggerBuildAndObserve(
        @Nullable String srvId,
        @Nullable String branchForTc,
        @Nullable String suiteId,
        @Nullable Boolean top,
        @Nullable Boolean observe,
        @Nullable String ticketId,
        ICredentialsProv prov) {
        String jiraRes = "";

        final ITeamcity teamcity = tcServerProvider.server(srvId, prov);

        Build build = teamcity.triggerBuild(suiteId, branchForTc, false, top != null && top);

        if (observe != null && observe)
            jiraRes = observeJira(srvId, branchForTc, ticketId, teamcity, build, prov);

        return jiraRes;
    }

    /**
     * @param srvId Server id.
     * @param branchForTc Branch for TeamCity.
     * @param ticketId JIRA ticket number.
     * @param teamcity TeamCity.
     * @param build Build.
     * @param prov Credentials.
     * @return Message with result.
     */
    private String observeJira(
        String srvId,
        String branchForTc,
        @Nullable String ticketId,
        ITeamcity teamcity,
        Build build,
        ICredentialsProv prov
    ) {
        if (F.isEmpty(ticketId)) {
            try {
                IGitHubConnection gitHubConnection = gitHubConnectionProvider.server(srvId);

                PullRequest pr = gitHubConnection.getPullRequest(branchForTc);

                ticketId = getTicketId(pr);

                if (ticketId.isEmpty()) {
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
        }

        buildObserverProvider.get().observe(build, srvId, prov, "ignite-" + ticketId);

        return "JIRA ticket IGNITE-" + ticketId + " will be notified after the tests are completed.";
    }

    @NotNull
    public SimpleResult commentJiraEx(
        @QueryParam("serverId") @Nullable String srvId,
        @QueryParam("branchName") @Nullable String branchForTc,
        @QueryParam("suiteId") @Nullable String suiteId,
        @QueryParam("ticketId") @Nullable String ticketId,
        ICredentialsProv prov) {
        String jiraRes = "";

        if (Strings.isNullOrEmpty(ticketId)) {
            try {
                IGitHubConnection gitHubConn = gitHubConnectionProvider.server(srvId);
                PullRequest pr = gitHubConn.getPullRequest(branchForTc);

                ticketId = getTicketId(pr);

                if (ticketId.isEmpty()) {
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
        }

        if (!Strings.isNullOrEmpty(ticketId)) {
            jiraRes = jiraIntegration.notifyJira(srvId, prov, suiteId, branchForTc, "ignite-" + ticketId);

            return new SimpleResult(jiraRes);
        }
        else
            return new SimpleResult("JIRA wasn't commented." + (!jiraRes.isEmpty() ? "<br>" + jiraRes : ""));
    }

    public List<ContributionToCheck> getContributionsToCheck(String srvId) {
        IGitHubConnIgnited gitHubConn = gitHubConnIgnitedProvider.server(srvId);
        List<PullRequest> requests = gitHubConn.getPullRequests();
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

            return check;
        }).collect(Collectors.toList());
    }

    public List<BuildRef> buildsForContribution(String srvId, ICredentialsProv prov,
        String suiteId, String prId) {

        ITeamcityIgnited srv = teamcityIgnitedProvider.server(srvId, prov);

        List<BuildRef> buildHist = srv.getBuildHistory(suiteId, branchForTcA(prId));

        if (!buildHist.isEmpty())
            return buildHist;

        buildHist = srv.getBuildHistory(suiteId, branchForTcB(prId));

        return buildHist;
    }

    String branchForTcA(String prId) {
        return "pull/" + prId + "/head";
    }

    String branchForTcB(String prId) {
        return "pull/" + prId + "/merge";
    }
}
