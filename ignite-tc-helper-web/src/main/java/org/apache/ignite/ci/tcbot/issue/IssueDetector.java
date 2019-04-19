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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.issue.EventTemplate;
import org.apache.ignite.ci.issue.EventTemplates;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.ci.issue.IssueType;
import org.apache.ignite.ci.jobs.CheckQueueJob;
import org.apache.ignite.ci.mail.EmailSender;
import org.apache.ignite.ci.mail.SlackSender;
import org.apache.ignite.ci.tcbot.chain.TrackedBranchChainsProcessor;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.conf.TcServerConfig;
import org.apache.ignite.ci.tcbot.user.IUserStorage;
import org.apache.ignite.ci.teamcity.ignited.IRunHistory;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.SyncMode;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.ci.teamcity.ignited.runhist.RunHistSync.normalizeBranch;

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
    private ICredentialsProv backgroundOpsCreds;
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

    private String registerIssuesAndNotifyLater(TestFailuresSummary res,
        ICredentialsProv creds) {

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
        List<TcHelperUser> userForPossibleNotifications = new ArrayList<>();

        userStorage.allUsers()
            .filter(TcHelperUser::hasEmail)
            .filter(TcHelperUser::hasSubscriptions)
            .forEach(userForPossibleNotifications::add);

        String slackCh = HelperConfig.loadEmailSettings().getProperty(HelperConfig.SLACK_CHANNEL);

        Map<String, Notification> toBeSent = new HashMap<>();

        AtomicInteger issuesChecked = new AtomicInteger();

        issuesStorage.allIssues()
            .peek(issue -> issuesChecked.incrementAndGet())
            .filter(issue -> {
                long detected = issue.detectedTs == null ? 0 : issue.detectedTs;
                long issueAgeMs = System.currentTimeMillis() - detected;

                return issueAgeMs <= TimeUnit.HOURS.toMillis(2);
            })
            .forEach(issue -> {
                List<String> addrs = new ArrayList<>();

                final String srvCode = issue.issueKey().server;
                final String defaultTracked = cfg.getTeamcityConfig(srvCode).defaultTrackedBranch();

                if (slackCh != null && defaultTracked.equals(issue.trackedBranchName))
                    addrs.add(SLACK + "#" + slackCh);

                for (TcHelperUser next : userForPossibleNotifications) {
                    if (next.getCredentials(srvCode) != null) {
                        if (next.isSubscribed(issue.trackedBranchName)) {
                            logger.info("User " + next + " is candidate for notification " + next.email
                                + " for " + issue);

                            addrs.add(next.email);
                        }
                    }
                }

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

        StringBuilder res = new StringBuilder();
        Collection<Notification> values = toBeSent.values();
        for (Notification next : values) {
            if (next.addr.startsWith(SLACK)) {
                String slackUser = next.addr.substring(SLACK.length());

                List<String> messages = next.toSlackMarkup();

                for (String msg : messages) {
                    final boolean snd = SlackSender.sendMessage(slackUser, msg);

                    res.append("Send ").append(slackUser).append(": ").append(snd);
                    if (!snd)
                        break;
                }
            }
            else {
                String builds = next.buildIdToIssue.keySet().toString();
                String subj = "[MTCGA]: " + next.countIssues() + " new failures in builds " + builds + " needs to be handled";

                EmailSender.sendEmail(next.addr, subj, next.toHtml(), next.toPlainText());
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
    protected String registerNewIssues(TestFailuresSummary res, ICredentialsProv creds) {
        int newIssues = 0;

        for (ChainAtServerCurrentStatus next : res.servers) {
            String srvId = next.serverId;

            if (!tcProv.hasAccess(srvId, creds))
                continue;

            ITeamcityIgnited tcIgnited = tcProv.server(srvId, creds);

            for (SuiteCurrentStatus suiteCurrentStatus : next.suites) {

                String normalizeBranch = normalizeBranch(suiteCurrentStatus.branchName());

                final String trackedBranch = res.getTrackedBranch();

                for (TestFailure testFailure : suiteCurrentStatus.testFailures) {
                    if (registerTestFailIssues(tcIgnited, srvId, normalizeBranch, testFailure, trackedBranch))
                        newIssues++;
                }

                if (registerSuiteFailIssues(tcIgnited, srvId, normalizeBranch, suiteCurrentStatus, trackedBranch))
                    newIssues++;
            }
        }

        return "New issues found " + newIssues;
    }

    private boolean registerSuiteFailIssues(ITeamcityIgnited tcIgnited,
        String srvId,
        String normalizeBranch,
        SuiteCurrentStatus suiteFailure,
        String trackedBranch) {

        String suiteId = suiteFailure.suiteId;

        SuiteInBranch key = new SuiteInBranch(suiteId, normalizeBranch);

        IRunHistory runStat = tcIgnited.getSuiteRunHist(key);

        if (runStat == null)
            return false;

        boolean issueFound = false;

        Integer firstFailedBuildId = runStat.detectTemplate(EventTemplates.newCriticalFailure);

        if (firstFailedBuildId != null && suiteFailure.hasCriticalProblem != null && suiteFailure.hasCriticalProblem) {
            IssueKey issueKey = new IssueKey(srvId, firstFailedBuildId, suiteId);

            if (issuesStorage.containsIssueKey(issueKey))
                return false; //duplicate

            Issue issue = new Issue(issueKey, IssueType.newCriticalFailure);
            issue.trackedBranchName = trackedBranch;
            issue.displayName = suiteFailure.name;
            issue.webUrl = suiteFailure.webToHist;
            issue.buildStartTs = tcIgnited.getBuildStartTs(issueKey.buildId);

            locateChanges(tcIgnited, firstFailedBuildId, issue);

            logger.info("Register new issue for suite fail: " + issue);

            issuesStorage.saveIssue(issue);

            issueFound = true;
        }

        return issueFound;
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
        String srvId,
        String normalizeBranch,
        TestFailure testFailure,
        String trackedBranch) {

        String name = testFailure.name;
        TestInBranch testInBranch = new TestInBranch(name, normalizeBranch);

        IRunHistory runStat = tcIgnited.getTestRunHist(testInBranch);

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

        IssueKey issueKey = new IssueKey(srvId, buildId, name);

        if (issuesStorage.containsIssueKey(issueKey))
            return false; //duplicate

        Issue issue = new Issue(issueKey, type);
        issue.trackedBranchName = trackedBranch;
        issue.displayName = testFailure.testName;
        issue.webUrl = testFailure.webUrl;

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

    public void startBackgroundCheck(ICredentialsProv prov) {
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
        List<String> ids = cfg.getTrackedBranchesIds();

        for (String tbranchName : ids) {
            try {
                checkFailuresEx(tbranchName);
            }
            catch (Exception e) {
                e.printStackTrace();

                logger.error("Failure periodic check failed: " + e.getMessage(), e);
            }
        }

    }

    /**
     * @param brachName
     */
    @AutoProfiling
    @MonitoredTask(name = "Detect Issues in tracked branch", nameExtArgIndex = 0)
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    protected String checkFailuresEx(String brachName) {
        int buildsToQry = EventTemplates.templates.stream().mapToInt(EventTemplate::cntEvents).max().getAsInt();

        ICredentialsProv creds = Preconditions.checkNotNull(backgroundOpsCreds, "Server should be authorized");

        tbProc.getTrackedBranchTestFailures(
            brachName,
            false,
            buildsToQry,
            creds,
            SyncMode.RELOAD_QUEUED);

        TestFailuresSummary failures =
            tbProc.getTrackedBranchTestFailures(brachName,
                false,
                1,
                creds,
                SyncMode.RELOAD_QUEUED
            );

        String issResult = registerIssuesAndNotifyLater(failures, backgroundOpsCreds);

        return "Tests " + failures.failedTests + " Suites " + failures.failedToFinish + " were checked. " + issResult;
    }

    public void stop() {
        if (executorService != null)
            executorService.shutdownNow();

    }
}
