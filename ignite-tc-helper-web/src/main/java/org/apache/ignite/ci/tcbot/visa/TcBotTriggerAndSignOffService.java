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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Provider;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import org.apache.ignite.ci.github.GitHubBranch;
import org.apache.ignite.ci.github.GitHubUser;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.observer.BuildsInfo;
import org.apache.ignite.ci.tcbot.ITcBotBgAuth;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.apache.ignite.ci.web.model.JiraCommentResponse;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.web.model.VisaRequest;
import org.apache.ignite.ci.web.model.hist.VisasHistoryStorage;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.githubignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.T2;
import org.apache.ignite.jiraignited.IJiraIgnited;
import org.apache.ignite.jiraignited.IJiraIgnitedProvider;
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.pr.BranchTicketMatcher;
import org.apache.ignite.tcbot.engine.pr.PrChainsProcessor;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteUi;
import org.apache.ignite.tcbot.engine.ui.ShortTestFailureUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcservice.ITeamcity;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.apache.ignite.tcservice.model.result.Build;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.ci.observer.BuildsInfo.CANCELLED_STATUS;
import static org.apache.ignite.ci.observer.BuildsInfo.FINISHED_STATUS;
import static org.apache.ignite.ci.observer.BuildsInfo.RUNNING_STATUS;
import static org.apache.ignite.tcservice.util.XmlUtil.xmlEscapeText;

/**
 * TC Bot Visa Facade. Provides method for TC Bot Visa obtaining. Contains features for adding comment to the ticket
 * based on latest state.
 */
