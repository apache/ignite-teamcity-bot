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

package org.apache.ignite.ci.tcbot.issue;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcbot.engine.issue.IssueType;
import org.apache.ignite.ci.jobs.CheckQueueJob;
import org.apache.ignite.tcbot.engine.tracked.DisplayMode;
import org.apache.ignite.tcbot.notify.ISlackSender;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.tcbot.common.TcBotConst;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.engine.conf.INotificationChannel;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.NotificationsConfig;
import org.apache.ignite.tcbot.engine.issue.EventTemplate;
import org.apache.ignite.tcbot.engine.issue.EventTemplates;
import org.apache.ignite.tcbot.engine.tracked.TrackedBranchChainsProcessor;
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
import org.apache.ignite.tcbot.engine.ui.DsSuiteUi;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.engine.ui.DsTestFailureUi;
import org.apache.ignite.tcbot.notify.IEmailSender;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.history.InvocationData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.tcignited.buildref.BranchEquivalence.normalizeBranch;
import static org.apache.ignite.tcignited.history.RunStatus.RES_FAILURE;
import static org.apache.ignite.tcignited.history.RunStatus.RES_OK;

/**
 *
 */
public class IssueDetector {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(IssueDetector.class);

    /** Slack prefix, using this for email address will switch notifier to slack (if configured). */
    private static final String SLACK = "slack:";

    @Inject private IIssuesStorage issuesStorage;
    @Inject private IUserStorage userStorage;

    private final AtomicBoolean init = new AtomicBoolean();
    private ITcBotUserCreds backgroundOpsCreds;
    @Deprecated //todo use scheduler
    private ScheduledExecutorService executorService;

    @Inject private Provider<CheckQueueJob> checkQueueJobProv;

    /** Tracked Branch Processor. */
    @Inject private TrackedBranchChainsProcessor tbProc;

    /** Server provider. */
    @Inject private ITeamcityIgnitedProvider tcProv;

    /** String Compactor. */
    @Inject private IStringCompactor compactor;

    /** Config. */
    @Inject private ITcBotConfig cfg;

    /** Email sender. */
    @Inject private IEmailSender emailSender;

    /** Email sender. */
    @Inject private ISlackSender slackSender;

    /** Send notification guard. */
    private final AtomicBoolean sndNotificationGuard = new AtomicBoolean();

    private String registerIssuesAndNotifyLater(DsSummaryUi res,
                                                ITcBotUserCreds creds) {

        if (creds == null)
            return null;

        String newIssues = registerNewIssues(res, creds);

        if (sndNotificationGuard.compareAndSet(false, true))
            executorService.schedule(this::sendNewNotifications, 90, TimeUnit.SECONDS);

        return newIssues;
    }

