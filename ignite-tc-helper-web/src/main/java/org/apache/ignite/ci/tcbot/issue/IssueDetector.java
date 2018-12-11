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

import com.google.common.base.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.issue.EventTemplate;
import org.apache.ignite.ci.issue.EventTemplates;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.teamcity.ignited.IRunHistory;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.change.ChangeCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.tcbot.chain.TrackedBranchChainsProcessor;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.jobs.CheckQueueJob;
import org.apache.ignite.ci.mail.EmailSender;
import org.apache.ignite.ci.mail.SlackSender;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.ci.teamcity.ignited.runhist.RunHistSync.normalizeBranch;

/**
 *
 */
public class IssueDetector {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(IssueDetector.class);

    /**Slack prefix, using this for email address will switch notifier to slack (if configured). */
    private static final String SLACK = "slack:";

    @Inject private IssuesStorage issuesStorage;
    @Inject private UserAndSessionsStorage userStorage;

    private final AtomicBoolean init = new AtomicBoolean();
    private ICredentialsProv backgroundOpsCreds;
    @Deprecated //todo use scheduler
    private ScheduledExecutorService executorService;

    @Inject private Provider<CheckQueueJob> checkQueueJobProv;

    /** Tracked Branch Processor. */
    @Inject private TrackedBranchChainsProcessor tbProc;

    /** Tc helper. */
    @Inject private ITcHelper tcHelper;

    /** Server provider. */
    @Inject private ITcServerProvider srvProvider;

    /** Server provider. */
    @Inject private ITeamcityIgnitedProvider tcProv;

