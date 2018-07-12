package org.apache.ignite.ci.issue;

import com.google.common.base.Strings;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteScheduler;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.mail.EmailSender;
import org.apache.ignite.ci.mail.SlackSender;
import org.apache.ignite.ci.tcmodel.changes.Change;
import org.apache.ignite.ci.tcmodel.changes.ChangeRef;
import org.apache.ignite.ci.tcmodel.changes.ChangesList;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.ignite.ci.web.rest.tracked.GetTrackedBranchTestResults;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.BuildChainProcessor.normalizeBranch;

public class IssueDetector {
    public static final String SLACK = "slack:";
    private final Ignite ignite;
    private final IssuesStorage issuesStorage;
    private UserAndSessionsStorage userStorage;

    private final AtomicBoolean init = new AtomicBoolean();
    private ICredentialsProv backgroundOpsCreds = null;
    private ITcHelper backgroundOpsTcHelper;
    private ScheduledExecutorService executorService;


    public IssueDetector(Ignite ignite, IssuesStorage issuesStorage,
        UserAndSessionsStorage userStorage) {
        this.ignite = ignite;
        this.issuesStorage = issuesStorage;
        this.userStorage = userStorage;
    }

    public void registerIssuesLater(TestFailuresSummary res, ITcHelper helper, ICredentialsProv creds) {
        IgniteScheduler s = ignite.scheduler();

        if (!FullQueryParams.DEFAULT_BRANCH_NAME.equals(res.getTrackedBranch()))
            return;

        if (creds == null)
            return;

        s.runLocal(
                () -> {
                    boolean newIssFound = registerNewIssues(res, helper, creds);

                    s.runLocal(this::sendNewNotifications, 10, TimeUnit.SECONDS);

                }, 10, TimeUnit.SECONDS
        );
    }

