package org.apache.ignite.ci.issue;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteScheduler;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.TestInBranch;
import org.apache.ignite.ci.mail.EmailSender;
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

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.BuildChainProcessor.normalizeBranch;

public class IssueDetector {
    private final Ignite ignite;
    private final IssuesStorage issuesStorage;

    public IssueDetector(Ignite ignite, IssuesStorage issuesStorage) {
        this.ignite = ignite;
        this.issuesStorage = issuesStorage;
    }

    public void registerIssuesLater(TestFailuresSummary res, ITcHelper helper, ICredentialsProv creds) {
        IgniteScheduler s = ignite.scheduler();

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
                String to = "dpavlov.spb@gmail.com";

                if (issuesStorage.needNotify(issue.issueKey, to)) {
                    toBeSent.computeIfAbsent(to, addr -> {
                        Notification notification = new Notification();
                        notification.ts = System.currentTimeMillis();
                        notification.addr = addr;
                        return notification;
                    }).addIssue(issue);
                }
            }

            Collection<Notification> values = toBeSent.values();
            for (Notification next : values) {
                String s = "MTCGA needs action from you: " + next.issues.size() + " new failures to be handled";

                String html = next.toHtml();

                EmailSender.sendEmail(next.addr, s,   html);
            }
        } catch (Exception e) {
            System.err.println("Fail to sent notifications");

            e.printStackTrace();
        }
    }


    public boolean registerNewIssues(TestFailuresSummary res, ITcHelper helper, ICredentialsProv creds) {
        int newIssues = 0;
        List<ChainAtServerCurrentStatus> servers = res.servers;

        for (ChainAtServerCurrentStatus next : servers) {
            if(!creds.hasAccess(next.serverId))
                continue;

            IAnalyticsEnabledTeamcity teamcity = helper.server(next.serverId, creds);

            for (SuiteCurrentStatus suiteCurrentStatus : next.suites) {

                String normalizeBranch = normalizeBranch(suiteCurrentStatus.branchName());

                for (TestFailure testFailure : suiteCurrentStatus.testFailures) {
                    if(registerTestFailIssues(teamcity, next.serverId, normalizeBranch, testFailure))
                        newIssues++;
                }
            }
        }
        return (newIssues>0);
    }

    private boolean registerTestFailIssues(IAnalyticsEnabledTeamcity teamcity,
                                           String serverId, String normalizeBranch,
                                           TestFailure testFailure) {
        String name = testFailure.name;
        TestInBranch testInBranch = new TestInBranch(name, normalizeBranch);
        RunStat runStat = teamcity.getTestRunStatProvider().apply(testInBranch);

        if (runStat == null)
            return false;

        RunStat.TestId testId = runStat.detectTemplate(EventTemplates.newFailure);
        if (testId == null) {
            return false;
        }

        int buildId = testId.getBuildId();
        IssueKey issueKey = new IssueKey(serverId, buildId, name);

        if (issuesStorage.cache().containsKey(issueKey))
            return false; //duplicate

        Issue issue = new Issue(issueKey);

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
}