    @Inject private IStringCompactor compactor;

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
        } catch (Exception e) {
            System.err.println("Fail to sent notifications");
            e.printStackTrace();

            logger.error("Failed to send notification", e.getMessage());
        } finally {
            sndNotificationGuard.set(false);
        }
    }

    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @AutoProfiling
    @MonitoredTask(name = "Send Notifications")
    protected String sendNewNotificationsEx() throws IOException {
        Collection<TcHelperUser> userForPossibleNotifications = new ArrayList<>();

        for(Cache.Entry<String, TcHelperUser> entry : userStorage.users()) {
            TcHelperUser tcHelperUser = entry.getValue();

            if (Strings.isNullOrEmpty(tcHelperUser.email))
                continue;

            if(tcHelperUser.hasSubscriptions())
                userForPossibleNotifications.add(tcHelperUser);
        }

        String slackCh = HelperConfig.loadEmailSettings().getProperty(HelperConfig.SLACK_CHANNEL);
        Map<String, Notification> toBeSent = new HashMap<>();

        int issuesChecked = 0;
        for (Issue issue : issuesStorage.all()) {
            issuesChecked++;
            long detected = issue.detectedTs == null ? 0 : issue.detectedTs;
            long issueAgeMs = System.currentTimeMillis() - detected;
            if (issueAgeMs > TimeUnit.HOURS.toMillis(2))
                continue;

            List<String> addrs = new ArrayList<>();

            if (slackCh != null && FullQueryParams.DEFAULT_TRACKED_BRANCH_NAME.equals(issue.trackedBranchName))
                addrs.add(SLACK + "#" + slackCh);

            for (TcHelperUser next : userForPossibleNotifications) {
                if (next.getCredentials(issue.issueKey().server) != null) {
                    if (next.isSubscribed(issue.trackedBranchName)) {
                        logger.info("User " + next + " is candidate for notification " + next.email
                            + " for " + issue);

                        addrs.add(next.email);
                    }
                }
            }

            for (String nextAddr : addrs) {
                if (issuesStorage.needNotify(issue.issueKey, nextAddr)) {
                    toBeSent.computeIfAbsent(nextAddr, addr -> {
                        Notification notification = new Notification();
                        notification.ts = System.currentTimeMillis();
                        notification.addr = addr;
                        return notification;
                    }).addIssue(issue);
                }
            }
        }

        if(toBeSent.isEmpty())
            return "Noting to notify, " + issuesChecked + " issues checked";

        StringBuilder res = new StringBuilder();
        Collection<Notification> values = toBeSent.values();
        for (Notification next : values) {
            if(next.addr.startsWith(SLACK)) {
                String slackUser = next.addr.substring(SLACK.length());

                List<String> messages = next.toSlackMarkup();

                for (String msg : messages) {
                    final boolean snd = SlackSender.sendMessage(slackUser, msg);

                    res.append("Send ").append(slackUser).append(": ").append(snd);
                    if (!snd)
                        break;
                }
            } else {
                String builds = next.buildIdToIssue.keySet().toString();
                String subj = "[MTCGA]: " + next.countIssues() + " new failures in builds " + builds + " needs to be handled";

                EmailSender.sendEmail(next.addr, subj, next.toHtml(), next.toPlainText());
                res.append("Send ").append(next.addr).append(" subject: ").append(subj);
            }
        }

        return res + ", " + issuesChecked + "issues checked";
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
            if(!creds.hasAccess(next.serverId))
                continue;

            IAnalyticsEnabledTeamcity teamcity = srvProvider.server(next.serverId, creds);

            ITeamcityIgnited tcIgnited = tcProv.server(next.serverId, creds);

            for (SuiteCurrentStatus suiteCurrentStatus : next.suites) {

                String normalizeBranch = normalizeBranch(suiteCurrentStatus.branchName());

                final String trackedBranch = res.getTrackedBranch();

                for (TestFailure testFailure : suiteCurrentStatus.testFailures) {
                    if(registerTestFailIssues(tcIgnited, teamcity, next.serverId, normalizeBranch, testFailure, trackedBranch))
                        newIssues++;
                }

                if(registerSuiteFailIssues(tcIgnited, teamcity, next.serverId, normalizeBranch, suiteCurrentStatus, trackedBranch))
                    newIssues++;
            }
        }

        return "New issues found " + newIssues;
    }

    private boolean registerSuiteFailIssues(ITeamcityIgnited tcIgnited, IAnalyticsEnabledTeamcity teamcity,
                                            String srvId,
                                            String normalizeBranch,
                                            SuiteCurrentStatus suiteFailure,
                                            String trackedBranch) {

        String suiteId = suiteFailure.suiteId;

        SuiteInBranch key = new SuiteInBranch(suiteId, normalizeBranch);

        Function<SuiteInBranch, ? extends IRunHistory> provider   =
            ITeamcity.NEW_RUN_STAT
                ? tcIgnited::getSuiteRunHist
                : teamcity.getBuildFailureRunStatProvider();

        IRunHistory runStat = provider.apply(key);

        if (runStat == null)
            return false;

        boolean issueFound = false;

        Integer firstFailedBuildId = runStat.detectTemplate(EventTemplates.newCriticalFailure);

        if (firstFailedBuildId != null && suiteFailure.hasCriticalProblem != null && suiteFailure.hasCriticalProblem) {
            IssueKey issueKey = new IssueKey(srvId, firstFailedBuildId, suiteId);

            if (issuesStorage.cache().containsKey(issueKey))
                return false; //duplicate

            Issue issue = new Issue(issueKey);
            issue.trackedBranchName = trackedBranch;
            issue.displayName = suiteFailure.name;
            issue.webUrl = suiteFailure.webToHist;
            issue.displayType = "New Critical Failure";

            locateChanges(tcIgnited, firstFailedBuildId, issue);

            logger.info("Register new issue for suite fail: " + issue);

            issuesStorage.cache().put(issueKey, issue);

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
                                           IAnalyticsEnabledTeamcity teamcity,
                                           String srvId,
                                           String normalizeBranch,
                                           TestFailure testFailure,
                                           String trackedBranch) {

        String name = testFailure.name;
        TestInBranch testInBranch = new TestInBranch(name, normalizeBranch);

        Function<TestInBranch, ? extends IRunHistory> function =
            ITeamcity.NEW_RUN_STAT
                ? tcIgnited::getTestRunHist
                : teamcity.getTestRunStatProvider();

        IRunHistory runStat = function.apply(testInBranch);

        if (runStat == null)
            return false;

        String displayType = null;

        Integer firstFailedBuildId = runStat.detectTemplate(EventTemplates.newContributedTestFailure);

        if (firstFailedBuildId != null)
            displayType = "Recently contributed test failed";

        if (firstFailedBuildId == null) {
            firstFailedBuildId = runStat.detectTemplate(EventTemplates.newFailure);

            if (firstFailedBuildId != null) {
                displayType = "New test failure";
                final String flakyComments = runStat.getFlakyComments();

                if (!Strings.isNullOrEmpty(flakyComments)) {
                    if (runStat.detectTemplate(EventTemplates.newFailureForFlakyTest) == null) {
                        logger.info("Skipping registering new issue for test fail:" +
                                " Test seems to be flaky " + name + ": " + flakyComments);

                        firstFailedBuildId = null;
                    } else
                        displayType = "New stable failure of a flaky test";
                }
            }
        }

        if (firstFailedBuildId == null)
            return false;

        int buildId = firstFailedBuildId;

        IssueKey issueKey = new IssueKey(srvId, buildId, name);

        if (issuesStorage.cache().containsKey(issueKey))
            return false; //duplicate

        Issue issue = new Issue(issueKey);
        issue.trackedBranchName = trackedBranch;
        issue.displayName = testFailure.testName;
        issue.webUrl = testFailure.webUrl;
        issue.displayType = displayType;

        locateChanges(tcIgnited, buildId, issue);

        logger.info("Register new issue for test fail: " + issue);

        issuesStorage.cache().put(issueKey, issue);

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
        List<String> ids = tcHelper.getTrackedBranchesIds();

        for (Iterator<String> iter = ids.iterator(); iter.hasNext(); ) {
            String tbranchName = iter.next();

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
     *
     * @param brachName
     */
    @AutoProfiling
    @MonitoredTask(name = "Detect Issues in tracked branch", nameExtArgIndex = 0)
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    protected String checkFailuresEx(String brachName) {
        int buildsToQry = EventTemplates.templates.stream().mapToInt(EventTemplate::cntEvents).max().getAsInt();

        TestFailuresSummary allHist = tbProc.getTrackedBranchTestFailures(brachName,
            false, buildsToQry, backgroundOpsCreds);

        TestFailuresSummary failures =
                tbProc.getTrackedBranchTestFailures(brachName,
                false,
                1,
                        backgroundOpsCreds
                );

        String issResult = registerIssuesAndNotifyLater(failures, backgroundOpsCreds);

        return "Tests " + failures.failedTests + " Suites " + failures.failedToFinish + " were checked. " + issResult;
    }

    public void stop() {
        if (executorService != null)
            executorService.shutdownNow();

    }
}
