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
import javax.inject.Provider;
import org.apache.ignite.ci.github.GitHubBranch;
import org.apache.ignite.ci.github.GitHubIssueComment;
import org.apache.ignite.ci.github.GitHubUser;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.tcbot.github.GitHubCommentsGenerator;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.observer.BuildsInfo;
import org.apache.ignite.ci.tcbot.ITcBotBgAuth;
import org.apache.ignite.ci.tcbot.jira.JiraCommentsGenerator;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.apache.ignite.ci.web.model.JiraCommentResponse;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.web.model.VisaRequest;
import org.apache.ignite.ci.web.model.hist.VisasHistoryStorage;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.githubignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.githubservice.IGitHubConnection;
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
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
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
    @Inject private IUserStorage userStorage;

    /** */
    @Inject private GitHubUserResolver gitHubUserResolver;

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

    /** User-visible process monitor. */
    @Inject BotProcessMonitor processMonitor;

    /** Jackson serializer. */
    private final ObjectMapper objMapper = new ObjectMapper();

    /** */
    public void startObserver() {
        buildObserverProvider.get();
    }

    /** */
    public List<VisaStatus> getVisasStatus(ITcBotUserCreds prov) {
        return getVisasStatus(prov, 50);
    }

    /** */
    public List<VisaStatus> getVisasStatus(ITcBotUserCreds prov, int limit) {
        List<VisaStatus> visaStatuses = new ArrayList<>();
        Map<String, ITeamcityIgnited> tcBySrv = new HashMap<>();
        Map<String, IJiraIgnited> jiraBySrv = new HashMap<>();
        Map<String, IGitHubConnIgnited> ghBySrv = new HashMap<>();
        Map<String, String> buildTypeNameByKey = new HashMap<>();
        Map<String, TcHelperUser> userByName = new HashMap<>();

        for (VisaRequest visaRequest : visasHistStorage.getVisas(limit)) {
            VisaStatus visaStatus = new VisaStatus();

            String srvCodeOrAlias = visaRequest.getInfo().srvId;

            if(!prov.hasAccess(srvCodeOrAlias))
                continue;

            BuildsInfo info = visaRequest.getInfo();
            Visa visa = visaRequest.getResult();
            boolean isObserving = visaRequest.isObserving();

            visaStatus.date = THREAD_FORMATTER.get().format(info.date);
            visaStatus.branchName = info.branchForTc;
            visaStatus.userName = info.userName;
            TcHelperUser requester = requester(visaStatus.userName, userByName);
            fillRequesterLinks(visaStatus, requester, null);
            visaStatus.ticket = info.ticket;
            visaStatus.prNum = info.prNum;
            visaStatus.commentTargets = info.commentTargets;
            visaStatus.commentOnlyIfNoBlockers = info.commentOnlyIfNoBlockers;
            visaStatus.commentStatus = visa.status;
            visaStatus.buildIds = buildIds(info);
            visaStatus.rerun = !info.getBuilds().isEmpty();
            visaStatus.analysisSlice = analysisSlice(info);
            visaStatus.buildTypeId = info.buildTypeId;

            ITeamcityIgnited tcIgn = tcBySrv.computeIfAbsent(srvCodeOrAlias,
                srv -> tcIgnitedProv.server(srv, prov));

            visaStatus.buildTypeName = buildTypeNameByKey.computeIfAbsent(srvCodeOrAlias + '\n' + info.buildTypeId,
                key -> buildTypeName(tcIgn, srvCodeOrAlias, info.buildTypeId));
            visaStatus.baseBranchForTc = info.baseBranchForTc;
            visaStatus.blockers = visa.getBlockers();

            if (!Strings.isNullOrEmpty(visaStatus.ticket) || visa.getJiraCommentResponse() != null)
                fillTicketLinks(visaStatus, jiraBySrv.computeIfAbsent(srvCodeOrAlias, jiraIgnProv::server), visa);

            if (visaStatus.prNum != null && visaStatus.prNum > 0) {
                GitHubUser prAuthor = fillPullRequestLinks(visaStatus, ghBySrv.computeIfAbsent(srvCodeOrAlias,
                    gitHubConnIgnitedProvider::server));

                fillRequesterLinks(visaStatus, requester, prAuthor);
            }

            String buildsStatus = isObserving ? info.getStatus(tcIgn, strCompactor) : null;

            if (!isObserving)
                visaStatus.status = visa.isSuccess() ? FINISHED_STATUS : CANCELLED_STATUS;
            else if (FINISHED_STATUS.equals(buildsStatus)) {
                if (visa.isSuccess()) {
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
     * @param info Build info.
     */
    private static String buildIds(BuildsInfo info) {
        return info.getBuilds().stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * @param info Build info.
     */
    static String analysisSlice(BuildsInfo info) {
        String builds = buildIds(info);

        return "branch=" + info.branchForTc + "; base=" +
            (Strings.isNullOrEmpty(info.baseBranchForTc) ? "<default>" : info.baseBranchForTc) +
            "; suite=" + info.buildTypeId + "; " +
            (Strings.isNullOrEmpty(builds) ? "direct comment" : "observed run buildIds=" + builds);
    }

    /**
     * @param tcIgn TeamCity.
     * @param srvCodeOrAlias Server code.
     * @param buildTypeId Build type id.
     */
    private String buildTypeName(ITeamcityIgnited tcIgn, String srvCodeOrAlias, String buildTypeId) {
        BuildTypeRefCompacted bt = null;

        try {
            bt = tcIgn.getBuildTypeRef(buildTypeId);
        }
        catch (RuntimeException e) {
            logger.debug("Failed to load build type name for visa history [srv={}, buildTypeId={}]",
                srvCodeOrAlias, buildTypeId, e);
        }

        return bt != null ? bt.name(compactor) : buildTypeId;
    }

    /**
     * @param userName User name.
     * @param userByName Users cache.
     */
    @Nullable private TcHelperUser requester(@Nullable String userName, Map<String, TcHelperUser> userByName) {
        if (Strings.isNullOrEmpty(userName))
            return null;

        return userByName.computeIfAbsent(userName, userStorage::getUser);
    }

    /**
     * @param visaStatus Status DTO.
     * @param user Bot user.
     * @param gitHubUser Optional GitHub user candidate to match by public email.
     */
    private void fillRequesterLinks(VisaStatus visaStatus, @Nullable TcHelperUser user,
        @Nullable GitHubUser gitHubUser) {
        GitHubUserResolver.Resolution resolution = gitHubUserResolver.resolve(user,
            gitHubUser == null ? Collections.emptyList() : Collections.singletonList(gitHubUser));
        String githubId = resolution.configuredLogins.stream().findFirst().orElse(null);
        String avatarUrl = null;

        if (Strings.isNullOrEmpty(githubId))
            githubId = resolution.autoResolvedLogins.stream().findFirst().orElse(null);

        if (!Strings.isNullOrEmpty(githubId) && gitHubUser != null && githubId.equalsIgnoreCase(gitHubUser.login()))
            avatarUrl = gitHubUser.avatarUrl();

        if (Strings.isNullOrEmpty(githubId))
            return;

        visaStatus.userUrl = "https://github.com/" + githubId;
        visaStatus.userAvatarUrl = Strings.isNullOrEmpty(avatarUrl) ? "https://github.com/" + githubId + ".png?size=44" :
            avatarUrl;
    }

    /**
     * @param visaStatus Status DTO.
     * @param jiraIntegration JIRA.
     * @param visa Visa result.
     */
    private void fillTicketLinks(VisaStatus visaStatus, IJiraIgnited jiraIntegration, Visa visa) {
        if (Strings.isNullOrEmpty(visaStatus.ticket))
            return;

        try {
            visaStatus.ticketUrl = jiraIntegration.generateTicketUrl(visaStatus.ticket);

            if (visa.getJiraCommentResponse() != null)
                visaStatus.commentUrl = jiraIntegration.generateCommentUrl(
                    visaStatus.ticket, visa.getJiraCommentResponse().getId());
        }
        catch (RuntimeException e) {
            logger.debug("Failed to build JIRA links for visa history [ticket={}]", visaStatus.ticket, e);
        }
    }

    /**
     * @param visaStatus Status DTO.
     * @param gh GitHub.
     */
    @Nullable private GitHubUser fillPullRequestLinks(VisaStatus visaStatus, IGitHubConnIgnited gh) {
        if (visaStatus.prNum == null || visaStatus.prNum <= 0)
            return null;

        PullRequest pr = null;

        try {
            pr = gh.getPullRequest(visaStatus.prNum);
        }
        catch (RuntimeException e) {
            logger.debug("Failed to load PR from cache for visa history [pr={}]", visaStatus.prNum, e);
        }

        GitHubUser author = null;

        if (pr != null) {
            visaStatus.prUrl = pr.htmlUrl();

            author = pr.gitHubUser();

            if (author != null) {
                visaStatus.prAuthor = author.login();
                visaStatus.prAuthorAvatarUrl = author.avatarUrl();

                if (!Strings.isNullOrEmpty(author.login()))
                    visaStatus.prAuthorUrl = "https://github.com/" + author.login();
            }
        }

        if (Strings.isNullOrEmpty(visaStatus.prUrl))
            visaStatus.prUrl = pullRequestUrl(gh.config().gitApiUrl(), visaStatus.prNum);

        return author;
    }

    /**
     * @param gitApiUrl GitHub API URL.
     * @param prNum PR number.
     */
    @Nullable static String pullRequestUrl(@Nullable String gitApiUrl, int prNum) {
        if (Strings.isNullOrEmpty(gitApiUrl))
            return null;

        String apiUrl = gitApiUrl;

        if (apiUrl.endsWith("/"))
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);

        if (apiUrl.endsWith("/repos/apache/ignite")) {
            String host = apiUrl.substring(0, apiUrl.length() - "/repos/apache/ignite".length());

            if (host.endsWith("/api/v3"))
                host = host.substring(0, host.length() - "/api/v3".length());

            if ("https://api.github.com".equals(host))
                host = "https://github.com";

            return host + "/apache/ignite/pull/" + prNum;
        }

        return null;
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
        @Nullable Boolean cleanRebuild,
        @Nullable String commentTargets,
        @Nullable Boolean commentOnlyIfNoBlockers,
        @Nullable ITcBotUserCreds prov) {
        return triggerBuildsAndObserve(srvCodeOrAlias, branchForTc, parentSuiteId, suiteIdList, top, observe, ticketId,
            prNum, baseBranchForTc, cleanRebuild, commentTargets, commentOnlyIfNoBlockers, prov, null);
    }

    /**
     * @param processId User-visible process id.
     */
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
        @Nullable Boolean cleanRebuild,
        @Nullable String commentTargets,
        @Nullable Boolean commentOnlyIfNoBlockers,
        @Nullable ITcBotUserCreds prov,
        @Nullable Long processId) {
        long startNanos = System.nanoTime();
        long initNanos = 0;
        long prLookupNanos = 0;
        long triggerNanos = 0;
        long syncNanos = 0;
        long observeNanos = 0;
        int triggeredBuilds = 0;

        String jiraRes = "";

        long stepStart = System.nanoTime();
        processMonitor.status(processId, "Preparing the selected trigger action.");

        ITeamcityIgnited teamcity = tcIgnitedProv.server(srvCodeOrAlias, prov);

        IGitHubConnIgnited ghIgn = gitHubConnIgnitedProvider.server(srvCodeOrAlias);
        initNanos = System.nanoTime() - stepStart;

        Integer parsedPrNum = parsePrNum(prNum);

        if(parsedPrNum != null) {
            try {
                stepStart = System.nanoTime();
                PullRequest pr = ghIgn.getPullRequest(parsedPrNum);
                prLookupNanos += System.nanoTime() - stepStart;

                if(pr!=null) {
                    String shaShort = pr.lastCommitShaShort();

                    if(shaShort!=null)
                         jiraRes = "Actual commit: " + shaShort + ". ";
                }
            }
            catch (RuntimeException e) {
                logger.error("PR & TC state checking failed" , e);
            }
        }

        String[] suiteIds = Objects.requireNonNull(suiteIdList).split(",");
        Build[] builds = new Build[suiteIds.length];
        Set<Integer> buildidsToSync = new HashSet<>();
        boolean clean = cleanRebuild != null && cleanRebuild;

        processMonitor.status(processId, "Starting selected builds in TeamCity.");

        for (int i = 0; i < suiteIds.length; i++) {
            stepStart = System.nanoTime();
            T2<Build, Set<Integer>> objects = teamcity.triggerBuild(suiteIds[i], branchForTc, clean,
                top != null && top, new HashMap<>(), false, "");
            triggerNanos += System.nanoTime() - stepStart;
            triggeredBuilds++;
            buildidsToSync.addAll(objects.get2());
            builds[i] = objects.get1();
        }

        stepStart = System.nanoTime();
        processMonitor.status(processId, "Synchronizing triggered builds back into the bot cache.");
        teamcity.fastBuildsSync(buildidsToSync);
        syncNanos = System.nanoTime() - stepStart;

        if (observe != null && observe) {
            stepStart = System.nanoTime();
            processMonitor.status(processId, "Scheduling a result comment after builds finish.");
            jiraRes += observeComments(srvCodeOrAlias, branchForTc, ticketId, prov, parentSuiteId, baseBranchForTc,
                commentTargets, parsedPrNum, commentOnlyIfNoBlockers != null && commentOnlyIfNoBlockers, builds);
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
        return observeComments(srvId, branchForTc, ticketFullName, prov, parentSuiteId, baseBranchForTc,
            CommentTargets.DFLT, null, false, builds);
    }

    /**
     * @param srvId Server id.
     * @param branchForTc Branch for TeamCity.
     * @param ticketFullName JIRA ticket number.
     * @param prov Credentials.
     * @param parentSuiteId Parent suite id.
     * @param baseBranchForTc Reference branch in TC identification.
     * @param commentTargets Comment targets.
     * @param prNum Pull request number selected by user.
     * @param commentOnlyIfNoBlockers Comment only if analysis has no blockers.
     * @param builds Builds.
     * @return Message with result.
     */
    private String observeComments(
        String srvId,
        String branchForTc,
        @Nullable String ticketFullName,
        ITcBotUserCreds prov,
        String parentSuiteId,
        String baseBranchForTc,
        @Nullable String commentTargets,
        @Nullable Integer prNum,
        boolean commentOnlyIfNoBlockers,
        Build... builds
    ) {
        String targets;

        try {
            targets = CommentTargets.normalize(commentTargets);
        }
        catch (IllegalArgumentException e) {
            return "Comment targets are invalid: " + e.getMessage();
        }

        try {
            if (CommentTargets.jira(targets))
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

        buildObserverProvider.get().observe(srvId, ticketFullName, branchForTc, parentSuiteId, baseBranchForTc,
            user, targets, prNum, commentOnlyIfNoBlockers, builds);

        if (!tcBotBgAuth.isServerAuthorized())
            return "Ask server administrator to authorize the Bot to enable notifications.";

        return "Comment targets " + targets + " will be notified after the tests are completed.";
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
        return commentJiraEx(srvId, branchForTc, suiteId, ticketFullName, baseBranchForTc, prov,
            CommentTargets.DFLT);
    }

    /**
     * @param srvId Server id.
     * @param branchForTc Branch for tc.
     * @param suiteId Suite id.
     * @param ticketFullName Ticket full name with IGNITE- prefix.
     * @param baseBranchForTc Base branch in TC identification.
     * @param prov Prov.
     * @param commentTargets Comment targets.
     */
    @NotNull
    public SimpleResult commentJiraEx(
        @Nullable String srvId,
        @Nullable String branchForTc,
        @Nullable String suiteId,
        @Nullable String ticketFullName,
        @Nullable String baseBranchForTc,
        ITcBotUserCreds prov,
        @Nullable String commentTargets) {
        return commentJiraEx(srvId, branchForTc, suiteId, ticketFullName, baseBranchForTc, prov,
            commentTargets, null, false);
    }

    /**
     * @param srvId Server id.
     * @param branchForTc Branch for tc.
     * @param suiteId Suite id.
     * @param ticketFullName Ticket full name with IGNITE- prefix.
     * @param baseBranchForTc Base branch in TC identification.
     * @param prov Prov.
     * @param commentTargets Comment targets.
     * @param prNum Pull request number selected by user.
     * @param commentOnlyIfNoBlockers Comment only if analysis has no blockers.
     */
    @NotNull
    public SimpleResult commentJiraEx(
        @Nullable String srvId,
        @Nullable String branchForTc,
        @Nullable String suiteId,
        @Nullable String ticketFullName,
        @Nullable String baseBranchForTc,
        ITcBotUserCreds prov,
        @Nullable String commentTargets,
        @Nullable String prNum,
        boolean commentOnlyIfNoBlockers) {
        return commentJiraEx(srvId, branchForTc, suiteId, ticketFullName, baseBranchForTc, prov, commentTargets, prNum,
            commentOnlyIfNoBlockers, null);
    }

    /**
     * @param processId User-visible process id.
     */
    @NotNull
    public SimpleResult commentJiraEx(
        @Nullable String srvId,
        @Nullable String branchForTc,
        @Nullable String suiteId,
        @Nullable String ticketFullName,
        @Nullable String baseBranchForTc,
        ITcBotUserCreds prov,
        @Nullable String commentTargets,
        @Nullable String prNum,
        boolean commentOnlyIfNoBlockers,
        @Nullable Long processId) {
        processMonitor.status(processId, "Preparing the selected comment action.");

        String targets;

        try {
            targets = CommentTargets.normalize(commentTargets);
        }
        catch (IllegalArgumentException e) {
            return new SimpleResult("Analysis wasn't commented - " + e.getMessage());
        }

        Integer parsedPrNum = parsePrNum(prNum);

        if (!Strings.isNullOrEmpty(prNum) && parsedPrNum == null)
            return new SimpleResult("Analysis wasn't commented - invalid PR number: " + prNum);

        try {
            if (CommentTargets.jira(targets))
                ticketFullName = ticketMatcher.resolveTicketFromBranch(srvId, ticketFullName, branchForTc);
        }
        catch (BranchTicketMatcher.TicketNotFoundException e) {
            logger.info("", e);
            return new SimpleResult("JIRA wasn't commented: TicketNotFoundException: <br>" + e.getMessage());
        }

        String user = prov.getUser(srvId);
        if (user == null)
            user = prov.getPrincipalId();

        BuildsInfo buildsInfo = new BuildsInfo(srvId, ticketFullName, branchForTc, suiteId, baseBranchForTc, user,
            targets, parsedPrNum, commentOnlyIfNoBlockers);

        VisaRequest lastVisaReq = visasHistStorage.getLastVisaRequest(buildsInfo.getContributionKey());

        if (Objects.nonNull(lastVisaReq) && lastVisaReq.isObserving())
            return new SimpleResult("Jira wasn't commented." +
                " \"Re-run possible blockers & Comment JIRA\" was triggered for current branch." +
                " Wait for the end or cancel exsiting observing.");

        processMonitor.status(processId, "Collecting build analysis for the comment.");

        Visa visa = notifyComments(srvId, prov, suiteId, branchForTc, ticketFullName, baseBranchForTc, targets,
            parsedPrNum, commentOnlyIfNoBlockers, processId);

        visasHistStorage.put(new VisaRequest(buildsInfo).setResult(visa));

        processMonitor.status(processId, "Comment operation finished: " + visa.status);

        return new SimpleResult(visa.status);
    }

    /**
     * @param srvCodeOrAlias Server id.
     * @param credsProv Credentials
     */
    @AutoProfiling
    public List<ContributionToCheck> getContributionsToCheck(String srvCodeOrAlias,
        ITcBotUserCreds credsProv) {
        return getContributionsToCheck(srvCodeOrAlias, credsProv, null);
    }

    /**
     * @param processId User-visible process id.
     */
    @AutoProfiling
    public List<ContributionToCheck> getContributionsToCheck(String srvCodeOrAlias,
        ITcBotUserCreds credsProv,
        @Nullable Long processId) {
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
        processMonitor.status(processId, "Building the contribution list from cached bot data.");

        IJiraIgnited jiraIntegration = jiraIgnProv.server(srvCodeOrAlias);

        IGitHubConnIgnited gitHubConnIgnited = gitHubConnIgnitedProvider.server(srvCodeOrAlias);

        ITeamcityIgnited tcIgn = tcIgnitedProv.server(srvCodeOrAlias, credsProv);
        serviceResolveNanos = System.nanoTime() - stepStart;

        stepStart = System.nanoTime();
        processMonitor.status(processId, "Loading pull request details from GitHub.");
        List<PullRequest> prs = gitHubConnIgnited.getPullRequests();
        prsLoadNanos = System.nanoTime() - stepStart;

        stepStart = System.nanoTime();
        processMonitor.status(processId, "Loading JIRA tickets for contribution matching.");
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
        processMonitor.status(processId, "Matching pull requests, JIRA tickets, and TeamCity branches.");
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
                    c.prAuthorUrl = Strings.isNullOrEmpty(user.login()) ? "" : "https://github.com/" + user.login();
                    c.prAuthorAvatarUrl = user.avatarUrl();
                }
                else {
                    c.prAuthor = "";
                    c.prAuthorUrl = "";
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
        processMonitor.status(processId, "Loading repository branches.");
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
            contribution.prTimeUpdate = "";

            contribution.prAuthor = "";
            contribution.prAuthorUrl = "";
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

        processMonitor.status(processId, "The contribution list is ready.");

        return contribsList;
    }

    /**
     * Refreshes GitHub data immediately and returns contribution list built on top of the updated cache.
     *
     * @param srvCodeOrAlias Server id.
     * @param credsProv Credentials.
     */
    @AutoProfiling
    public List<ContributionToCheck> refreshContributionsToCheck(String srvCodeOrAlias,
        ITcBotUserCreds credsProv) {
        return refreshContributionsToCheck(srvCodeOrAlias, credsProv, null);
    }

    /**
     * @param processId User-visible process id.
     */
    @AutoProfiling
    public List<ContributionToCheck> refreshContributionsToCheck(String srvCodeOrAlias,
        ITcBotUserCreds credsProv,
        @Nullable Long processId) {
        IGitHubConnIgnited gitHubConnIgnited = gitHubConnIgnitedProvider.server(srvCodeOrAlias);

        processMonitor.status(processId, "Refreshing pull requests from GitHub.");
        gitHubConnIgnited.refreshPullRequests();

        if (gitHubConnIgnited.config().isPreferBranches()) {
            processMonitor.status(processId, "Refreshing repository branches.");
            gitHubConnIgnited.refreshBranches();
        }

        return getContributionsToCheck(srvCodeOrAlias, credsProv, processId);
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
        return notifyComments(srvCodeOrAlias, prov, buildTypeId, branchForTc, ticket, baseBranchForTc,
            CommentTargets.DFLT);
    }

    /**
     * Produce visa message based on passed parameters and publish it as requested comments.
     *
     * @param srvCodeOrAlias TC Server ID to take information about token from.
     * @param prov Credentials.
     * @param buildTypeId Build type ID, for which visa was ordered.
     * @param branchForTc Branch for TeamCity.
     * @param ticket JIRA ticket full name. E.g. IGNITE-5555
     * @param baseBranchForTc Base branch in TC identification.
     * @param commentTargets Comment targets.
     * @return {@link Visa} instance.
     */
    @AutoProfiling
    public Visa notifyComments(
        String srvCodeOrAlias,
        ITcBotUserCreds prov,
        String buildTypeId,
        String branchForTc,
        @Nullable String ticket,
        @Nullable String baseBranchForTc,
        @Nullable String commentTargets) {
        return notifyComments(srvCodeOrAlias, prov, buildTypeId, branchForTc, ticket, baseBranchForTc,
            commentTargets, null, false);
    }

    /**
     * Produce visa message based on passed parameters and publish it as requested comments.
     *
     * @param srvCodeOrAlias TC Server ID to take information about token from.
     * @param prov Credentials.
     * @param buildTypeId Build type ID, for which visa was ordered.
     * @param branchForTc Branch for TeamCity.
     * @param ticket JIRA ticket full name. E.g. IGNITE-5555
     * @param baseBranchForTc Base branch in TC identification.
     * @param commentTargets Comment targets.
     * @param prNum Pull request number selected by user.
     * @param commentOnlyIfNoBlockers Comment only if analysis has no blockers.
     * @return {@link Visa} instance.
     */
    @AutoProfiling
    public Visa notifyComments(
        String srvCodeOrAlias,
        ITcBotUserCreds prov,
        String buildTypeId,
        String branchForTc,
        @Nullable String ticket,
        @Nullable String baseBranchForTc,
        @Nullable String commentTargets,
        @Nullable Integer prNum,
        boolean commentOnlyIfNoBlockers) {
        return notifyComments(srvCodeOrAlias, prov, buildTypeId, branchForTc, ticket, baseBranchForTc, commentTargets,
            prNum, commentOnlyIfNoBlockers, null);
    }

    /**
     * @param processId User-visible process id.
     */
    @AutoProfiling
    public Visa notifyComments(
        String srvCodeOrAlias,
        ITcBotUserCreds prov,
        String buildTypeId,
        String branchForTc,
        @Nullable String ticket,
        @Nullable String baseBranchForTc,
        @Nullable String commentTargets,
        @Nullable Integer prNum,
        boolean commentOnlyIfNoBlockers,
        @Nullable Long processId) {
        return notifyComments(srvCodeOrAlias, prov, buildTypeId, branchForTc, ticket, baseBranchForTc, commentTargets,
            prNum, commentOnlyIfNoBlockers, processId, null);
    }

    /**
     * @param processId User-visible process id.
     * @param rerunBuildIds Build ids triggered by the bot for this observed rerun slice.
     */
    @AutoProfiling
    public Visa notifyComments(
        String srvCodeOrAlias,
        ITcBotUserCreds prov,
        String buildTypeId,
        String branchForTc,
        @Nullable String ticket,
        @Nullable String baseBranchForTc,
        @Nullable String commentTargets,
        @Nullable Integer prNum,
        boolean commentOnlyIfNoBlockers,
        @Nullable Long processId,
        @Nullable Collection<Integer> rerunBuildIds) {
        long startNanos = System.nanoTime();
        String targets;

        processMonitor.status(processId, "Collecting build analysis for the comment.");

        try {
            targets = CommentTargets.normalize(commentTargets);
        }
        catch (IllegalArgumentException e) {
            return Visa.failure("Analysis wasn't commented - " + e.getMessage());
        }

        boolean githubRequested = CommentTargets.github(targets);
        boolean jiraRequested = CommentTargets.jira(targets);

        ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, prov);

        IJiraIgnited jira = jiraRequested ? jiraIgnProv.server(srvCodeOrAlias) : null;

        processMonitor.status(processId, "Loading the latest finished build for the comment.");

        List<Integer> builds = tcIgnited.getLastNBuildsFromHistory(buildTypeId, branchForTc, 1);

        if (builds.isEmpty()) {
            logSlowVisaOperation(startNanos, "notifyJiraNoBuilds", srvCodeOrAlias, buildTypeId, branchForTc, 0);

            return Visa.failure("JIRA wasn't commented - no finished builds to analyze.");
        }

        Integer buildId = builds.get(0);

        FatBuildCompacted fatBuild = tcIgnited.getFatBuild(buildId);
        Build build = fatBuild.toBuild(compactor);

        build.webUrl = tcIgnited.host() + "viewLog.html?buildId=" + build.getId() + "&buildTypeId=" + build.buildTypeId;

        int blockers;

        JiraCommentResponse res = null;
        String jiraCommentStatus = null;
        GitHubCommentResult gitHubComment = GitHubCommentResult.commented(null);

        try {
            String baseBranch = Strings.isNullOrEmpty(baseBranchForTc)
                ? prChainsProcessor.dfltBaseTcBranch(srvCodeOrAlias) : baseBranchForTc;

            processMonitor.status(processId, "Resolving the base branch for build analysis: " + baseBranch + ".");

            processMonitor.status(processId, "Loading blocker analysis for the latest finished build.");
            List<ShortSuiteUi> suitesStatuses = prChainsProcessor.getBlockersSuitesStatuses(buildTypeId,
                build.branchName, srvCodeOrAlias, prov, SyncMode.RELOAD_QUEUED, baseBranch);

            processMonitor.status(processId, "Loading new-tests analysis for the latest finished build.");
            List<ShortSuiteNewTestsUi> newTestsStatuses = prChainsProcessor.getNewTestsSuitesStatuses(buildTypeId,
                build.branchName, srvCodeOrAlias, prov, SyncMode.RELOAD_QUEUED, baseBranch);

            if (suitesStatuses == null)
                return Visa.failure("JIRA wasn't commented - no finished builds to analyze." +
                    " Check builds availability for branch: " + build.branchName + "/" + baseBranch);

            blockers = suitesStatuses.stream().mapToInt(ShortSuiteUi::totalBlockers).sum();

            processMonitor.status(processId, "Build analysis is ready: " + blockers + " blockers, " +
                newTestsStatuses.size() + " new-test suites.");

            if (commentOnlyIfNoBlockers && blockers > 0)
                return Visa.skipped(blockers);

            String analysisSliceKey = analysisSliceKey(build.getId(), rerunBuildIds);

            if (githubRequested) {
                processMonitor.status(processId, "Publishing the analysis comment to GitHub.");

                gitHubComment = notifyGitHubPullRequest(srvCodeOrAlias, buildTypeId, branchForTc, prNum, build,
                    fatBuild, tcIgnited, suitesStatuses, newTestsStatuses, blockers, baseBranch, analysisSliceKey);
            }

            if (jiraRequested) {
                if (Strings.isNullOrEmpty(ticket))
                    return Visa.failure("JIRA wasn't commented - ticket is not specified.");

                processMonitor.status(processId, "Publishing the analysis comment to JIRA.");

                String marker = JiraCommentsGenerator.duplicateMarker(analysisSliceKey);

                if (hasExistingJiraComment(jira, ticket, marker)) {
                    logger.info("JIRA ticket already has TCBot analysis comment [srv={}, ticket={}, build={}, slice={}]",
                        srvCodeOrAlias, ticket, build.getId(), analysisSliceKey);

                    jiraCommentStatus = "JIRA ticket already has a valid TCBot comment for this build: " +
                        ticketTarget(jira, ticket, null);
                }
                else {
                    String comment = JiraCommentsGenerator.generateJiraComment(jira.config().getApiVersion(), compactor,
                        suitesStatuses, newTestsStatuses, build.webUrl, buildTypeId, tcIgnited, blockers,
                        build.branchName, baseBranch, analysisSliceKey);

                    res = objMapper.readValue(jira.postJiraComment(ticket, comment), JiraCommentResponse.class);
                    jiraCommentStatus = "JIRA ticket commented: " + ticketTarget(jira, ticket, res);
                }
            }

            if (githubRequested && !gitHubComment.commented)
                return commentResult(targets, gitHubComment.commented, gitHubComment.error, gitHubComment.status,
                    jiraCommentStatus, res, blockers);
        }
        catch (Exception e) {
            String errMsg = "Exception happened during commenting TCBot analysis " +
                "[build=" + build.getId() + ", errMsg=" + e.getMessage() + ']';

            logger.error(errMsg);

            return Visa.failure("Analysis wasn't commented - " + errMsg);
        }

        logSlowVisaOperation(startNanos, "notifyComments", srvCodeOrAlias, buildTypeId, branchForTc, blockers);

        return commentResult(targets, true, null, gitHubComment.status, jiraCommentStatus, res, blockers);
    }

    /**
     * @param targets Comment targets.
     * @param gitHubCommented Whether GitHub target was commented successfully.
     * @param res JIRA response.
     * @param blockers Blockers count.
     */
    static Visa commentResult(String targets, boolean gitHubCommented, JiraCommentResponse res, int blockers) {
        return commentResult(targets, gitHubCommented, null, res, blockers);
    }

    /**
     * @param targets Comment targets.
     * @param gitHubCommented Whether GitHub target was commented successfully.
     * @param gitHubError GitHub comment error.
     * @param res JIRA response.
     * @param blockers Blockers count.
     */
    static Visa commentResult(String targets, boolean gitHubCommented, @Nullable String gitHubError,
        JiraCommentResponse res, int blockers) {
        return commentResult(targets, gitHubCommented, gitHubError, null, null, res, blockers);
    }

    /**
     * @param targets Comment targets.
     * @param gitHubCommented Whether GitHub target was commented successfully.
     * @param gitHubError GitHub comment error.
     * @param gitHubStatus GitHub comment status.
     * @param jiraStatus JIRA comment status.
     * @param res JIRA response.
     * @param blockers Blockers count.
     */
    static Visa commentResult(String targets, boolean gitHubCommented, @Nullable String gitHubError,
        @Nullable String gitHubStatus, @Nullable String jiraStatus, JiraCommentResponse res, int blockers) {
        boolean githubRequested = CommentTargets.github(targets);
        boolean jiraRequested = CommentTargets.jira(targets);

        if (githubRequested && !gitHubCommented && jiraRequested)
            return Visa.success(partialCommentStatus(jiraStatus, gitHubError), res, blockers);

        if (githubRequested && !gitHubCommented)
            return Visa.failure("GitHub wasn't commented - " +
                (Strings.isNullOrEmpty(gitHubError) ? "unknown GitHub comment error." : gitHubError));

        if (jiraRequested && githubRequested)
            return Visa.success(joinCommentStatuses(jiraStatus, gitHubStatus), res, blockers);

        if (jiraRequested)
            return Visa.success(Strings.isNullOrEmpty(jiraStatus) ? Visa.JIRA_COMMENTED : jiraStatus, res, blockers);

        return Visa.success(Strings.isNullOrEmpty(gitHubStatus) ? Visa.COMMENTED : gitHubStatus, res, blockers);
    }

    /**
     * @param jiraStatus JIRA comment status.
     * @param gitHubError GitHub comment error.
     */
    private static String partialCommentStatus(@Nullable String jiraStatus, @Nullable String gitHubError) {
        String prefix = Strings.isNullOrEmpty(jiraStatus) ? Visa.PARTIALLY_COMMENTED : jiraStatus;

        return prefix + "; GitHub wasn't commented - " +
            (Strings.isNullOrEmpty(gitHubError) ? "unknown GitHub comment error." : gitHubError);
    }

    /**
     * @param jiraStatus JIRA comment status.
     * @param gitHubStatus GitHub comment status.
     */
    private static String joinCommentStatuses(@Nullable String jiraStatus, @Nullable String gitHubStatus) {
        List<String> statuses = new ArrayList<>();

        if (!Strings.isNullOrEmpty(jiraStatus))
            statuses.add(jiraStatus);

        if (!Strings.isNullOrEmpty(gitHubStatus))
            statuses.add(gitHubStatus);

        return statuses.isEmpty() ? Visa.COMMENTED : String.join("; ", statuses);
    }

    /**
     * @param jira JIRA.
     * @param ticket Ticket.
     * @param res JIRA comment response.
     */
    private static String ticketTarget(IJiraIgnited jira, String ticket, @Nullable JiraCommentResponse res) {
        try {
            if (res != null && res.getId() > 0)
                return ticket + " " + jira.generateCommentUrl(ticket, res.getId());

            return ticket + " " + jira.generateTicketUrl(ticket);
        }
        catch (RuntimeException e) {
            return ticket;
        }
    }

    /**
     * @param pr Pull request.
     */
    private static String pullRequestTarget(PullRequest pr) {
        String target = "PR #" + pr.getNumber();

        return Strings.isNullOrEmpty(pr.htmlUrl()) ? target : target + " " + pr.htmlUrl();
    }

    /**
     * @param chainBuildId Main chain build id.
     * @param rerunBuildIds Build ids triggered by the bot for this observed rerun slice.
     */
    static String analysisSliceKey(int chainBuildId, @Nullable Collection<Integer> rerunBuildIds) {
        List<Integer> sortedBuildIds = new ArrayList<>();

        if (rerunBuildIds != null) {
            for (Integer buildId : rerunBuildIds) {
                if (buildId != null && buildId != chainBuildId)
                    sortedBuildIds.add(buildId);
            }
        }

        Collections.sort(sortedBuildIds);

        return "chainBuildId=" + chainBuildId + " rerunBuildIds=" +
            (sortedBuildIds.isEmpty() ? "none" : sortedBuildIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
    }

    /**
     * @param srvCodeOrAlias Server code.
     * @param buildTypeId Build type id.
     * @param requestedBranchForTc Branch requested by caller.
     * @param prNum Pull request number selected by user.
     * @param build Build.
     * @param fatBuild Compacted build.
     * @param tcIgnited TeamCity.
     * @param suitesStatuses Suites statuses.
     * @param newTestsStatuses New tests statuses.
     * @param blockers Blockers count.
     * @param baseBranch Base branch.
     * @param analysisSliceKey Analysis slice key.
     */
    private GitHubCommentResult notifyGitHubPullRequest(
        String srvCodeOrAlias,
        String buildTypeId,
        String requestedBranchForTc,
        @Nullable Integer prNum,
        Build build,
        FatBuildCompacted fatBuild,
        ITeamcityIgnited tcIgnited,
        List<ShortSuiteUi> suitesStatuses,
        List<ShortSuiteNewTestsUi> newTestsStatuses,
        int blockers,
        String baseBranch,
        String analysisSliceKey) {
        try {
            IGitHubConnIgnited gh = gitHubConnIgnitedProvider.server(srvCodeOrAlias);
            PullRequest pr = prNum == null ? findPullRequestForBuild(gh, requestedBranchForTc, build.branchName)
                : findPullRequestByNumber(gh, prNum);

            if (pr == null) {
                String err = "related PR was not found [srv=" + srvCodeOrAlias +
                    ", prNum=" + prNum +
                    ", requestedBranch=" + requestedBranchForTc +
                    ", buildBranch=" + build.branchName + ']';

                logger.info("GitHub PR was not found for TCBot analysis comment [{}]", err);

                return GitHubCommentResult.failed(err);
            }

            String marker = GitHubCommentsGenerator.duplicateMarker(analysisSliceKey);
            String prTarget = pullRequestTarget(pr);

            if (hasExistingGitHubComment(gh, pr.getNumber(), marker)) {
                logger.info("GitHub PR already has TCBot analysis comment [srv={}, pr={}, build={}, slice={}]",
                    srvCodeOrAlias, pr.getNumber(), build.getId(), analysisSliceKey);

                return GitHubCommentResult.alreadyCommented(prTarget);
            }

            String testedCommit = tcIgnited.getLatestCommitVersion(fatBuild);
            String testedCommitLink = testedCommitLink(gh, testedCommit, pr.getNumber());

            String comment = GitHubCommentsGenerator.generateGitHubComment(compactor, suitesStatuses,
                newTestsStatuses, build.webUrl, buildTypeId, tcIgnited, blockers, build.branchName, baseBranch,
                testedCommitLink, analysisSliceKey);

            String notifyErr = gh.postIssueCommentError(pr.getNumber(), comment);

            if (notifyErr != null) {
                String err = "GitHub issue comment POST failed [srv=" + srvCodeOrAlias +
                    ", pr=" + pr.getNumber() +
                    ", build=" + build.getId() +
                    ", err=" + notifyErr + ']';

                logger.warn("GitHub PR was not commented [{}]", err);

                return GitHubCommentResult.failed(err);
            }

            return GitHubCommentResult.commented(prTarget);
        }
        catch (Exception e) {
            String err = "exception happened during commenting GitHub PR [srv=" + srvCodeOrAlias +
                ", requestedBranch=" + requestedBranchForTc + ", build=" + build.getId() +
                ", errType=" + e.getClass().getSimpleName() +
                ", errMsg=" + e.getMessage() + ']';

            logger.error(err, e);

            return GitHubCommentResult.failed(err);
        }
    }

    /** GitHub comment attempt result. */
    private static class GitHubCommentResult {
        /** */
        private final boolean commented;

        /** */
        @Nullable private final String error;

        /** */
        @Nullable private final String status;

        /**
         * @param commented Commented flag.
         * @param error Error.
         * @param status User-visible status.
         */
        private GitHubCommentResult(boolean commented, @Nullable String error, @Nullable String status) {
            this.commented = commented;
            this.error = error;
            this.status = status;
        }

        /**
         * @param target Comment target.
         */
        private static GitHubCommentResult commented(@Nullable String target) {
            return new GitHubCommentResult(true, null,
                Strings.isNullOrEmpty(target) ? null : "GitHub PR commented: " + target);
        }

        /**
         * @param target Comment target.
         */
        private static GitHubCommentResult alreadyCommented(String target) {
            return new GitHubCommentResult(true, null,
                "GitHub PR already has a valid TCBot comment for this build: " + target);
        }

        /**
         * @param error Error.
         */
        private static GitHubCommentResult failed(String error) {
            return new GitHubCommentResult(false, error, null);
        }
    }

    /**
     * @param gh GitHub.
     * @param prNum Pull request number.
     */
    @Nullable PullRequest findPullRequestByNumber(IGitHubConnIgnited gh, int prNum) {
        PullRequest pr = findPullRequestByNumberInCache(gh, prNum);

        if (pr != null)
            return pr;

        logger.info("GitHub PR was not found in cache, refreshing pull requests [pr={}]", prNum);

        gh.refreshPullRequests();

        return findPullRequestByNumberInCache(gh, prNum);
    }

    /**
     * @param gh GitHub.
     * @param prNum Pull request number.
     */
    @Nullable private PullRequest findPullRequestByNumberInCache(IGitHubConnIgnited gh, int prNum) {
        PullRequest pr = gh.getPullRequest(prNum);

        if (pr != null)
            return pr;

        List<PullRequest> prs = gh.getPullRequests();

        if (prs == null)
            return null;

        for (PullRequest next : prs) {
            if (next.getNumber() == prNum)
                return next;
        }

        return null;
    }

    /**
     * @param gh GitHub.
     * @param branches Branch names to check.
     */
    @Nullable private PullRequest findPullRequestForBuild(IGitHubConnIgnited gh, String... branches) {
        for (String branch : branches) {
            Integer prId = IGitHubConnection.convertBranchToPrId(branch);

            if (prId != null) {
                PullRequest pr = gh.getPullRequest(prId);

                if (pr != null)
                    return pr;

                List<PullRequest> prs = gh.getPullRequests();

                if (prs != null) {
                    for (PullRequest next : prs) {
                        if (next.getNumber() == prId)
                            return next;
                    }
                }
            }
        }

        List<PullRequest> prs = gh.getPullRequests();

        if (prs == null)
            return null;

        for (String branch : branches) {
            if (Strings.isNullOrEmpty(branch))
                continue;

            for (PullRequest pr : prs) {
                GitHubBranch head = pr.head();

                if (head != null && branch.equals(head.ref()))
                    return pr;
            }
        }

        return null;
    }

    /**
     * @param gh GitHub.
     * @param prNum PR number.
     * @param marker Marker.
     */
    boolean hasExistingGitHubComment(IGitHubConnIgnited gh, int prNum, String marker) {
        List<GitHubIssueComment> comments = gh.getIssueComments(prNum);

        if (comments == null)
            return false;

        for (GitHubIssueComment comment : comments) {
            String body = comment.body();

            if (body != null && body.contains(marker))
                return true;
        }

        return false;
    }

    /**
     * @param jira JIRA.
     * @param ticket Ticket.
     * @param marker Marker.
     */
    boolean hasExistingJiraComment(IJiraIgnited jira, String ticket, String marker) {
        try {
            String comments = jira.getJiraComments(ticket);

            return comments != null && comments.contains(marker);
        }
        catch (Exception e) {
            logger.warn("Unable to check existing JIRA comments [ticket={}]. Comment will be posted.", ticket, e);

            return false;
        }
    }

    /**
     * @param prNum Pull request number.
     */
    @Nullable private Integer parsePrNum(@Nullable String prNum) {
        if (Strings.isNullOrEmpty(prNum))
            return null;

        try {
            return Integer.parseInt(prNum);
        }
        catch (NumberFormatException e) {
            logger.warn("Invalid PR number: {}", prNum);

            return null;
        }
    }

    /**
     * @param gh GitHub.
     * @param commit Commit hash.
     * @param prNum Pull request number.
     */
    private String testedCommitLink(IGitHubConnIgnited gh, @Nullable String commit, int prNum) {
        if (Strings.isNullOrEmpty(commit))
            return null;

        String shortCommit = commit.length() > PullRequest.INCLUDE_SHORT_VER
            ? commit.substring(0, PullRequest.INCLUDE_SHORT_VER) : commit;
        String url = commitHtmlUrl(gh.config().gitApiUrl(), commit, prNum);

        return url == null ? "`" + shortCommit + "`" : "[" + shortCommit + "](" + url + ")";
    }

    /**
     * @param gitApiUrl GitHub API URL.
     * @param commit Commit hash.
     * @param prNum Pull request number.
     */
    @Nullable String commitHtmlUrl(@Nullable String gitApiUrl, String commit, int prNum) {
        if (Strings.isNullOrEmpty(gitApiUrl))
            return null;

        String apiUrl = gitApiUrl;

        while (apiUrl.endsWith("/"))
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);

        String marker = "/repos/";
        int idx = apiUrl.indexOf(marker);

        if (idx < 0)
            return null;

        String host = apiUrl.substring(0, idx);
        String repoPath = apiUrl.substring(idx + marker.length());
        String[] path = repoPath.split("/");

        if (path.length < 2)
            return null;

        if ("https://api.github.com".equals(host))
            host = "https://github.com";
        else if (host.endsWith("/api/v3"))
            host = host.substring(0, host.length() - "/api/v3".length());

        return host + "/" + path[0] + "/" + path[1] + "/pull/" + prNum + "/commits/" + commit;
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