    private void sendNewNotifications() {
        try {
            Collection<TcHelperUser> userForPossibleNotifications = new ArrayList<>();

            for(Cache.Entry<String, TcHelperUser> entry : userStorage.users()) {
                TcHelperUser tcHelperUser = entry.getValue();

                if (Strings.isNullOrEmpty(tcHelperUser.email))
                    continue;

                if(tcHelperUser.hasSubscriptions())
                    userForPossibleNotifications.add(tcHelperUser);
            }

            Map<String, Notification> toBeSent = new HashMap<>();

            for (Issue issue : issuesStorage.all()) {
                long detected = issue.detectedTs == null ? 0 : issue.detectedTs;
                long issueAgeMs = System.currentTimeMillis() - detected;
                if (issueAgeMs > TimeUnit.HOURS.toMillis(2))
                    continue;

                String to1 = "dpavlov.spb@gmail.com";
                String to2 = "slack:dpavlov"; //todo implement direct slask notification

                List<String> addrs = new ArrayList<>();

                String property = HelperConfig.loadEmailSettings().getProperty(HelperConfig.SLACK_CHANNEL);
                if (property != null)
                    addrs.add(SLACK + "#" + property);

                for (TcHelperUser next : userForPossibleNotifications) {
                    if (next.getCredentials(issue.issueKey().server) != null) {
                        if (next.isSubscribed(issue.trackedBranchName)) {
                            System.err.println("User " + next + " is candidate for notification " + next.email
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

            Collection<Notification> values = toBeSent.values();
            for (Notification next : values) {
                if(next.addr.startsWith(SLACK)) {
                    String substring = next.addr.substring(SLACK.length());

                    List<String> messages = next.toSlackMarkup();
                    for (String msg : messages) {
                        if (!SlackSender.sendMessage(substring, msg))
                            break;
                    }
                } else {
                    String builds = next.buildIdToIssue.keySet().toString();
                    String subj = "[MTCGA]: " + next.countIssues() + " new failures in builds " + builds + " needs to be handled";

                    EmailSender.sendEmail(next.addr, subj, next.toHtml(), next.toPlainText());
                }
            }
        } catch (Exception e) {
            System.err.println("Fail to sent notifications");

            e.printStackTrace();
        }
    }


    public boolean registerNewIssues(TestFailuresSummary res, ITcHelper helper, ICredentialsProv creds) {
        int newIssues = 0;

        for (ChainAtServerCurrentStatus next : res.servers) {
            if(!creds.hasAccess(next.serverId))
                continue;

            IAnalyticsEnabledTeamcity teamcity = helper.server(next.serverId, creds);

            for (SuiteCurrentStatus suiteCurrentStatus : next.suites) {

                String normalizeBranch = normalizeBranch(suiteCurrentStatus.branchName());

                for (TestFailure testFailure : suiteCurrentStatus.testFailures) {
                    if(registerTestFailIssues(res, teamcity, next.serverId, normalizeBranch, testFailure))
                        newIssues++;
                }
            }
        }
        return (newIssues>0);
    }

    private boolean registerTestFailIssues(TestFailuresSummary res,
        IAnalyticsEnabledTeamcity teamcity,
        String srvId,
        String normalizeBranch,
        TestFailure testFailure) {

        String name = testFailure.name;
        TestInBranch testInBranch = new TestInBranch(name, normalizeBranch);

        RunStat runStat = teamcity.getTestRunStatProvider().apply(testInBranch);

        if (runStat == null)
            return false;

        RunStat.TestId testId = runStat.detectTemplate(EventTemplates.newFailure);
        if (testId == null)
            return false;

        int buildId = testId.getBuildId();
        IssueKey issueKey = new IssueKey(srvId, buildId, name);

        if (issuesStorage.cache().containsKey(issueKey))
            return false; //duplicate

        Issue issue = new Issue(issueKey);
        issue.trackedBranchName = res.getTrackedBranch();
        issue.displayName = testFailure.testName;
        issue.webUrl = testFailure.webUrl;

        issue.displayType = "New test failure";

        Build build = teamcity.getBuild(buildId);

        if (build.changesRef != null) {
            ChangesList changeList = teamcity.getChangesList(build.changesRef.href);
            // System.err.println("changes: " + changeList);
            if (changeList.changes != null) {
                for (ChangeRef next : changeList.changes) {
                    if (!isNullOrEmpty(next.href)) {
                        // just to cache this change
                        Change change = teamcity.getChange(next.href);

                        issue.addChange(change.username, change.webUrl);
                    }
                }
            }
        }

        issuesStorage.cache().put(issueKey, issue);

        return true;
    }

    public boolean isAuthorized() {
        return backgroundOpsCreds != null && backgroundOpsTcHelper != null;
    }

    public void startBackgroundCheck(ITcHelper helper, ICredentialsProv prov) {

        try {
            if(init.compareAndSet(false, true)) {
                this.backgroundOpsCreds = prov;
                this.backgroundOpsTcHelper = helper;

                executorService = Executors.newScheduledThreadPool(1);

                executorService.scheduleAtFixedRate(this::checkFailures, 0, 15, TimeUnit.MINUTES);
            }
        }
        catch (Exception e) {
            e.printStackTrace();

            init.set(false);

            throw e;
        }
        // SchedulerFuture<?> future = ignite.scheduler().scheduleLocal(this::checkFailures, "? * * * * *");
    }

    private void checkFailures() {
        int buildsToQry = EventTemplates.templates.stream().mapToInt(EventTemplate::cntEvents).max().getAsInt();

        GetTrackedBranchTestResults.getTrackedBranchTestFailures(FullQueryParams.DEFAULT_BRANCH_NAME,
            false, buildsToQry, backgroundOpsTcHelper, backgroundOpsCreds);

        TestFailuresSummary failures =
            GetTrackedBranchTestResults.getTrackedBranchTestFailures(FullQueryParams.DEFAULT_BRANCH_NAME,
                false,
                1,
                backgroundOpsTcHelper,
                backgroundOpsCreds);

        registerIssuesLater(failures, backgroundOpsTcHelper, backgroundOpsCreds);
    }

    public void stop() {
        if (executorService != null)
            executorService.shutdownNow();

    }
}
