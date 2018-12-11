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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.ws.rs.QueryParam;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.github.GitHubUser;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.github.pure.IGitHubConnectionProvider;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.jira.IJiraIntegrationProvider;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.observer.BuildsInfo;
import org.apache.ignite.ci.tcbot.chain.PrChainsProcessor;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.mute.MuteInfo;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.SyncMode;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.apache.ignite.ci.web.model.VisaRequest;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.hist.VisasHistoryStorage;
import org.apache.ignite.internal.util.typedef.F;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.ci.observer.BuildsInfo.CANCELLED_STATUS;
import static org.apache.ignite.ci.observer.BuildsInfo.FINISHED_STATUS;
import static org.apache.ignite.ci.observer.BuildsInfo.RUNNING_STATUS;
import static org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedImpl.DEFAULT_PROJECT_ID;

/**
 * Provides method for TC Bot Visa obtaining
 */
public class TcBotTriggerAndSignOffService {
    /** */
    private static final ThreadLocal<DateFormat> THREAD_FORMATTER = new ThreadLocal<DateFormat>() {
        @Override protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
    };

    @Inject Provider<BuildObserver> buildObserverProvider;

    /** Git hub pure http connection provider. */
    @Inject IGitHubConnectionProvider gitHubConnectionProvider;

    /** Git hub connection ignited provider. */
    @Inject IGitHubConnIgnitedProvider gitHubConnIgnitedProvider;

    @Inject ITeamcityIgnitedProvider tcIgnitedProv;

    /** */
    @Inject IJiraIntegrationProvider jiraIntegrationProvider;

    @Inject ITeamcityIgnitedProvider teamcityIgnitedProvider;

    @Inject Provider<BuildObserver> observer;

    /** */
    @Inject private VisasHistoryStorage visasHistoryStorage;

    /** */
    @Inject IStringCompactor compactor;

    /** Helper. */
    @Inject ITcHelper tcHelper;


    @Inject PrChainsProcessor prChainsProcessor;

    /** */
    public void startObserver() {
        buildObserverProvider.get();
    }

    /** */
    public List<VisaStatus> getVisasStatus(String srvId, ICredentialsProv prov) {
        List<VisaStatus> visaStatuses = new ArrayList<>();

        IAnalyticsEnabledTeamcity teamcity = tcHelper.server(srvId, prov);
        ITeamcityIgnited ignited = tcIgnitedProv.server(srvId, prov);

        IJiraIntegration jiraIntegration = jiraIntegrationProvider.server(srvId);

        for (VisaRequest visaRequest : visasHistoryStorage.getVisas()) {
            VisaStatus visaStatus = new VisaStatus();

            BuildsInfo info = visaRequest.getInfo();

            Visa visa = visaRequest.getResult();

            boolean isObserving = visaRequest.isObserving();

            visaStatus.date = THREAD_FORMATTER.get().format(info.date);
            visaStatus.branchName = info.branchForTc;
            visaStatus.userName = info.userName;
            visaStatus.ticket = info.ticket;
            visaStatus.buildTypeId = info.buildTypeId;

            BuildTypeRefCompacted bt = ignited.getBuildTypeRef(info.buildTypeId);
            visaStatus.buildTypeName = (bt != null ? bt.name(compactor) : visaStatus.buildTypeId);

            String buildsStatus = visaStatus.status = info.getStatus(teamcity);

            if (FINISHED_STATUS.equals(buildsStatus)) {
                if (visa.isSuccess()) {
                    visaStatus.commentUrl = jiraIntegration.generateCommentUrl(
                        visaStatus.ticket, visa.getJiraCommentResponse().getId());

                    visaStatus.blockers = visa.getBlockers();

                    visaStatus.status = FINISHED_STATUS;
                }
                else
                    visaStatus.status = isObserving ? "waiting results" : CANCELLED_STATUS;
            }
            else if (RUNNING_STATUS.equals(buildsStatus))
                visaStatus.status = isObserving ? RUNNING_STATUS : CANCELLED_STATUS;
            else
                visaStatus.status = buildsStatus;

            if (isObserving)
                visaStatus.cancelUrl = "/rest/visa/cancel?server=" + srvId + "&branch=" + info.branchForTc;

            visaStatuses.add(visaStatus);
        }

        return visaStatuses;
    }