public class TcBotTriggerAndSignOffService {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TcBotTriggerAndSignOffService.class);

    /** */
    private static final ThreadLocal<DateFormat> THREAD_FORMATTER = new ThreadLocal<DateFormat>() {
        @Override protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
    };

    /** Build observer provider. */
    @Inject Provider<BuildObserver> buildObserverProvider;

    /** GitHub connection ignited provider. */
    @Inject IGitHubConnIgnitedProvider gitHubConnIgnitedProvider;

    /** TC ignited provider. */
    @Inject ITeamcityIgnitedProvider tcIgnitedProv;

    /** JIRA provider */
    @Inject private IJiraIgnitedProvider jiraIgnProv;

    /** */
    @Inject private VisasHistoryStorage visasHistStorage;

    /** */
    @Inject private IStringCompactor strCompactor;

    /** */
    @Inject IStringCompactor compactor;

    /** Helper. */
    @Inject ITcBotBgAuth tcBotBgAuth;

    /** PR chain processor. */
    @Inject PrChainsProcessor prChainsProcessor;

    /** Config. */
    @Inject ITcBotConfig cfg;

    @Inject
    BranchTicketMatcher ticketMatcher;

    /** Jackson serializer. */
    private final ObjectMapper objMapper = new ObjectMapper();

    /** */
    public void startObserver() {
        buildObserverProvider.get();
    }

    /** */
    public List<VisaStatus> getVisasStatus(ITcBotUserCreds prov) {
        List<VisaStatus> visaStatuses = new ArrayList<>();


        for (VisaRequest visaRequest : visasHistStorage.getVisas()) {
            VisaStatus visaStatus = new VisaStatus();

            String srvCodeOrAlias = visaRequest.getInfo().srvId;

            if(!prov.hasAccess(srvCodeOrAlias))
                continue;

            ITeamcityIgnited tcIgn = tcIgnitedProv.server(srvCodeOrAlias, prov);

            IJiraIgnited jiraIntegration = jiraIgnProv.server(srvCodeOrAlias);

            BuildsInfo info = visaRequest.getInfo();

            Visa visa = visaRequest.getResult();

            boolean isObserving = visaRequest.isObserving();

            visaStatus.date = THREAD_FORMATTER.get().format(info.date);
            visaStatus.branchName = info.branchForTc;
            visaStatus.userName = info.userName;
            visaStatus.ticket = info.ticket;
            visaStatus.buildTypeId = info.buildTypeId;

            BuildTypeRefCompacted bt = tcIgn.getBuildTypeRef(info.buildTypeId);
            visaStatus.buildTypeName = (bt != null ? bt.name(compactor) : visaStatus.buildTypeId);
            visaStatus.baseBranchForTc = info.baseBranchForTc;

            String buildsStatus = visaStatus.status = info.getStatus(tcIgn, strCompactor);

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
                visaStatus.cancelUrl = "/rest/visa/cancel?server=" + srvCodeOrAlias + "&branch=" + info.branchForTc;

            visaStatuses.add(visaStatus);
        }

        return visaStatuses;
    }

    /**
     * @param srvId Server id.
     * @param creds Credentials.
     * @return Mutes for given server-project pair.
     */
    public Set<MuteInfo> getMutes(String srvId, String projectId, ITcBotUserCreds creds) {
        ITeamcityIgnited ignited = tcIgnitedProv.server(srvId, creds);

        Set<MuteInfo> mutes = ignited.getMutes(projectId);

        IJiraIgnited jiraIgn = jiraIgnProv.server(srvId);

        String browseUrl = jiraIgn.generateTicketUrl("");

        insertTicketStatus(mutes, jiraIgn.getTickets(), browseUrl);

        for (MuteInfo info : mutes)
            info.assignment.muteDate = THREAD_FORMATTER.get().format(new Date(info.assignment.timestamp()));

        return mutes;
    }

    /**
     * Insert ticket status for all mutes, if they have ticket in description.
     *
     * @param mutes Mutes.
     * @param tickets Tickets.
     * @param browseUrl JIRA URL for browsing tickets, e.g. https://issues.apache.org/jira/browse/
     */
    private void insertTicketStatus(Set<MuteInfo> mutes, Collection<Ticket> tickets, String browseUrl) {
        for (MuteInfo mute : mutes) {
            if (F.isEmpty(mute.assignment.text))
                continue;

            int pos = mute.assignment.text.indexOf(browseUrl);

            if (pos == -1)
                continue;

            for (Ticket ticket : tickets) {
                String muteTicket = mute.assignment.text.substring(pos + browseUrl.length());

                if (ticket.key.equals(muteTicket)) {
                    mute.ticketStatus = ticket.status();

                    break;
                }
            }
        }
    }

    @NotNull public String triggerBuildsAndObserve(
        @Nullable String srvCodeOrAlias,
        @Nullable String branchForTc,
        @Nonnull String parentSuiteId,
        @Nonnull String suiteIdList,
        @Nullable Boolean top,
        @Nullable Boolean observe,
        @Nullable String ticketId,
        @Nullable String prNum,
        @Nullable String baseBranchForTc,
        @Nullable ITcBotUserCreds prov) {
        String jiraRes = "";

        ITeamcityIgnited teamcity = tcIgnitedProv.server(srvCodeOrAlias, prov);

        IGitHubConnIgnited ghIgn = gitHubConnIgnitedProvider.server(srvCodeOrAlias);

        if(!Strings.isNullOrEmpty(prNum)) {
            try {
                PullRequest pr = ghIgn.getPullRequest(Integer.parseInt(prNum));

                if(pr!=null) {
                    String shaShort = pr.lastCommitShaShort();

                    if(shaShort!=null)
                         jiraRes = "Actual commit: " + shaShort + ". ";
                }
            }
            catch (NumberFormatException e) {
                logger.error("PR & TC state checking failed" , e);
            }
        }

        String[] suiteIds = Objects.requireNonNull(suiteIdList).split(",");

        //todo consult if there are change differences here https://ci.ignite.apache.org/app/rest/changes?locator=buildType:(id:IgniteTests24Java8_Cache7),pending:true,branch:pull%2F6224%2Fhead
        Build[] builds = new Build[suiteIds.length];
        Set<Integer> buildidsToSync = new HashSet<>();

        for (int i = 0; i < suiteIds.length; i++) {
            T2<Build, Set<Integer>> objects = teamcity.triggerBuild(suiteIds[i], branchForTc, false, top != null && top, new HashMap<>(),
                false, "");
            buildidsToSync.addAll(objects.get2());
            builds[i] = objects.get1();
        }

        teamcity.fastBuildsSync(buildidsToSync);

        if (observe != null && observe)
            jiraRes += observeJira(srvCodeOrAlias, branchForTc, ticketId, prov, parentSuiteId, baseBranchForTc, builds);

        return jiraRes;
    }

    /**
     * @param srvId Server id.
     * @param branchForTc Branch for TeamCity.
     * @param ticketFullName JIRA ticket number.
     * @param prov Credentials.
     * @param baseBranchForTc Reference branch in TC identification.
     * @param builds Builds.
     * @return Message with result.
     */
    private String observeJira(
        String srvId,
        String branchForTc,
        @Nullable String ticketFullName,
        ITcBotUserCreds prov,
        String parentSuiteId,
        String baseBranchForTc,
        Build... builds
    ) {
        try {
            ticketFullName = ticketMatcher.resolveTicketFromBranch(srvId, ticketFullName, branchForTc);
        }
        catch (BranchTicketMatcher.TicketNotFoundException e) {
            logger.info("", e);
            return "JIRA ticket will not be notified after the tests are completed - " +
                "exception happened when server tried to get ticket ID from Pull Request [errMsg="
                + e.getMessage();
        }

        String user = prov.getUser(srvId);
        if (user == null)
            user = prov.getPrincipalId();

        buildObserverProvider.get().observe(srvId, ticketFullName, branchForTc, parentSuiteId, baseBranchForTc, user, builds);

        if (!tcBotBgAuth.isServerAuthorized())
            return "Ask server administrator to authorize the Bot to enable JIRA notifications.";

        return "JIRA ticket " + ticketFullName + " will be notified after the tests are completed.";
    }

    /**
     * @param srvId Server id.
     * @param branchForTc Branch for tc.
     * @param suiteId Suite id.
     * @param ticketFullName Ticket full name with IGNITE- prefix.
     * @param baseBranchForTc base branch in TC identification
     * @param prov Prov.
     */
    @NotNull
    public SimpleResult commentJiraEx(
        @Nullable String srvId,
        @Nullable String branchForTc,
        @Nullable String suiteId,
        @Nullable String ticketFullName,
        @Nullable String baseBranchForTc,
        ITcBotUserCreds prov) {

        try {
            ticketFullName = ticketMatcher.resolveTicketFromBranch(srvId, ticketFullName, branchForTc);
        }
        catch (BranchTicketMatcher.TicketNotFoundException e) {
            logger.info("", e);
            return new SimpleResult("JIRA wasn't commented: TicketNotFoundException: <br>" + e.getMessage());
        }

        String user = prov.getUser(srvId);
        if (user == null)
            user = prov.getPrincipalId();

        BuildsInfo buildsInfo = new BuildsInfo(srvId, ticketFullName, branchForTc, suiteId, baseBranchForTc, user);

        VisaRequest lastVisaReq = visasHistStorage.getLastVisaRequest(buildsInfo.getContributionKey());

        if (Objects.nonNull(lastVisaReq) && lastVisaReq.isObserving())
            return new SimpleResult("Jira wasn't commented." +
                " \"Re-run possible blockers & Comment JIRA\" was triggered for current branch." +
                " Wait for the end or cancel exsiting observing.");

        Visa visa = notifyJira(srvId, prov, suiteId, branchForTc, ticketFullName, baseBranchForTc);

        visasHistStorage.put(new VisaRequest(buildsInfo).setResult(visa));

        return new SimpleResult(visa.status);
    }

    /**
     * @param srvCodeOrAlias Server id.
     * @param credsProv Credentials
     */
    public List<ContributionToCheck> getContributionsToCheck(String srvCodeOrAlias,
        ITcBotUserCreds credsProv) {
        IJiraIgnited jiraIntegration = jiraIgnProv.server(srvCodeOrAlias);

        IGitHubConnIgnited gitHubConnIgnited = gitHubConnIgnitedProvider.server(srvCodeOrAlias);

        ITeamcityIgnited tcIgn = tcIgnitedProv.server(srvCodeOrAlias, credsProv);

        List<PullRequest> prs = gitHubConnIgnited.getPullRequests();

        Set<Ticket> tickets = jiraIntegration.getTickets();

        IJiraServerConfig jiraCfg = jiraIntegration.config();
        IGitHubConfig ghCfg = gitHubConnIgnited.config();

        String defBtForTcServ = findDefaultBuildType(srvCodeOrAlias);

        List<ContributionToCheck> contribsList = new ArrayList<>();

        if (prs != null) {
            prs.forEach(pr -> {
                ContributionToCheck c = new ContributionToCheck();

                c.prNumber = pr.getNumber();
                c.prTitle = pr.getTitle();
                c.prHtmlUrl = pr.htmlUrl();
                c.prHeadCommit = pr.lastCommitShaShort();
                c.prTimeUpdate = pr.getTimeUpdate();

                GitHubUser user = pr.gitHubUser();
                if (user != null) {
                    c.prAuthor = user.login();
                    c.prAuthorAvatarUrl = user.avatarUrl();
                }
                else {
                    c.prAuthor = "";
                    c.prAuthorAvatarUrl = "";
                }

                Ticket ticket = ticketMatcher.resolveTicketIdForPrBasedContrib(tickets, jiraCfg, pr.getTitle());

                if (ticket == null || ticket.id == 0) {
                    if (pr.head() != null && pr.head().ref() != null)
                        ticket = ticketMatcher.resolveTicketIdForPrBasedContrib(tickets, jiraCfg, pr.head().ref());
                }

                c.jiraIssueId = ticket == null ? null : ticket.key;
                c.jiraStatusName = ticket == null ? null : ticket.status();

                if (!Strings.isNullOrEmpty(c.jiraIssueId)
                        && jiraCfg.getUrl() != null)
                    c.jiraIssueUrl = jiraIntegration.generateTicketUrl(c.jiraIssueId);

                findBuildsForPr(defBtForTcServ, Integer.toString(pr.getNumber()), gitHubConnIgnited, tcIgn)
                        .stream()
                        .map(buildRefCompacted -> buildRefCompacted.branchName(compactor))
                        .findAny()
                        .ifPresent(bName -> c.tcBranchName = bName);

                contribsList.add(c);
            });
        }

        List<String> branches = gitHubConnIgnited.getBranches();

        List<Ticket> activeTickets = tickets.stream().filter(Ticket::isActiveContribution).collect(Collectors.toList());

        activeTickets.forEach(ticket -> {
            String branch = ticketMatcher.resolveTcBranchForPrLess(ticket,
                jiraCfg,
                ghCfg);

            if (Strings.isNullOrEmpty(branch))
                return; // nothing to do if branch was not resolved


            if (!branches.contains(branch)
                && tcIgn.getAllBuildsCompacted(defBtForTcServ, branch).isEmpty())
                return; //Skipping contributions without builds

            ContributionToCheck contribution = new ContributionToCheck();

            contribution.jiraIssueId = ticket.key;
            contribution.jiraStatusName = ticket.status();
            contribution.jiraIssueUrl = jiraIntegration.generateTicketUrl(ticket.key);
            contribution.tcBranchName = branch;

            if (branch.startsWith(ghCfg.gitBranchPrefix())) {
                String branchTc = branch.substring(ghCfg.gitBranchPrefix().length());

                try {
                    contribution.prNumber = -Integer.valueOf(branchTc);
                }
                catch (NumberFormatException e) {
                    logger.error("PR less contribution has invalid branch name", e);
                }
            }

            contribution.prTitle = ticket.fields.summary;
            contribution.prHtmlUrl = "";
            contribution.prHeadCommit = "";
            contribution.prTimeUpdate = ""; //todo ticket updateTime

            contribution.prAuthor = "";
            contribution.prAuthorAvatarUrl = "";

            contribsList.add(contribution);
        });

        return contribsList;
    }

    /**
     * @param suiteId Suite id.
     * @param prId Pr id from {@link ContributionToCheck#prNumber}. Negative value imples branch number for PR-less.
     * @param ghConn Gh connection.
     * @param srv TC Server connection.
     */
    @Nonnull
    private List<BuildRefCompacted> findBuildsForPr(String suiteId,
        String prId,
        IGitHubConnIgnited ghConn,
        ITeamcityIgnited srv) {

        List<BuildRefCompacted> buildHist = srv.getAllBuildsCompacted(suiteId,
                branchForTcDefault(prId, ghConn));

        if (!buildHist.isEmpty())
            return buildHist;

        Integer prNum = Integer.valueOf(prId);
        if (prNum < 0)
            return buildHist; // Don't iterate for other options if PR ID is absent

        buildHist = srv.getAllBuildsCompacted(suiteId, branchForTcB(prId));

        if (!buildHist.isEmpty())
            return buildHist;

        String bracnhToCheck =
                ghConn.config().isPreferBranches()
                        ? branchForTcA(prId) // for prefer branches mode it was already checked in default
                        : getPrBranch(ghConn, prNum);

        if (bracnhToCheck == null)
            return Collections.emptyList();

        buildHist = srv.getAllBuildsCompacted(suiteId, bracnhToCheck);

        return buildHist;
    }

    @Nullable
    private String getPrBranch(IGitHubConnIgnited ghConn, Integer prNum) {
        PullRequest pr = ghConn.getPullRequest(prNum);

        if (pr == null)
            return null;

        GitHubBranch head = pr.head();

        if (head == null)
            return null;

        return head.ref();
    }

    /**
     * @param prId Pr id from {@link ContributionToCheck#prNumber}. Negative value imples branch number to be used for
     * PR-less contributions.
     * @param ghConn Github integration.
     */
    private String branchForTcDefault(String prId, IGitHubConnIgnited ghConn) {
        Integer prNum = Integer.valueOf(prId);
        if (prNum < 0)
            return ghConn.gitBranchPrefix() + (-prNum); // Checking "ignite-10930" builds only

        if (ghConn.config().isPreferBranches()) {
            String ref = getPrBranch(ghConn, prNum);
            if (ref != null)
                return ref;
        }

        return branchForTcA(prId);
    }

    private String branchForTcA(String prId) {
        return "pull/" + prId + "/head";
    }

    private String branchForTcB(String prId) {
        return "pull/" + prId + "/merge";
    }

    /**
     * @param srvCodeOrAlias Server (service) internal code.
     * @param prov Prov.
     * @param prId Pr id from {@link ContributionToCheck#prNumber}. Negative value imples branch number (with
     * appropriate prefix from GH config).
     */
    public Set<ContributionCheckStatus> contributionStatuses(String srvCodeOrAlias, ITcBotUserCreds prov,
        String prId) {
        Set<ContributionCheckStatus> statuses = new LinkedHashSet<>();

        ITeamcityIgnited teamcity = tcIgnitedProv.server(srvCodeOrAlias, prov);

        String defaultBuildType = findDefaultBuildType(srvCodeOrAlias);

        IGitHubConnIgnited ghConn = gitHubConnIgnitedProvider.server(srvCodeOrAlias);

        Preconditions.checkState(ghConn.config().code().equals(srvCodeOrAlias));

        List<String> compositeBuildTypeIds = findApplicableBuildTypes(srvCodeOrAlias, teamcity);

        for (String btId : compositeBuildTypeIds) {
            List<BuildRefCompacted> buildsForBt = findBuildsForPr(btId, prId, ghConn, teamcity);

            ContributionCheckStatus contributionAgainstSuite = buildsForBt.isEmpty()
                ? new ContributionCheckStatus(btId, branchForTcDefault(prId, ghConn))
                : contributionStatus(srvCodeOrAlias, btId, buildsForBt, teamcity, ghConn, prId);

            if(Objects.equals(btId, defaultBuildType))
                contributionAgainstSuite.defaultBuildType = true;

            statuses.add(contributionAgainstSuite);
        }

        return statuses;
    }

    /**
     *
     * @param srvIdOrAlias TC server ID or reference to it.
     * @param teamcity Teamcity.
     * @return list of build types which may be taken for
     */
    public List<String> findApplicableBuildTypes(String srvIdOrAlias, ITeamcityIgnited teamcity) {
        String defBtForMaster = findDefaultBuildType(srvIdOrAlias);

        BuildTypeCompacted buildType = Strings.isNullOrEmpty(defBtForMaster)
            ? null
            : teamcity.getBuildType(defBtForMaster);

        List<String> compositeBuildTypeIds;
        String projectId;
        if (buildType != null) {
            projectId = compactor.getStringFromId(buildType.projectId());
            compositeBuildTypeIds = teamcity.getCompositeBuildTypesIdsSortedByBuildNumberCounter(projectId);
        }
        else {
            //for case build type not found, actualizing all projects resync
            List<String> projects = teamcity.getAllProjectsIds();

            for (String pId : projects)
                teamcity.getCompositeBuildTypesIdsSortedByBuildNumberCounter(pId);

            compositeBuildTypeIds = new ArrayList<>();

            if (!Strings.isNullOrEmpty(defBtForMaster))
                compositeBuildTypeIds.add(defBtForMaster);
        }

        return compositeBuildTypeIds;
    }

    /**
     * @param srvIdOrAlias Server id. May be weak reference to TC
     * @return Some build type included into tracked branches with default branch.
     */
    @NotNull
    private String findDefaultBuildType(String srvIdOrAlias) {
        StringBuilder buildTypeId = new StringBuilder();

        ITcServerConfig tcCfg = cfg.getTeamcityConfig(srvIdOrAlias);
        String visaBuildType = tcCfg.defaultVisaSuiteId();

        if(!Strings.isNullOrEmpty(visaBuildType))
            return visaBuildType;

        String trBranch = tcCfg.defaultTrackedBranch();

        String realTcId = Strings.isNullOrEmpty(tcCfg.reference()) ? srvIdOrAlias : tcCfg.reference();

        cfg.getTrackedBranches()
            .get(trBranch)
            .ifPresent(
                b -> b.chainsStream()
                    .filter(c -> Objects.equals(realTcId, c.serverCode()))
                    .filter(c -> ITeamcity.DEFAULT.equals(c.tcBranch()))
                    .findFirst()
                    .ifPresent(ch -> buildTypeId.append(ch.tcSuiteId())));

        return buildTypeId.toString();
    }

    /**
     * @param srvId Server id.
     * @param suiteId Suite id.
     * @param builds Build references.
     * @param ghConn GitHub integration.
     */
    public ContributionCheckStatus contributionStatus(String srvId, String suiteId, List<BuildRefCompacted> builds,
        ITeamcityIgnited teamcity, IGitHubConnIgnited ghConn, String prId) {
        ContributionCheckStatus status = new ContributionCheckStatus(suiteId);

        List<BuildRefCompacted> finishedOrCancelled = builds.stream()
            .filter(t -> t.isFinished(compactor)).collect(Collectors.toList());

        if (!finishedOrCancelled.isEmpty()) {
            BuildRefCompacted buildRefCompacted = finishedOrCancelled.get(0);

            status.suiteIsFinished = !buildRefCompacted.isCancelled(compactor);
            status.branchWithFinishedSuite = buildRefCompacted.branchName(compactor);

            FatBuildCompacted fatBuild = teamcity.getFatBuild(buildRefCompacted.id(), SyncMode.NONE);

            String commit = teamcity.getLatestCommitVersion(fatBuild);

            if (!Strings.isNullOrEmpty(commit) && commit.length() > PullRequest.INCLUDE_SHORT_VER) {
                status.finishedSuiteCommit
                    = commit.substring(0, PullRequest.INCLUDE_SHORT_VER).toLowerCase();
            }
        }
        else {
            status.branchWithFinishedSuite = null;
            status.finishedSuiteCommit = null;
            status.suiteIsFinished = false;
        }

        if (status.branchWithFinishedSuite != null)
            status.resolvedBranch = status.branchWithFinishedSuite;
            //todo take into account running/queued
        else
            status.resolvedBranch = !builds.isEmpty() ? builds.get(0).branchName(compactor) : branchForTcDefault(prId, ghConn);

        String observationsStatus = buildObserverProvider.get().getObservationStatus(new ContributionKey(srvId, status.resolvedBranch));

        status.observationsStatus = Strings.emptyToNull(observationsStatus);

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

    public CurrentVisaStatus currentVisaStatus(String srvCode, ITcBotUserCreds prov, String buildTypeId,
                                               String tcBranch) {
        CurrentVisaStatus status = new CurrentVisaStatus();

        List<ShortSuiteUi> suitesStatuses
            = prChainsProcessor.getBlockersSuitesStatuses(buildTypeId, tcBranch, srvCode, prov, SyncMode.NONE, null);

        if (suitesStatuses == null)
            return status;

        status.blockers = suitesStatuses.stream().mapToInt(ShortSuiteUi::totalBlockers).sum();

        return status;
    }

    /**
     * Produce visa message(see {@link Visa}) based on passed parameters and publish it as a comment for specified
     * ticket on Jira server.
     *
     * @param srvCodeOrAlias TC Server ID to take information about token from.
     * @param prov Credentials.
     * @param buildTypeId Build type ID, for which visa was ordered.
     * @param branchForTc Branch for TeamCity.
     * @param ticket JIRA ticket full name. E.g. IGNITE-5555
     * @param baseBranchForTc Base branch in TC identification
     * @return {@link Visa} instance.
     */
    public Visa notifyJira(
        String srvCodeOrAlias,
        ITcBotUserCreds prov,
        String buildTypeId,
        String branchForTc,
        String ticket,
        @Nullable String baseBranchForTc) {
        ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, prov);

        IJiraIgnited jira = jiraIgnProv.server(srvCodeOrAlias);

        List<Integer> builds = tcIgnited.getLastNBuildsFromHistory(buildTypeId, branchForTc, 1);

        if (builds.isEmpty())
            return new Visa("JIRA wasn't commented - no finished builds to analyze.");

        Integer buildId = builds.get(0);

        FatBuildCompacted fatBuild = tcIgnited.getFatBuild(buildId);
        Build build = fatBuild.toBuild(compactor);

        build.webUrl = tcIgnited.host() + "viewLog.html?buildId=" + build.getId() + "&buildTypeId=" + build.buildTypeId;

        int blockers;

        JiraCommentResponse res;

        try {
            String baseBranch = Strings.isNullOrEmpty(baseBranchForTc) ? prChainsProcessor.dfltBaseTcBranch(srvCodeOrAlias) : baseBranchForTc;

            List<ShortSuiteUi> suitesStatuses = prChainsProcessor.getBlockersSuitesStatuses(buildTypeId, build.branchName, srvCodeOrAlias, prov,
                SyncMode.RELOAD_QUEUED,
                baseBranch);

            if (suitesStatuses == null)
                return new Visa("JIRA wasn't commented - no finished builds to analyze." +
                    " Check builds availabiliy for branch: " + build.branchName + "/" + baseBranch);

            blockers = suitesStatuses.stream().mapToInt(ShortSuiteUi::totalBlockers).sum();

            String comment = generateJiraComment(suitesStatuses, build.webUrl, buildTypeId, tcIgnited, blockers, build.branchName, baseBranch);


            res = objMapper.readValue(jira.postJiraComment(ticket, comment), JiraCommentResponse.class);
        }
        catch (Exception e) {
            String errMsg = "Exception happened during commenting JIRA ticket " +
                "[build=" + build.getId() + ", errMsg=" + e.getMessage() + ']';

            logger.error(errMsg);

            return new Visa("JIRA wasn't commented - " + errMsg);
        }

        return new Visa(Visa.JIRA_COMMENTED, res, blockers);
    }

    /**
     * @param suites Suite Current Status.
     * @param webUrl Build URL.
     * @param buildTypeId Build type ID, for which visa was ordered.
     * @param tcIgnited TC service.
     * @param blockers Count of blockers.
     * @param branchName TC Branch name, which was tested.
     * @param baseBranch TC Base branch used for comment
     * @return Comment, which should be sent to the JIRA ticket.
     */
    private String generateJiraComment(List<ShortSuiteUi> suites, String webUrl, String buildTypeId,
        ITeamcityIgnited tcIgnited, int blockers, String branchName, String baseBranch) {
        BuildTypeRefCompacted bt = tcIgnited.getBuildTypeRef(buildTypeId);

        String suiteNameUsedForVisa = (bt != null ? bt.name(compactor) : buildTypeId);

        StringBuilder res = new StringBuilder();

        String baseBranchDisp = (Strings.isNullOrEmpty(baseBranch) || ITeamcity.DEFAULT.equals(baseBranch))
            ? "master" :  baseBranch ;
        for (ShortSuiteUi suite : suites) {
            res.append("{color:#d04437}");

            res.append(jiraEscText(suite.name)).append("{color}");

            int totalBlockerTests = suite.testFailures().size();
            res.append(" [[tests ").append(totalBlockerTests);

            if (suite.result != null && !suite.result.isEmpty())
                res.append(' ').append(suite.result);

            res.append('|').append(suite.webToBuild).append("]]\\n");

            int cnt = 0;

            for (ShortTestFailureUi failure : suite.testFailures()) {
                res.append("* ");

                if (failure.suiteName != null && failure.testName != null)
                    res.append(jiraEscText(failure.suiteName)).append(": ").append(jiraEscText(failure.testName));
                else
                    res.append(jiraEscText(failure.name));

                res.append(" - ").append(jiraEscText(failure.blockerComment));

                res.append("\\n");

                cnt++;
                if (cnt > 10) {
                    res.append("... and ").append(totalBlockerTests - cnt).append(" tests blockers\\n");

                    break;
                }
            }

            res.append("\\n");
        }

        String suiteNameForComment = jiraEscText(suiteNameUsedForVisa);

        String branchNameForComment = jiraEscText("Branch: [" + branchName + "] ");

        String baseBranchForComment = jiraEscText("Base: [" + baseBranchDisp + "] ");
        String branchVsBaseComment = branchNameForComment + baseBranchForComment;

        if (res.length() > 0) {
            String hdrPanel = "{panel:title=" + branchVsBaseComment + ": Possible Blockers (" + blockers + ")|" +
                "borderStyle=dashed|borderColor=#ccc|titleBGColor=#F7D6C1}\\n";

            res.insert(0, hdrPanel)
                .append("{panel}");
        }
        else {
            res.append("{panel:title=").append(branchVsBaseComment).append(": No blockers found!|")
                .append("borderStyle=dashed|borderColor=#ccc|titleBGColor=#D6F7C1}{panel}");
        }

        res.append("\\n").append("[TeamCity *").append(suiteNameForComment).append("* Results|").append(webUrl).append(']');

        return xmlEscapeText(res.toString());
    }

    /**
     * Escapes text for JIRA.
     * @param txt Txt.
     */
    private String jiraEscText(String txt) {
        if(Strings.isNullOrEmpty(txt))
            return "";

        return txt.replaceAll("\\|", "/");
    }
}
