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
import org.apache.ignite.ci.issue.*;
import org.apache.ignite.ci.jobs.CheckQueueJob;
import org.apache.ignite.ci.mail.EmailSender;
import org.apache.ignite.ci.mail.SlackSender;
import org.apache.ignite.tcbot.engine.issue.EventTemplate;
import org.apache.ignite.tcbot.engine.issue.EventTemplates;
import org.apache.ignite.tcbot.engine.tracked.TrackedBranchChainsProcessor;
import org.apache.ignite.ci.tcbot.user.IUserStorage;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.InvocationData;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
import org.apache.ignite.tcbot.engine.ui.DsSuiteUi;
import org.apache.ignite.tcbot.engine.ui.DsTestFailureUi;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.engine.conf.INotificationChannel;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.NotificationsConfig;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.ignite.tcignited.history.RunHistSync.normalizeBranch;

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
    protected String sendNewNotificationsEx() throws IOException {
        List<INotificationChannel> channels = new ArrayList<>();

        userStorage.allUsers()
            .filter(TcHelperUser::hasEmail)
            .filter(TcHelperUser::hasSubscriptions)
            .forEach(channels::add);

        channels.addAll(cfg.notifications().channels());

        Map<String, Notification> toBeSent = new HashMap<>();

        AtomicInteger issuesChecked = new AtomicInteger();

        issuesStorage.allIssues()
            .peek(issue -> issuesChecked.incrementAndGet())
            .filter(issue -> {
                long detected = issue.detectedTs == null ? 0 : issue.detectedTs;
                long issueAgeMs = System.currentTimeMillis() - detected;

                return issueAgeMs <= TimeUnit.HOURS.toMillis(2);
            })
            .filter(issue -> {
                long buildStartTs = issue.buildStartTs == null ? 0 : issue.buildStartTs;
                long buildAgeMs = System.currentTimeMillis() - buildStartTs;
                long maxBuildAgeToNotify = TimeUnit.DAYS.toMillis(InvocationData.MAX_DAYS) / 2;

                return buildAgeMs <= maxBuildAgeToNotify;
            })
            .filter(issue -> {
                return cfg.getTrackedBranches()
                    .get(issue.trackedBranchName)
                    .filter(tb -> !tb.disableIssueTypes().contains(issue.type()))
                    .isPresent();
            })
            .forEach(issue -> {
                List<String> addrs = new ArrayList<>();

                final String srvCode = issue.issueKey().server;

                channels.stream()
                    .filter(ch -> ch.isServerAllowed(srvCode))
                    .filter(ch -> ch.isSubscribedToBranch(issue.trackedBranchName))
                    .filter(ch -> {
                        if (ch.hasTagFilter())
                            return issue.buildTags().stream().anyMatch(ch::isSubscribedToTag);

                        return true;
                    })
                    .forEach(channel -> {
                        String email = channel.email();
                        String slack = channel.slack();
                        logger.info("User/channel " + channel + " is candidate for notification " + email
                            + " , " + slack + "for " + issue);

                        if (!Strings.isNullOrEmpty(email))
                            addrs.add(email);

                        if (!Strings.isNullOrEmpty(slack))
                            addrs.add(SLACK + slack);
                    });

                for (String nextAddr : addrs) {
                    if (issuesStorage.setNotified(issue.issueKey, nextAddr)) {
                        toBeSent.computeIfAbsent(nextAddr, addr -> {
                            Notification notification = new Notification();
                            notification.ts = System.currentTimeMillis();
                            notification.addr = addr;
                            return notification;
                        }).addIssue(issue);
                    }
                }
            });

        if (toBeSent.isEmpty())
            return "Noting to notify, " + issuesChecked + " issues checked";

        NotificationsConfig notifications = cfg.notifications();

        StringBuilder res = new StringBuilder();
        Collection<Notification> values = toBeSent.values();
        for (Notification next : values) {
            if (next.addr.startsWith(SLACK)) {
                String slackUser = next.addr.substring(SLACK.length());

                List<String> messages = next.toSlackMarkup();

                for (String msg : messages) {
                    final boolean snd = SlackSender.sendMessage(slackUser, msg, notifications);

                    res.append("Send ").append(slackUser).append(": ").append(snd);
                    if (!snd)
                        break;
                }
            }
            else {
                String builds = next.buildIdToIssue.keySet().toString();
                String subj = "[MTCGA]: " + next.countIssues() + " new failures in builds " + builds + " needs to be handled";

                EmailSender.sendEmail(next.addr, subj, next.toHtml(), next.toPlainText(), notifications);
                res.append("Send ").append(next.addr).append(" subject: ").append(subj);
            }
        }

        return res + ", " + issuesChecked.get() + "issues checked";
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
        Issue issue = new Issue(issueKey, issType);
        issue.trackedBranchName = trackedBranch;
        issue.displayName = suiteFailure.name;
        issue.webUrl = suiteFailure.webToHist;
        issue.buildStartTs = tcIgnited.getBuildStartTs(issueKey.buildId);

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

                if (!Strings.isNullOrEmpty(flakyComments)) {
                    if (runStat.detectTemplate(EventTemplates.newFailureForFlakyTest) == null) {
                        logger.info("Skipping registering new issue for test fail:" +
                            " Test seems to be flaky " + name + ": " + flakyComments);

                        firstFailedBuildId = null;
                    }
                    else
                        type = IssueType.newFailureForFlakyTest;
                }
            }
        }

        if (firstFailedBuildId == null)
            return false;

        if (type == null)
            return false;

        int buildId = firstFailedBuildId;

        IssueKey issueKey = new IssueKey(srvCode, buildId, name);

        if (issuesStorage.containsIssueKey(issueKey))
            return false; //duplicate

        Issue issue = new Issue(issueKey, type);
        issue.trackedBranchName = trackedBranch;
        issue.displayName = testFailure.testName;
        issue.webUrl = testFailure.webUrl;

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
            false);

        DsSummaryUi failures =
            tbProc.getTrackedBranchTestFailures(brachName,
                false,
                1,
                creds,
                SyncMode.RELOAD_QUEUED,
                false);

        String issRes = registerIssuesAndNotifyLater(failures, backgroundOpsCreds);

        return "Tests " + failures.failedTests + " Suites " + failures.failedToFinish + " were checked. " + issRes;
    }

    public void stop() {
        if (executorService != null)
            executorService.shutdownNow();

    }
}