    /**
     * @param srvId Server id.
     * @param creds Credentials.
     * @return Mutes for given server-project pair.
     */
    public Set<MuteInfo> getMutes(String srvId, String projectId, ICredentialsProv creds) {
        ITeamcityIgnited ignited = teamcityIgnitedProvider.server(srvId, creds);

        Set<MuteInfo> infos = ignited.getMutes(projectId);

        for (MuteInfo info : infos)
            info.assignment.muteDate = THREAD_FORMATTER.get().format(new Date(info.assignment.timestamp()));

        return infos;
    }

    /**
     * @param pr Pull Request.
     * @return JIRA ticket full name or empty string.
     */
    @NotNull public static String getTicketFullName(PullRequest pr) {
        String ticketId = "";

        if (pr.getTitle().toUpperCase().startsWith("IGNITE-")) {
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
        @Nonnull String suiteIdList,
        @Nullable Boolean top,
        @Nullable Boolean observe,
        @Nullable String ticketId,
        ICredentialsProv prov) {
        String jiraRes = "";

        final ITeamcityIgnited teamcity = tcIgnitedProv.server(srvId, prov);

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
                IGitHubConnection gitHubConn = gitHubConnectionProvider.server(srvId);

                PullRequest pr = gitHubConn.getPullRequest(branchForTc);

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

        buildObserverProvider.get().observe(srvId, prov, ticketFullName, branchForTc, builds);

        if (!tcHelper.isServerAuthorized())
            return "Ask server administrator to authorize the Bot to enable JIRA notifications.";

        return "JIRA ticket IGNITE-" + ticketFullName +
            " will be notified after the tests are completed.";
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
            BuildsInfo buildsInfo = new BuildsInfo(srvId, prov, ticketFullName, branchForTc);

            buildsInfo.buildTypeId = suiteId;

            VisaRequest lastVisaReq = visasHistoryStorage.getLastVisaRequest(buildsInfo.getContributionKey());

            if (Objects.nonNull(lastVisaReq) && lastVisaReq.isObserving())
                return new SimpleResult("Jira wasn't commented." +
                    " \"Re-run possible blockers & Comment JIRA\" was triggered for current branch." +
                    " Wait for the end or cancel exsiting observing.");

            IJiraIntegration jiraIntegration = jiraIntegrationProvider.server(srvId);

            Visa visa = jiraIntegration.notifyJira(srvId, prov, suiteId, branchForTc, ticketFullName);

            visasHistoryStorage.put(new VisaRequest(buildsInfo)
                .setResult(visa));

            return new SimpleResult(visa.status);
        }
        else
            return new SimpleResult("JIRA wasn't commented." + (!jiraRes.isEmpty() ? "<br>" + jiraRes : ""));
    }

    /**
     * @param srvId Server id.
     */
    public List<ContributionToCheck> getContributionsToCheck(String srvId) {
        IJiraIntegration jiraIntegration = jiraIntegrationProvider.server(srvId);

        List<PullRequest> requests = gitHubConnIgnitedProvider.server(srvId).getPullRequests();
        if (requests == null)
            return null;

        return requests.stream().map(pr -> {
            ContributionToCheck check = new ContributionToCheck();
            check.prNumber = pr.getNumber();
            check.prTitle = pr.getTitle();
            check.prHtmlUrl = pr.htmlUrl();
            check.prTimeUpdate = pr.getTimeUpdate();

            GitHubUser user = pr.gitHubUser();
            if (user != null) {
                check.prAuthor = user.login();
                check.prAuthorAvatarUrl = user.avatarUrl();
            }

            check.jiraIssueId = Strings.emptyToNull(getTicketFullName(pr));

            if (!Strings.isNullOrEmpty(check.jiraIssueId))
                check.jiraIssueUrl = jiraIntegration.generateTicketUrl(check.jiraIssueId);

            return check;
        }).collect(Collectors.toList());
    }

