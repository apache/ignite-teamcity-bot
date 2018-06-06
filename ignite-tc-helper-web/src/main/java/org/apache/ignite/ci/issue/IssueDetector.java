package org.apache.ignite.ci.issue;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.ignite.ci.web.rest.GetCurrTestFailures;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.apache.ignite.scheduler.SchedulerFuture;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.BuildChainProcessor.normalizeBranch;

public class IssueDetector {
    public static final String SLACK = "slack:";
    private final Ignite ignite;
    private final IssuesStorage issuesStorage;


    private final AtomicBoolean init = new AtomicBoolean();
    private ICredentialsProv backgroundOpsCreds = null;
    private ITcHelper backgroundOpsTcHelper;
    private ScheduledExecutorService executorService;


    public IssueDetector(Ignite ignite, IssuesStorage issuesStorage) {
        this.ignite = ignite;
        this.issuesStorage = issuesStorage;
    }

    public void registerIssuesLater(TestFailuresSummary res, ITcHelper helper, ICredentialsProv creds) {
        IgniteScheduler s = ignite.scheduler();

        if(!FullQueryParams.DEFAULT_BRANCH_NAME.equals(res.getTrackedBranch()))
            return;

        s.runLocal(
                () -> {
                    boolean newIssFound = registerNewIssues(res, helper, creds);

                    if (newIssFound || true)
                        s.runLocal(()->{
                            sendNewNotifications();

                        }, 10, TimeUnit.SECONDS);

                }, 10, TimeUnit.SECONDS
        );
    }

    private void sendNewNotifications() {
        try {
            Map<String, Notification> toBeSent = new HashMap<>();

            for (Issue issue : issuesStorage.all()) {
                String to1 = "dpavlov.spb@gmail.com";
                String to2 = "slack:dpavlov";

                List<String> addrs = new ArrayList<>(Arrays.asList(to1, to2));

                String property = HelperConfig.loadEmailSettings().getProperty(HelperConfig.SLACK_CHANNEL);
                if (property != null)
                    addrs.add(SLACK + "#" + property);

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
                    String s = "MTCGA needs action from you: " + next.countIssues() + " new failures to be handled";

                    String html = next.toHtml();

                    EmailSender.sendEmail(next.addr, s, html);
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
        //todo add branch of failure


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
        TestFailuresSummary failures = GetCurrTestFailures.getTrackedBranchTestFailures(FullQueryParams.DEFAULT_BRANCH_NAME, false,
            backgroundOpsTcHelper,
            backgroundOpsCreds);

        registerIssuesLater(failures, backgroundOpsTcHelper, backgroundOpsCreds);
    }

    public void stop() {
        if (executorService != null)
            executorService.shutdownNow();

    }
}
