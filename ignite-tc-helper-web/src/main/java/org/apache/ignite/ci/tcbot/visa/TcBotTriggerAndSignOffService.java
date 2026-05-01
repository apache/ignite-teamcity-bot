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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import org.apache.ignite.ci.tcbot.jira.JiraCommentsGenerator;
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
import org.apache.ignite.jiraservice.JiraTicketStatusCode;
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.pr.BranchTicketMatcher;
import org.apache.ignite.tcbot.engine.pr.PrChainsProcessor;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteNewTestsUi;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteUi;
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

/**
 * TC Bot Visa Facade. Provides method for TC Bot Visa obtaining. Contains features for adding comment to the ticket
 * based on latest state.
 */
public class TcBotTriggerAndSignOffService {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TcBotTriggerAndSignOffService.class);

    /** Slow visa operation threshold. */
    private static final long SLOW_VISA_OPERATION_WARN_MS = Long.getLong("tcbot.visa.slowOperationWarnMs", 1000L);

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
                    mute.ticketStatus = JiraTicketStatusCode.text(ticket.status());

                    break;
                }
            }
        }
    }

    @AutoProfiling
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
        @Nonnull Boolean cleanRebuild,
        @Nullable ITcBotUserCreds prov) {
        long startNanos = System.nanoTime();
        long initNanos = 0;
        long prLookupNanos = 0;
        long triggerNanos = 0;
        long syncNanos = 0;
        long observeNanos = 0;
        int triggeredBuilds = 0;

        String jiraRes = "";

        long stepStart = System.nanoTime();
        ITeamcityIgnited teamcity = tcIgnitedProv.server(srvCodeOrAlias, prov);

        IGitHubConnIgnited ghIgn = gitHubConnIgnitedProvider.server(srvCodeOrAlias);
        initNanos = System.nanoTime() - stepStart;

        if(!Strings.isNullOrEmpty(prNum)) {
            try {
                stepStart = System.nanoTime();
                PullRequest pr = ghIgn.getPullRequest(Integer.parseInt(prNum));
                prLookupNanos += System.nanoTime() - stepStart;

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
            stepStart = System.nanoTime();
            T2<Build, Set<Integer>> objects = teamcity.triggerBuild(suiteIds[i], branchForTc, cleanRebuild, top != null && top, new HashMap<>(),
                false, "");
            triggerNanos += System.nanoTime() - stepStart;
            triggeredBuilds++;
            buildidsToSync.addAll(objects.get2());
            builds[i] = objects.get1();
        }

        stepStart = System.nanoTime();
        teamcity.fastBuildsSync(buildidsToSync);
        syncNanos = System.nanoTime() - stepStart;

        if (observe != null && observe) {
            stepStart = System.nanoTime();
            jiraRes += observeJira(srvCodeOrAlias, branchForTc, ticketId, prov, parentSuiteId, baseBranchForTc, builds);
            observeNanos = System.nanoTime() - stepStart;
        }

        long totalMs = millisSince(startNanos);
        if (totalMs >= SLOW_VISA_OPERATION_WARN_MS) {
            logger.warn("Slow visa trigger budget: server={}, branch={}, parentSuite={}, suites={}, totalMs={}, " +
                    "initMs={}, prLookupMs={}, triggerMs={}, fastSyncMs={}, observeMs={}, triggeredBuilds={}, " +
                    "buildIdsToSync={}",
                srvCodeOrAlias, branchForTc, parentSuiteId, suiteIdList, totalMs,
                nanosToMillis(initNanos), nanosToMillis(prLookupNanos), nanosToMillis(triggerNanos),
                nanosToMillis(syncNanos), nanosToMillis(observeNanos), triggeredBuilds, buildidsToSync.size());
        }

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
    @AutoProfiling
    public List<ContributionToCheck> getContributionsToCheck(String srvCodeOrAlias,
        ITcBotUserCreds credsProv) {
        long startNanos = System.nanoTime();
        long serviceResolveNanos;
        long prsLoadNanos;
        long ticketsLoadNanos;
        long defaultBuildTypeNanos;
        long prLoopNanos;
        AtomicLong prTicketResolveNanos = new AtomicLong();
        AtomicLong prBuildLookupNanos = new AtomicLong();
        long branchesLoadNanos;
        long activeTicketsFilterNanos;
        long activeTicketsLoopNanos;
        AtomicLong activeBranchResolveNanos = new AtomicLong();
        AtomicLong activeBranchCheckNanos = new AtomicLong();
        AtomicLong activeBuildLookupNanos = new AtomicLong();
        AtomicInteger prBuildLookupCnt = new AtomicInteger();
        AtomicInteger activeBuildLookupCnt = new AtomicInteger();

        long stepStart = System.nanoTime();
        IJiraIgnited jiraIntegration = jiraIgnProv.server(srvCodeOrAlias);

        IGitHubConnIgnited gitHubConnIgnited = gitHubConnIgnitedProvider.server(srvCodeOrAlias);

        ITeamcityIgnited tcIgn = tcIgnitedProv.server(srvCodeOrAlias, credsProv);
        serviceResolveNanos = System.nanoTime() - stepStart;

        stepStart = System.nanoTime();
        List<PullRequest> prs = gitHubConnIgnited.getPullRequests();
        prsLoadNanos = System.nanoTime() - stepStart;

        stepStart = System.nanoTime();
        Set<Ticket> tickets = jiraIntegration.getTickets();
        ticketsLoadNanos = System.nanoTime() - stepStart;

        Map<String, Ticket> ticketsByKey = tickets.stream()
            .filter(ticket -> ticket.key != null)
            .collect(Collectors.toMap(ticket -> ticket.key, ticket -> ticket, (first, second) -> first));

        IJiraServerConfig jiraCfg = jiraIntegration.config();
        IGitHubConfig ghCfg = gitHubConnIgnited.config();

        stepStart = System.nanoTime();
        String defBtForTcServ = findDefaultBuildType(srvCodeOrAlias);
        defaultBuildTypeNanos = System.nanoTime() - stepStart;

        List<ContributionToCheck> contribsList = new ArrayList<>();

        stepStart = System.nanoTime();
        if (prs != null) {
            prs.forEach(pr -> {
                ContributionToCheck c = new ContributionToCheck();
                String prHeadRef = pr.head() == null ? null : pr.head().ref();

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

                long ticketStart = System.nanoTime();
                Ticket ticket = ticketMatcher.resolveTicketIdForPrBasedContrib(tickets, ticketsByKey, jiraCfg, pr.getTitle());
                prTicketResolveNanos.addAndGet(System.nanoTime() - ticketStart);

                if (ticket == null || ticket.id == 0) {
                    if (prHeadRef != null) {
                        ticketStart = System.nanoTime();
                        ticket = ticketMatcher.resolveTicketIdForPrBasedContrib(tickets, ticketsByKey, jiraCfg, prHeadRef);
                        prTicketResolveNanos.addAndGet(System.nanoTime() - ticketStart);
                    }
                }

                c.jiraIssueId = ticket == null ? null : ticket.key;
                c.jiraStatusName = ticket == null ? null : JiraTicketStatusCode.text(ticket.status());

                if (!Strings.isNullOrEmpty(c.jiraIssueId)
                        && jiraCfg.getUrl() != null)
                    c.jiraIssueUrl = jiraIntegration.generateTicketUrl(c.jiraIssueId);

                long buildLookupStart = System.nanoTime();
                findBuildsForPr(defBtForTcServ, Integer.toString(pr.getNumber()), prHeadRef, gitHubConnIgnited, tcIgn)
                        .stream()
                        .map(buildRefCompacted -> buildRefCompacted.branchName(compactor))
                        .findAny()
                        .ifPresent(bName -> c.tcBranchName = bName);
                prBuildLookupNanos.addAndGet(System.nanoTime() - buildLookupStart);
                prBuildLookupCnt.incrementAndGet();

                contribsList.add(c);
            });
        }
        prLoopNanos = System.nanoTime() - stepStart;

        stepStart = System.nanoTime();
        List<String> branches = gitHubConnIgnited.getBranches();
        Set<String> branchesSet = new HashSet<>(branches);
        branchesLoadNanos = System.nanoTime() - stepStart;

        stepStart = System.nanoTime();
        List<Ticket> activeTickets = tickets.stream()
            .filter(ticket -> JiraTicketStatusCode.isActiveContribution(ticket.status()))
            .collect(Collectors.toList());
        activeTicketsFilterNanos = System.nanoTime() - stepStart;

        stepStart = System.nanoTime();
        activeTickets.forEach(ticket -> {
            long branchResolveStart = System.nanoTime();
            String branch = ticketMatcher.resolveTcBranchForPrLess(ticket,
                jiraCfg,
                ghCfg);
            activeBranchResolveNanos.addAndGet(System.nanoTime() - branchResolveStart);

            if (Strings.isNullOrEmpty(branch))
                return; // nothing to do if branch was not resolved

            long branchCheckStart = System.nanoTime();
            boolean branchExists = branchesSet.contains(branch);
            activeBranchCheckNanos.addAndGet(System.nanoTime() - branchCheckStart);

            if (!branchExists) {
                long buildLookupStart = System.nanoTime();
                boolean buildsExist = !tcIgn.getAllBuildsCompacted(defBtForTcServ, branch).isEmpty();
                activeBuildLookupNanos.addAndGet(System.nanoTime() - buildLookupStart);
                activeBuildLookupCnt.incrementAndGet();

                if (!buildsExist)
                    return; //Skipping contributions without builds
            }

            ContributionToCheck contribution = new ContributionToCheck();

            contribution.jiraIssueId = ticket.key;
            contribution.jiraStatusName = JiraTicketStatusCode.text(ticket.status());
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

            contribution.prTitle = ticket.fields.summary();
            contribution.prHtmlUrl = "";
            contribution.prHeadCommit = "";
            contribution.prTimeUpdate = ""; //todo ticket updateTime

            contribution.prAuthor = "";
            contribution.prAuthorAvatarUrl = "";

            contribsList.add(contribution);
        });
        activeTicketsLoopNanos = System.nanoTime() - stepStart;

        long totalMs = millisSince(startNanos);
        if (totalMs >= SLOW_VISA_OPERATION_WARN_MS) {
            logger.warn("Slow visa contributions budget: server={}, totalMs={}, result={}, prs={}, tickets={}, " +
                    "activeTickets={}, branches={}, serviceResolveMs={}, prsLoadMs={}, ticketsLoadMs={}, " +
                    "defaultBuildTypeMs={}, prLoopMs={}, prTicketResolveMs={}, prBuildLookupMs={}, " +
                    "prBuildLookups={}, branchesLoadMs={}, activeFilterMs={}, activeLoopMs={}, " +
                    "activeBranchResolveMs={}, activeBranchCheckMs={}, activeBuildLookupMs={}, activeBuildLookups={}",
                srvCodeOrAlias, totalMs, contribsList.size(), prs == null ? 0 : prs.size(), tickets.size(),
                activeTickets.size(), branches.size(), nanosToMillis(serviceResolveNanos), nanosToMillis(prsLoadNanos),
                nanosToMillis(ticketsLoadNanos), nanosToMillis(defaultBuildTypeNanos), nanosToMillis(prLoopNanos),
                nanosToMillis(prTicketResolveNanos.get()), nanosToMillis(prBuildLookupNanos.get()),
                prBuildLookupCnt.get(),
                nanosToMillis(branchesLoadNanos), nanosToMillis(activeTicketsFilterNanos),
                nanosToMillis(activeTicketsLoopNanos), nanosToMillis(activeBranchResolveNanos.get()),
                nanosToMillis(activeBranchCheckNanos.get()), nanosToMillis(activeBuildLookupNanos.get()),
                activeBuildLookupCnt.get());
        }

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
        return findBuildsForPr(suiteId, prId, null, ghConn, srv);
    }

    /**
     * @param suiteId Suite id.
     * @param prId Pr id from {@link ContributionToCheck#prNumber}. Negative value imples branch number for PR-less.
     * @param prHeadBranch PR head branch name, when it is already known by caller.
     * @param ghConn Gh connection.
     * @param srv TC Server connection.
     */
    @Nonnull
    private List<BuildRefCompacted> findBuildsForPr(String suiteId,
        String prId,
        @Nullable String prHeadBranch,
        IGitHubConnIgnited ghConn,
        ITeamcityIgnited srv) {

        List<BuildRefCompacted> buildHist = srv.getAllBuildsCompacted(suiteId,
                branchForTcDefault(prId, ghConn, prHeadBranch));

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
                        : prHeadBranch != null ? prHeadBranch : getPrBranch(ghConn, prNum);

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
        return branchForTcDefault(prId, ghConn, null);
    }

    /**
     * @param prId Pr id from {@link ContributionToCheck#prNumber}. Negative value imples branch number to be used for
     * PR-less contributions.
     * @param ghConn Github integration.
     * @param prHeadBranch PR head branch name, when it is already known by caller.
     */
    private String branchForTcDefault(String prId, IGitHubConnIgnited ghConn, @Nullable String prHeadBranch) {
        Integer prNum = Integer.valueOf(prId);
        if (prNum < 0)
            return ghConn.gitBranchPrefix() + (-prNum); // Checking "ignite-10930" builds only

        if (ghConn.config().isPreferBranches()) {
            String ref = prHeadBranch != null ? prHeadBranch : getPrBranch(ghConn, prNum);
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
    @AutoProfiling
    public Set<ContributionCheckStatus> contributionStatuses(String srvCodeOrAlias, ITcBotUserCreds prov,
        String prId) {
        long startNanos = System.nanoTime();
        long serviceResolveNanos;
        long defaultBuildTypeNanos;
        long buildTypesNanos;
        long buildLookupNanos = 0;
        long statusBuildNanos = 0;
        int buildLookupCnt = 0;

        Set<ContributionCheckStatus> statuses = new LinkedHashSet<>();

        long stepStart = System.nanoTime();
        ITeamcityIgnited teamcity = tcIgnitedProv.server(srvCodeOrAlias, prov);

        IGitHubConnIgnited ghConn = gitHubConnIgnitedProvider.server(srvCodeOrAlias);
        serviceResolveNanos = System.nanoTime() - stepStart;

        stepStart = System.nanoTime();
        String defaultBuildType = findDefaultBuildType(srvCodeOrAlias);
        defaultBuildTypeNanos = System.nanoTime() - stepStart;

        Preconditions.checkState(ghConn.config().code().equals(srvCodeOrAlias));

        stepStart = System.nanoTime();
        List<String> compositeBuildTypeIds = findApplicableBuildTypes(srvCodeOrAlias, teamcity);
        buildTypesNanos = System.nanoTime() - stepStart;

        for (String btId : compositeBuildTypeIds) {
            stepStart = System.nanoTime();
            List<BuildRefCompacted> buildsForBt = findBuildsForPr(btId, prId, ghConn, teamcity);
            buildLookupNanos += System.nanoTime() - stepStart;
            buildLookupCnt++;

            stepStart = System.nanoTime();
            ContributionCheckStatus contributionAgainstSuite = buildsForBt.isEmpty()
                ? new ContributionCheckStatus(btId, branchForTcDefault(prId, ghConn))
                : contributionStatus(srvCodeOrAlias, btId, buildsForBt, teamcity, ghConn, prId);
            statusBuildNanos += System.nanoTime() - stepStart;

            if(Objects.equals(btId, defaultBuildType))
                contributionAgainstSuite.defaultBuildType = true;

            statuses.add(contributionAgainstSuite);
        }

        long totalMs = millisSince(startNanos);
        if (totalMs >= SLOW_VISA_OPERATION_WARN_MS) {
            logger.warn("Slow visa contributionStatus budget: server={}, prId={}, totalMs={}, statuses={}, " +
                    "serviceResolveMs={}, defaultBuildTypeMs={}, buildTypesMs={}, buildLookupMs={}, " +
                    "statusBuildMs={}, buildLookups={}",
                srvCodeOrAlias, prId, totalMs, statuses.size(), nanosToMillis(serviceResolveNanos),
                nanosToMillis(defaultBuildTypeNanos), nanosToMillis(buildTypesNanos), nanosToMillis(buildLookupNanos),
                nanosToMillis(statusBuildNanos), buildLookupCnt);
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

    @AutoProfiling
    public CurrentVisaStatus currentVisaStatus(String srvCode, ITcBotUserCreds prov, String buildTypeId,
                                               String tcBranch) {
        long startNanos = System.nanoTime();
        CurrentVisaStatus status = new CurrentVisaStatus();

        List<ShortSuiteUi> suitesStatuses
            = prChainsProcessor.getBlockersSuitesStatuses(buildTypeId, tcBranch, srvCode, prov, SyncMode.NONE, null);

        if (suitesStatuses == null) {
            logSlowVisaOperation(startNanos, "visaStatus", srvCode, buildTypeId, tcBranch, 0);

            return status;
        }

        status.blockers = suitesStatuses.stream().mapToInt(ShortSuiteUi::totalBlockers).sum();

        logSlowVisaOperation(startNanos, "visaStatus", srvCode, buildTypeId, tcBranch, suitesStatuses.size());

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
    @AutoProfiling
    public Visa notifyJira(
        String srvCodeOrAlias,
        ITcBotUserCreds prov,
        String buildTypeId,
        String branchForTc,
        String ticket,
        @Nullable String baseBranchForTc) {
        long startNanos = System.nanoTime();
        ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, prov);

        IJiraIgnited jira = jiraIgnProv.server(srvCodeOrAlias);

        List<Integer> builds = tcIgnited.getLastNBuildsFromHistory(buildTypeId, branchForTc, 1);

        if (builds.isEmpty()) {
            logSlowVisaOperation(startNanos, "notifyJiraNoBuilds", srvCodeOrAlias, buildTypeId, branchForTc, 0);

            return new Visa("JIRA wasn't commented - no finished builds to analyze.");
        }

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

            List<ShortSuiteNewTestsUi> newTestsStatuses = prChainsProcessor.getNewTestsSuitesStatuses(buildTypeId, build.branchName, srvCodeOrAlias, prov,
                SyncMode.RELOAD_QUEUED,
                baseBranch);

            if (suitesStatuses == null)
                return new Visa("JIRA wasn't commented - no finished builds to analyze." +
                    " Check builds availability for branch: " + build.branchName + "/" + baseBranch);

            blockers = suitesStatuses.stream().mapToInt(ShortSuiteUi::totalBlockers).sum();

            String comment = JiraCommentsGenerator.generateJiraComment(jira.config().getApiVersion(), compactor, suitesStatuses, newTestsStatuses, build.webUrl, buildTypeId, tcIgnited, blockers, build.branchName, baseBranch);

            res = objMapper.readValue(jira.postJiraComment(ticket, comment), JiraCommentResponse.class);
        }
        catch (Exception e) {
            String errMsg = "Exception happened during commenting JIRA ticket " +
                "[build=" + build.getId() + ", errMsg=" + e.getMessage() + ']';

            logger.error(errMsg);

            return new Visa("JIRA wasn't commented - " + errMsg);
        }

        logSlowVisaOperation(startNanos, "notifyJira", srvCodeOrAlias, buildTypeId, branchForTc, blockers);

        return new Visa(Visa.JIRA_COMMENTED, res, blockers);
    }

    /**
     * @param nanos Nanoseconds.
     */
    private static long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    /**
     * @param startNanos Start time.
     */
    private static long millisSince(long startNanos) {
        return nanosToMillis(System.nanoTime() - startNanos);
    }

    /**
     * Logs slow visa operation.
     */
    private void logSlowVisaOperation(long startNanos, String op, String srvCode, String buildTypeId, String tcBranch,
        int resultSize) {
        long totalMs = millisSince(startNanos);

        if (totalMs >= SLOW_VISA_OPERATION_WARN_MS) {
            logger.warn("Slow visa operation: op={}, server={}, buildType={}, branch={}, totalMs={}, resultSize={}",
                op, srvCode, buildTypeId, tcBranch, totalMs, resultSize);
        }
    }
}