    @Nonnull private List<BuildRefCompacted> findBuildsForPr(String suiteId, String prId, ITeamcityIgnited srv) {

        String branchName = branchForTcA(prId);
        List<BuildRefCompacted> buildHist = srv.getAllBuildsCompacted(suiteId, branchName);

        if (!buildHist.isEmpty())
            return buildHist;

        buildHist = srv.getAllBuildsCompacted(suiteId, branchForTcB(prId));

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
     * @param prId Pr id.
     */
    public Set<ContributionCheckStatus> contributionStatuses(String srvId, ICredentialsProv prov,
        String prId) {
        Set<ContributionCheckStatus> statuses = new LinkedHashSet<>();

        ITeamcityIgnited teamcity = teamcityIgnitedProvider.server(srvId, prov);

        List<String> compositeBuildTypes = teamcity
            .getCompositeBuildTypesIdsSortedByBuildNumberCounter(DEFAULT_PROJECT_ID);

        for (String buildType : compositeBuildTypes) {
            List<BuildRefCompacted> forTests = findBuildsForPr(buildType, prId, teamcity);

            statuses.add(forTests.isEmpty() ? new ContributionCheckStatus(buildType, branchForTcA(prId)) :
                contributionStatus(srvId, buildType, forTests, teamcity, prId));
        }

        return statuses;
    }

    /**
     * @param srvId Server id.
     * @param suiteId Suite id.
     * @param builds Build references.
     */
    public ContributionCheckStatus contributionStatus(String srvId, String suiteId, List<BuildRefCompacted> builds,
        ITeamcityIgnited teamcity, String prId) {
        ContributionCheckStatus status = new ContributionCheckStatus();

        status.suiteId = suiteId;

        List<BuildRefCompacted> finishedOrCancelled = builds.stream()
            .filter(t -> t.isFinished(compactor)).collect(Collectors.toList());

        if (!finishedOrCancelled.isEmpty()) {
            BuildRefCompacted buildRefCompacted = finishedOrCancelled.get(0);

            status.suiteIsFinished = !buildRefCompacted.isCancelled(compactor);
            status.branchWithFinishedSuite = buildRefCompacted.branchName(compactor);
        }
        else {
            status.branchWithFinishedSuite = null;
            status.suiteIsFinished = false;
        }

        if (status.branchWithFinishedSuite != null)
            status.resolvedBranch = status.branchWithFinishedSuite;
            //todo take into account running/queued
        else
            status.resolvedBranch = !builds.isEmpty() ? builds.get(0).branchName(compactor) : branchForTcA(prId);

        String observationsStatus = observer.get().getObservationStatus(new ContributionKey(srvId, status.resolvedBranch));

        status.observationsStatus  = Strings.emptyToNull(observationsStatus);

        List<BuildRefCompacted> queuedSuites = builds.stream()
            .filter(t -> t.isNotCancelled(compactor))
            .filter(t -> t.isQueued(compactor))
            .collect(Collectors.toList());

        List<BuildRefCompacted> runningSuites = builds.stream()
            .filter(t -> t.isNotCancelled(compactor))
            .filter(t -> t.isRunning(compactor))
            .collect(Collectors.toList());

        status.queuedBuilds = queuedSuites.size();
        status.runningBuilds = runningSuites.size();

        status.webLinksQueuedSuites = Stream.concat(queuedSuites.stream(), runningSuites.stream())
            .map(ref -> getWebLinkToQueued(teamcity, ref)).collect(Collectors.toList());

        return status;
    }

    //later may move it to BuildRef webUrl
    /**
     * @param teamcity Teamcity.
     * @param ref Reference.
     */
    @NotNull public String getWebLinkToQueued(ITeamcityIgnited teamcity, BuildRefCompacted ref) {
        return teamcity.host() + "viewQueued.html?itemId=" + ref.id();
    }

    public CurrentVisaStatus currentVisaStatus(String srvId, ICredentialsProv prov, String buildTypeId, String tcBranch) {
        CurrentVisaStatus status = new CurrentVisaStatus();

        List<SuiteCurrentStatus> suitesStatuses
            = prChainsProcessor.getBlockersSuitesStatuses(buildTypeId, tcBranch, srvId, prov, SyncMode.NONE);

        if (suitesStatuses == null)
            return status;

        status.blockers = suitesStatuses.stream()
            .mapToInt(suite -> {
                if (suite.testFailures.isEmpty())
                    return 1;

                return suite.testFailures.size();
            })
            .sum();

        return status;
    }
}