    private void sendNewNotifications() {
        try {
            sendNewNotificationsEx();
        }
        catch (Exception e) {
            System.err.println("Fail to sent notifications");
            e.printStackTrace();

            logger.error("Failed to send notification", e.getMessage());
        }
        finally {
            sndNotificationGuard.set(false);
        }
    }

    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @AutoProfiling
    @MonitoredTask(name = "Send Notifications")
    protected String sendNewNotificationsEx() {
        List<INotificationChannel> channels = new ArrayList<>();

        userStorage.allUsers()
            .filter(TcHelperUser::hasEmail)
            .filter(TcHelperUser::hasSubscriptions)
            .forEach(channels::add);

        channels.addAll(cfg.notifications().channels());

        Map<String, Notification> toBeSent = new HashMap<>();

        AtomicInteger issuesChecked = new AtomicInteger();
        AtomicInteger filteredFresh = new AtomicInteger();
        AtomicInteger filteredBuildTs = new AtomicInteger();
        AtomicInteger filteredNotDisabled = new AtomicInteger();
        AtomicInteger hasSubscriptions = new AtomicInteger();
        AtomicInteger neverSentBefore = new AtomicInteger();

        issuesStorage.allIssues()
            .peek(issue -> issuesChecked.incrementAndGet())
            .filter(issue -> {
                long detected = issue.detectedTs == null ? 0 : issue.detectedTs;
                long issueAgeMs = System.currentTimeMillis() - detected;

                //here boundary can be not an absolute, but some ts when particular notification channel config was changed
                // alternatively boundary may depend to issue notification histroy

                boolean neverNotified = issue.addressNotified == null || issue.addressNotified.isEmpty();
                // if issue had a prior notification, limit age by 2 hours to avoid new addresses spamming.
                // otherwise check last day issues if it is notifiable
                long bound = TimeUnit.HOURS.toMillis(neverNotified
                    ? TcBotConst.NOTIFY_MAX_AGE_SINCE_DETECT_HOURS
                    : TcBotConst.NOTIFY_MAX_AGE_SINCE_DETECT_FOR_NOTIFIED_ISSUE_HOURS );

                return issueAgeMs <= bound;
            })
            .peek(issue -> filteredFresh.incrementAndGet())
            .filter(issue -> {
                if (issue.buildStartTs == null)
                    return true; // exception due to bug in issue detection; field was not filled

                long buildStartTs = issue.buildStartTs == null ? 0 : issue.buildStartTs;
                long buildAgeMs = System.currentTimeMillis() - buildStartTs;
                long maxBuildAgeToNotify = TimeUnit.DAYS.toMillis(TcBotConst.NOTIFY_MAX_AGE_SINCE_START_DAYS) / 2;

                return buildAgeMs <= maxBuildAgeToNotify;
            })
            .peek(issue -> filteredBuildTs.incrementAndGet())
            .filter(issue -> {
                return cfg.getTrackedBranches()
                    .get(issue.trackedBranchName)
                    .filter(tb -> !tb.disableIssueTypes().contains(issue.type()))
                    .isPresent();
            })
            .peek(issue -> filteredNotDisabled.incrementAndGet())
            .forEach(issue -> {
                List<String> addrs = new ArrayList<>();

                final String srvCode = issue.issueKey().server;

                AtomicInteger ctnSrvAllowed = new AtomicInteger();
                AtomicInteger cntSubscibed = new AtomicInteger();
                AtomicInteger cntTagsFilterPassed = new AtomicInteger();

                channels.stream()
                    .filter(ch -> ch.isServerAllowed(srvCode))
                    .peek(ch -> ctnSrvAllowed.incrementAndGet())
                    .filter(ch -> ch.isSubscribedToBranch(issue.trackedBranchName))
                    .peek(ch -> cntSubscibed.incrementAndGet())
                    .filter(ch -> {
                        if (ch.hasTagFilter())
                            return issue.buildTags().stream().anyMatch(ch::isSubscribedToTag);

                        return true;
                    })
                    .peek(ch -> cntTagsFilterPassed.incrementAndGet())
                    .forEach(channel -> {
                        String email = channel.email();
                        String slack = channel.slack();
                        logger.info("User/channel " + channel + " is candidate for notification " + email
                            + " , " + slack + " for " + issue);

                        if (!Strings.isNullOrEmpty(email))
                            addrs.add(email);

                        if (!Strings.isNullOrEmpty(slack))
                            addrs.add(SLACK + slack);
                    });

                if(!addrs.isEmpty())
                    hasSubscriptions.incrementAndGet();

                boolean nonNotifedChFound = false;

                for (String nextAddr : addrs) {
                    if (issuesStorage.getIsNewAndSetNotified(issue.issueKey, nextAddr, null)) {
                        nonNotifedChFound = true;

                        toBeSent.computeIfAbsent(nextAddr, addr -> {
                            Notification notification = new Notification();
                            notification.ts = System.currentTimeMillis();
                            notification.addr = addr;
                            return notification;
                        }).addIssue(issue);
                    }
                }

                if (!nonNotifedChFound) {
                    issuesStorage.saveIssueSubscribersStat(issue.issueKey,
                        ctnSrvAllowed.get(),
                        cntSubscibed.get(),
                        cntTagsFilterPassed.get());
                }
                else
                    neverSentBefore.incrementAndGet();
            });

        String stat = issuesChecked.get() + " issues checked, " +
            filteredFresh.get() + " detected recenty, " +
            filteredBuildTs.get() + " for fresh builds, " +
            filteredNotDisabled.get() + " not disabled, " +
            hasSubscriptions.get() + " has subscriber, " +
            neverSentBefore.get() + " non sent before";

        if (toBeSent.isEmpty())
            return "Noting to notify, " + stat;

        NotificationsConfig notifications = cfg.notifications();

        Map<String, AtomicInteger> sndStat = new HashMap<>();

        for (Notification next : toBeSent.values()) {
            String addr = next.addr;

            try {
                if (addr.startsWith(SLACK)) {
                    String slackUser = addr.substring(SLACK.length());

                    List<String> messages = next.toSlackMarkup();

                    for (String msg : messages) {
                        slackSender.sendMessage(slackUser, msg, notifications);

                        sndStat.computeIfAbsent(addr, k -> new AtomicInteger()).incrementAndGet();
                    }
                }
                else {
                    String builds = next.buildIdToIssue.keySet().toString();
                    String subj = "[MTCGA]: " + next.countIssues() + " new failures in builds " + builds + " needs to be handled";

                    emailSender.sendEmail(addr, subj, next.toHtml(), next.toPlainText(), notifications.email());

                    sndStat.computeIfAbsent(addr, k -> new AtomicInteger()).incrementAndGet();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                logger.warn("Unable to notify address [" + addr + "] about build failures", e);

                next.allIssues().forEach(issue -> {
                        IssueKey key = issue.issueKey();
                        // rollback successfull notification
                        issuesStorage.getIsNewAndSetNotified(key, addr, e);
                    });

                stat += " ;" + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        return "Send " + sndStat.toString() + "; Statistics: " + stat;
    }

    /**
     * @param res summary of failures in test
     * @param creds Credentials provider.
     * @return Displayable string with operation status.
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @AutoProfiling
    @MonitoredTask(name = "Register new issues")
    protected String registerNewIssues(DsSummaryUi res, ITcBotUserCreds creds) {
        int newIssues = 0;

        for (DsChainUi next : res.servers) {
            String srvCode = next.serverCode;

            if (!tcProv.hasAccess(srvCode, creds))
                continue;

            ITeamcityIgnited tcIgnited = tcProv.server(srvCode, creds);

            for (DsSuiteUi suiteCurrentStatus : next.suites) {
                String normalizeBranch = normalizeBranch(suiteCurrentStatus.branchName());

                final String trackedBranch = res.getTrackedBranch();

                String suiteId = suiteCurrentStatus.suiteId;
                for (DsTestFailureUi testFailure : suiteCurrentStatus.testFailures) {
                    if (registerTestFailIssues(tcIgnited, srvCode, suiteId, normalizeBranch, testFailure, trackedBranch,
                        suiteCurrentStatus.tags))
                        newIssues++;
                }

                if (registerSuiteFailIssues(tcIgnited, srvCode, suiteId, normalizeBranch, suiteCurrentStatus, trackedBranch))
                    newIssues++;
            }
        }

        return "New issues found " + newIssues;
    }

    /**
     * Checks and persists suites failure.
     *
     * @param tcIgnited Tc ignited.
     * @param srvCode Servers (services) code.
     * @param normalizeBranch Normalize branch.
     * @param suiteFailure Suite failure.
     * @param trackedBranch Tracked branch.
     */
    private boolean registerSuiteFailIssues(ITeamcityIgnited tcIgnited,
        String srvCode,
        String suiteId,
        String normalizeBranch,
        DsSuiteUi suiteFailure,
        String trackedBranch) {

        Integer btId = compactor.getStringIdIfPresent(suiteId);
        Integer brNormId = compactor.getStringIdIfPresent(normalizeBranch);

        IRunHistory runStat = tcIgnited.getSuiteRunHist(btId, brNormId).self();

        if (runStat == null)
            return false;

        boolean issueFound = false;

        Integer firstFailedBuildId = runStat.detectTemplate(EventTemplates.newCriticalFailure);

        if (firstFailedBuildId != null && Boolean.TRUE.equals(suiteFailure.hasCriticalProblem)) {
            IssueKey issueKey = new IssueKey(srvCode, firstFailedBuildId, suiteId);

            if (!issuesStorage.containsIssueKey(issueKey)) {
                issuesStorage.saveIssue(createIssueForSuite(tcIgnited, suiteFailure, trackedBranch,
                    issueKey, IssueType.newCriticalFailure));

                issueFound = true;
            }
        }

        if (cfg.getTeamcityConfig(srvCode).trustedSuites().contains(suiteId)
            || tcIgnited.config().trustedSuites().contains(suiteId)) {
            Integer firstTrustedSuiteFailue = runStat.detectTemplate(EventTemplates.newFailure);

            if (firstTrustedSuiteFailue != null) {
                IssueKey issueKey = new IssueKey(srvCode, firstTrustedSuiteFailue, suiteId);

                if (!issuesStorage.containsIssueKey(issueKey)) {
                    issuesStorage.saveIssue(createIssueForSuite(tcIgnited, suiteFailure, trackedBranch,
                        issueKey, IssueType.newTrustedSuiteFailure));

                    issueFound = true;
                }
            }
        }

        return issueFound;
    }

    @NotNull
    private Issue createIssueForSuite(ITeamcityIgnited tcIgnited, DsSuiteUi suiteFailure, String trackedBranch,
                                      IssueKey issueKey, IssueType issType) {
        Issue issue = new Issue(issueKey, issType,  tcIgnited.getBuildStartTs(issueKey.buildId));
        issue.trackedBranchName = trackedBranch;
        issue.displayName = suiteFailure.name;
        issue.webUrl = suiteFailure.webToHist;

        issue.buildTags.addAll(suiteFailure.tags);

        locateChanges(tcIgnited, issueKey.buildId, issue);

        logger.info("Register new issue for suite fail: " + issue);
        return issue;
    }

    private void locateChanges(ITeamcityIgnited teamcity, int buildId, Issue issue) {
        final FatBuildCompacted fatBuild = teamcity.getFatBuild(buildId);
        final int[] changes = fatBuild.changes();
        final Collection<ChangeCompacted> allChanges = teamcity.getAllChanges(changes);

        for (ChangeCompacted next : allChanges) {
            issue.addChange(next.vcsUsername(compactor),
                teamcity.host() + "viewModification.html?modId=" + next.id());
        }
    }

    private boolean registerTestFailIssues(ITeamcityIgnited tcIgnited,
        String srvCode,
        String suiteId,
        String normalizeBranch,
        DsTestFailureUi testFailure,
        String trackedBranch,
        @Nonnull Set<String> suiteTags) {
        String name = testFailure.name;
        int tname = compactor.getStringId(name);
        Integer btId = compactor.getStringIdIfPresent(suiteId);
        Integer brNormId = compactor.getStringIdIfPresent(normalizeBranch);

        IRunHistory runStat = tcIgnited.getTestRunHist(tname, btId, brNormId);

        if (runStat == null)
            return false;

        IssueType type = null;

        Integer firstFailedBuildId = runStat.detectTemplate(EventTemplates.newContributedTestFailure);

        if (firstFailedBuildId != null)
            type = IssueType.newContributedTestFailure;

        if (firstFailedBuildId == null) {
            firstFailedBuildId = runStat.detectTemplate(EventTemplates.newFailure);

            if (firstFailedBuildId != null) {
                type = IssueType.newFailure;
                final String flakyComments = runStat.getFlakyComments();

                if (!Strings.isNullOrEmpty(flakyComments) &&
                    runStat.detectTemplate(EventTemplates.newFailureForFlakyTest) != null)
                        type = IssueType.newFailureForFlakyTest;
            }
        }

        double flakyRate = 0;

        if (firstFailedBuildId == null || type == null) {
            List<Invocation> invocations = runStat.getInvocations().
                filter(invocation -> invocation != null && invocation.status() != InvocationData.MISSING)
                .collect(Collectors.toList());

            int confidenceOkTestsRow = Math.max(1,
                (int) Math.ceil(Math.log(1 - cfg.confidence()) / Math.log(1 - cfg.flakyRate() / 100.0)));

            if (invocations.size() >= confidenceOkTestsRow * 2) {
                List<Invocation> lastInvocations =
                    invocations.subList(invocations.size() - confidenceOkTestsRow * 2, invocations.size());

                int stableTestRuns = 0;

                for (int i = 0; i < confidenceOkTestsRow; i++) {
                    if (lastInvocations.get(i).status() == RES_OK.getCode())
                        stableTestRuns++;
                    else
                        break;
                }

                if (stableTestRuns == confidenceOkTestsRow) {
                    long failedTestRuns = 0;

                    for (int i = confidenceOkTestsRow; i < confidenceOkTestsRow * 2; i++) {
                        if (lastInvocations.get(i).status() == RES_FAILURE.getCode())
                            failedTestRuns++;
                    }

                    flakyRate = (double) failedTestRuns / confidenceOkTestsRow * 100;

                    if (flakyRate > cfg.flakyRate()) {
                        type = IssueType.newTestWithHighFlakyRate;

                        firstFailedBuildId = lastInvocations.stream()
                            .filter(invocation -> invocation.status() == RES_FAILURE.getCode())
                            .findFirst()
                            .orElseGet(() -> lastInvocations.get(confidenceOkTestsRow))
                            .buildId();
                    }
                }
            }
        }

        if (firstFailedBuildId == null && cfg.alwaysFailedTestDetection()) {
            firstFailedBuildId = runStat.detectTemplate(EventTemplates.alwaysFailure);

            if (firstFailedBuildId != null)
                type = IssueType.newAlwaysFailure;
        }

        if (firstFailedBuildId == null)
            return false;

        if (type == null)
            return false;

        int buildId = firstFailedBuildId;

        IssueKey issueKey = new IssueKey(srvCode, buildId, name);

        if (issuesStorage.containsIssueKey(issueKey))
            return false; //duplicate

        Issue issue = new Issue(issueKey, type, tcIgnited.getBuildStartTs(issueKey.buildId));
        issue.trackedBranchName = trackedBranch;
        issue.displayName = testFailure.testName;
        issue.webUrl = testFailure.webUrl;
        issue.flakyRate = flakyRate;

        issue.buildTags.addAll(suiteTags);

        locateChanges(tcIgnited, buildId, issue);

        logger.info("Register new issue for test fail: " + issue);

        issuesStorage.saveIssue(issue);

        return true;
    }

    /**
     *
     */
    public boolean isAuthorized() {
        return backgroundOpsCreds != null;
    }

    public void startBackgroundCheck(ITcBotUserCreds prov) {
        try {
            if (init.compareAndSet(false, true)) {
                this.backgroundOpsCreds = prov;

                executorService = Executors.newScheduledThreadPool(3);

                executorService.scheduleAtFixedRate(this::checkFailures, 0, 15, TimeUnit.MINUTES);

                final CheckQueueJob checkQueueJob = checkQueueJobProv.get();

                checkQueueJob.init(backgroundOpsCreds);

                executorService.scheduleAtFixedRate(checkQueueJob, 0, 10, TimeUnit.MINUTES);
            }
        }
        catch (Exception e) {
            e.printStackTrace();

            init.set(false);

            throw e;
        }
    }

    /**
     *
     */
    private void checkFailures() {
        cfg.getTrackedBranches().branchesStream().forEach(tb -> {
            try {
                checkFailuresEx(tb.name());
            } catch (Exception e) {
                e.printStackTrace();

                logger.error("Failure periodic check failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * @param brachName
     */
    @AutoProfiling
    @MonitoredTask(name = "Detect Issues in tracked branch", nameExtArgIndex = 0)
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    protected String checkFailuresEx(String brachName) {
        int buildsToQry = EventTemplates.templates.stream().mapToInt(EventTemplate::cntEvents).max().getAsInt();

        ITcBotUserCreds creds = Preconditions.checkNotNull(backgroundOpsCreds, "Server should be authorized");

        tbProc.getTrackedBranchTestFailures(
            brachName,
            false,
            buildsToQry,
            creds,
            SyncMode.RELOAD_QUEUED,
            false,
            null,
            null,
            DisplayMode.None,
            null,
            -1, false, false);

        DsSummaryUi failures =
            tbProc.getTrackedBranchTestFailures(brachName,
                false,
                1,
                creds,
                SyncMode.RELOAD_QUEUED,
                false,
                null,
                null,
                DisplayMode.OnlyFailures,
                null,
                -1, false, false);

        String issRes = registerIssuesAndNotifyLater(failures, backgroundOpsCreds);

        return "Tests " + failures.failedTests + " Suites " + failures.failedToFinish + " were checked. " + issRes;
    }

    public void stop() {
        if (executorService != null)
            executorService.shutdownNow();

    }
}
