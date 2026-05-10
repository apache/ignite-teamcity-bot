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

package org.apache.ignite.tcbot.integration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.ignite.tcbot.integration.IntegrationTestEnvironment.request;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * First black-box flow through bot REST used by the UI: login, queue trigger, and emulator-side completion.
 */
public class BotLoginTriggerQueueFlowTest {
    /** */
    private static final Pattern FINISHED_PROCESS = Pattern.compile("\"finished\"\\s*:\\s*[1-9][0-9]*");

    /** */
    private static final Pattern BUILD_ID = Pattern.compile("<build[^>]* id=\"([0-9]+)\"");

    /** */
    private static IntegrationTestEnvironment env;

    /** */
    @BeforeClass
    public static void startSuite() throws Exception {
        env = IntegrationTestEnvironment.get();
        env.resetEmulators();
    }

    /** */
    @Test
    public void loginThenLoadPrTriggerBuildAndSeeItInUiRest() throws Exception {
        IntegrationTestEnvironment.HttpResponse primaryServer = request("GET",
            env.botUrl + "/rest/login/primaryServerData", null, null, null);

        assertEquals(primaryServer.body, 200, primaryServer.status);
        assertTrue(primaryServer.body.contains("127.0.0.1:" + env.teamcityPort));

        String token = env.login();

        createIssueAndPullRequest12004();
        runTestOnlyAction(token, "refresh-jira", 700000010L);
        runTestOnlyAction(token, "refresh-github", 700000011L);

        IntegrationTestEnvironment.HttpResponse contributions = request("GET", env.botUrl
            + "/rest/visa/contributions?serverId=apache",
            "Token " + token, null, null);

        assertEquals(contributions.body, 200, contributions.status);
        assertTrue(contributions.body, contributions.body.contains("\"prNumber\":12004"));
        assertTrue(contributions.body, contributions.body.contains("IGNITE-20004"));

        String processId = "700000012";
        IntegrationTestEnvironment.HttpResponse trigger = request("GET", env.botUrl + "/rest/build/triggerBuildsAsync"
            + "?srvCode=apache"
            + "&branchName=" + enc("pull/12004/head")
            + "&parentSuiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&suiteIdList=" + enc("IgniteTests24Java17_RunAll")
            + "&top=false"
            + "&observe=false"
            + "&cleanRebuild=false"
            + "&processId=" + processId,
            "Token " + token, null, null);

        assertEquals(trigger.body, 200, trigger.status);
        assertTrue(trigger.body.contains("Trigger process started"));

        int buildId = waitForTriggeredBuild("pull/12004/head");

        IntegrationTestEnvironment.HttpResponse completed = request("POST", env.teamcityUrl
            + "/__test__/teamcity/complete-build", null, "application/json",
            "{\"buildId\":" + buildId + ",\"status\":\"SUCCESS\",\"tests\":[]}");

        assertEquals(completed.body, 200, completed.status);
        assertTrue(completed.body.contains("\"state\": \"finished\""));
        assertTrue(completed.body.contains("\"status\": \"SUCCESS\""));
    }

    /** */
    private static void createIssueAndPullRequest12004() throws Exception {
        createIssueAndPullRequest(12004, "IGNITE-20004", "Integration test generated issue");
    }

    /** */
    private static void createIssueAndPullRequest(int prNum, String ticket, String summary) throws Exception {
        IntegrationTestEnvironment.HttpResponse issue = request("POST", env.jiraUrl + "/__test__/jira/create-issue",
            null, "application/json", "{\"key\":\"" + ticket + "\",\"summary\":\"" + summary + "\"}");

        assertEquals(issue.body, 201, issue.status);

        IntegrationTestEnvironment.HttpResponse pr = request("POST", env.githubUrl + "/__test__/github/create-pr",
            null, "application/json", "{\"issue\":\"" + ticket + "\",\"number\":" + prNum
                + ",\"user\":\"ignite-tester\"}");

        assertEquals(pr.body, 201, pr.status);
        assertTrue(pr.body, pr.body.contains("\"number\": " + prNum));
    }

    /** */
    @Test
    public void testOnlyRefreshRunsThroughSchedulerAndReportsProcessStatus() throws Exception {
        String token = env.login();
        long processId = 700000002L;
        IntegrationTestEnvironment.HttpResponse start = request("POST", env.botUrl
            + "/rest/__test__/bot/refresh-jira?serverId=apache&processId=" + processId,
            "Token " + token, null, null);

        assertEquals(start.body, 200, start.status);
        assertTrue(start.body.contains("\"queued\":true"));
        assertTrue(start.body.contains("\"task\":\"TestOnly.refreshJira.apache\""));

        String status = waitForProcess(processId, token);

        assertTrue(status, status.contains("\"kind\":\"testOnly\""));
        assertTrue(status, status.contains("serverId"));
        assertTrue(status, status.contains("tickets"));
    }

    /** */
    @Test
    public void refreshedContributionsExposeSeededUnhappyPrBuild() throws Exception {
        String token = env.login();

        runTestOnlyAction(token, "refresh-jira", 700000003L);
        runTestOnlyAction(token, "refresh-github", 700000004L);
        runPrBuildRefRecheck(token, 700000005L, "IgniteTests24Java17_RunAll", "pull/12006/head");

        IntegrationTestEnvironment.HttpResponse prResults = request("GET", env.botUrl
            + "/rest/pr/results?serverId=apache"
            + "&suiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&branchForTc=" + enc("pull/12006/head")
            + "&action=" + enc("Latest"),
            "Token " + token, null, null);

        assertEquals(prResults.body, 200, prResults.status);
        assertTrue(prResults.body, prResults.body.contains("org.apache.ignite.sql.SqlRetryTest.testRetryOnTopologyChange"));
        assertTrue(prResults.body, prResults.body.contains("\"totalBlockers\":1"));
        assertTrue(prResults.body, !prResults.body.contains("Build not found"));

        IntegrationTestEnvironment.HttpResponse contributions = request("GET", env.botUrl
            + "/rest/visa/contributions?serverId=apache",
            "Token " + token, null, null);

        assertEquals(contributions.body, 200, contributions.status);
        assertTrue(contributions.body, contributions.body.contains("\"prNumber\":12006"));
        assertTrue(contributions.body, contributions.body.contains("pull/12006/head"));

        IntegrationTestEnvironment.HttpResponse status = request("GET", env.botUrl
            + "/rest/visa/contributionStatus?serverId=apache&prId=12006",
            "Token " + token, null, null);

        assertEquals(status.body, 200, status.status);
        assertTrue(status.body, status.body.contains("IgniteTests24Java17_RunAll"));
        assertTrue(status.body, status.body.contains("pull/12006/head"));
        assertTrue(status.body, status.body.contains("\"suiteIsFinished\":true"));

        String visa = waitForVisaBlockers(token, "pull/12006/head", 1);

        assertTrue(visa, visa.contains("\"blockers\":1"));
    }

    /** */
    @Test
    public void commentBuildAnalysisPublishesToJiraAndGithub() throws Exception {
        String token = env.login();

        runTestOnlyAction(token, "refresh-jira", 700000020L);
        runTestOnlyAction(token, "refresh-github", 700000021L);
        runPrBuildRefRecheck(token, 700000022L, "IgniteTests24Java17_RunAll", "pull/12006/head");

        IntegrationTestEnvironment.HttpResponse prResults = request("GET", env.botUrl
            + "/rest/pr/results?serverId=apache"
            + "&suiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&branchForTc=" + enc("pull/12006/head")
            + "&action=" + enc("Latest"),
            "Token " + token, null, null);

        assertEquals(prResults.body, 200, prResults.status);
        assertTrue(prResults.body, prResults.body.contains("org.apache.ignite.sql.SqlRetryTest.testRetryOnTopologyChange"));

        String status = commentBuildAnalysis(token, 700000023L, "pull/12006/head", "IGNITE-20006", 12006);

        assertTrue(status, status.contains("JIRA ticket commented: IGNITE-20006"));
        assertTrue(status, status.contains("GitHub PR commented: PR #12006"));

        assertServiceCommentContains("JIRA", env.jiraUrl + "/rest/api/2/issue/IGNITE-20006/comment",
            "Bearer jira-test-token", "tcbot-analysis-comment", "pull/12006/head");
        assertServiceCommentContains("GitHub", env.githubUrl + "/repos/apache/ignite/issues/12006/comments",
            "Bearer CAFEBABE", "tcbot-analysis-comment", "pull/12006/head");
    }

    /** */
    @Test
    public void requestedVisaIsScheduledAndVisibleInVisaHistory() throws Exception {
        String token = env.login();

        runTestOnlyAction(token, "refresh-jira", 700000030L);
        runTestOnlyAction(token, "refresh-github", 700000031L);

        IntegrationTestEnvironment.HttpResponse trigger = request("GET", env.botUrl + "/rest/build/triggerBuildsAsync"
            + "?srvCode=apache"
            + "&branchName=" + enc("pull/12001/head")
            + "&parentSuiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&suiteIdList=" + enc("IgniteTests24Java17_RunAll")
            + "&top=false"
            + "&observe=true"
            + "&ticketId=" + enc("IGNITE-20001")
            + "&comment=" + enc("JIRA,GITHUB")
            + "&prNum=12001"
            + "&commentOnlyIfNoBlockers=false"
            + "&cleanRebuild=false"
            + "&processId=700000032",
            "Token " + token, null, null);

        assertEquals(trigger.body, 200, trigger.status);
        assertTrue(trigger.body, trigger.body.contains("Trigger process started"));

        String status = waitForProcess(700000032L, token);

        assertTrue(status, status.contains("Scheduling a result comment after builds finish.")
            || status.contains("will be notified after the tests are completed")
            || status.contains("result comments will stay pending"));
        waitForVisaHistoryEntry(token, "pull/12001/head", "IGNITE-20001", 12001);
    }

    /** */
    @Test
    public void authorizedUserCanRequestVisaForFullRunAndBlockerRerun() throws Exception {
        String token = env.login();

        authorizeServer(token);
        createIssueAndPullRequest(12008, "IGNITE-20008", "Integration full run visa");
        runTestOnlyAction(token, "refresh-jira", 700000050L);
        runTestOnlyAction(token, "refresh-github", 700000051L);

        triggerObservedVisa(token, 700000052L, "pull/12008/head", "IgniteTests24Java17_RunAll",
            "IGNITE-20008", 12008, false);
        waitForVisaHistoryEntry(token, "pull/12008/head", "IGNITE-20008", 12008, 90);
        waitForObservedVisaComments(token, 700001000L, "pull/12008/head", "IGNITE-20008", 12008);

        runPrBuildRefRecheck(token, 700000053L, "IgniteTests24Java17_RunAll", "pull/12006/head");
        triggerObservedVisa(token, 700000054L, "pull/12006/head", "IgniteTests24Java17_Sql",
            "IGNITE-20006", 12006, false);

        String running = refreshRunningVisas(token, 700000055L);

        assertTrue(running, running.contains("\"branchName\":\"pull/12006/head\""));
        assertTrue(running, running.contains("\"ticket\":\"IGNITE-20006\""));
        assertTrue(running, running.contains("\"rerun\":true"));
        assertTrue(running, running.contains("\"buildIds\""));
        waitForVisaHistoryEntry(token, "pull/12006/head", "IGNITE-20006", 12006, 120);
        waitForObservedVisaComments(token, 700002000L, "pull/12006/head", "IGNITE-20006", 12006);
    }

    /** */
    @Test
    public void singleBuildReportCanPostAnalysisComment() throws Exception {
        String token = env.login();

        runTestOnlyAction(token, "refresh-jira", 700000060L);
        runTestOnlyAction(token, "refresh-github", 700000061L);

        IntegrationTestEnvironment.HttpResponse report = request("GET", env.botUrl
            + "/rest/build/failures?serverId=apache&buildId=800201",
            "Token " + token, null, null);

        assertEquals(report.body, 200, report.status);
        assertTrue(report.body, report.body.contains("SqlRetryTest.testRetryOnTopologyChange"));

        String status = commentBuildAnalysis(token, 700000062L, "pull/12006/head", "IGNITE-20006", 12006);

        assertTrue(status, status.contains("JIRA ticket commented: IGNITE-20006"));
        assertTrue(status, status.contains("GitHub PR commented: PR #12006"));
        assertServiceCommentContains("JIRA", env.jiraUrl + "/rest/api/2/issue/IGNITE-20006/comment",
            "Bearer jira-test-token", "tcbot-analysis-comment", "pull/12006/head");
        assertServiceCommentContains("GitHub", env.githubUrl + "/repos/apache/ignite/issues/12006/comments",
            "Bearer CAFEBABE", "tcbot-analysis-comment", "pull/12006/head");
    }

    /** */
    @Test
    public void suiteRetriggerAppearsInPrResults() throws Exception {
        String token = env.login();

        runTestOnlyAction(token, "refresh-jira", 700000040L);
        runTestOnlyAction(token, "refresh-github", 700000041L);
        runPrBuildRefRecheck(token, 700000042L, "IgniteTests24Java17_RunAll", "pull/12006/head");

        IntegrationTestEnvironment.HttpResponse before = request("GET", env.botUrl
            + "/rest/pr/results?serverId=apache"
            + "&suiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&branchForTc=" + enc("pull/12006/head")
            + "&action=" + enc("Latest"),
            "Token " + token, null, null);

        assertEquals(before.body, 200, before.status);
        assertTrue(before.body, before.body.contains("\"totalBlockers\":1"));

        String suiteId = "IgniteTests24Java17_Sql";
        String secondSuiteId = "IgniteTests24Java17_Pds1";
        IntegrationTestEnvironment.HttpResponse trigger = request("GET", env.botUrl + "/rest/build/triggerBuildsAsync"
            + "?srvCode=apache"
            + "&branchName=" + enc("pull/12006/head")
            + "&parentSuiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&suiteIdList=" + enc(suiteId + "," + secondSuiteId)
            + "&top=false"
            + "&observe=false"
            + "&cleanRebuild=false"
            + "&processId=700000043",
            "Token " + token, null, null);

        assertEquals(trigger.body, 200, trigger.status);
        assertTrue(trigger.body, trigger.body.contains("Trigger process started"));

        int buildId = waitForTriggeredBuild("pull/12006/head", suiteId);
        int secondBuildId = waitForTriggeredBuild("pull/12006/head", secondSuiteId);

        assertTrue("Suite retrigger should create a concrete suite build id", buildId > 0);
        assertTrue("Suite retrigger should create the second concrete suite build id", secondBuildId > 0);
        runPrBuildRefRecheck(token, 700000044L, suiteId, "pull/12006/head");
        runPrBuildRefRecheck(token, 700000045L, secondSuiteId, "pull/12006/head");
        waitForPrResultsSuiteBuild(token, "pull/12006/head", suiteId);
        waitForPrResultsSuiteBuild(token, "pull/12006/head", secondSuiteId);
    }

    /** */
    @Test
    public void currentPageRestModelForMasterIsNotEmpty() throws Exception {
        String token = env.login();
        IntegrationTestEnvironment.HttpResponse response = request("GET",
            env.botUrl + "/rest/tracked/results?branch=master", "Token " + token, null, null);

        assertEquals(response.body, 200, response.status);
        assertTrue(response.body.contains("\"servers\""));
        assertTrue("current.html model should contain at least one suite: " + response.body,
            response.body.contains("\"suites\""));
    }

    /** */
    private static int waitForTriggeredBuild(String branch) throws Exception {
        return waitForTriggeredBuild(branch, "IgniteTests24Java17_RunAll");
    }

    /** */
    private static int waitForTriggeredBuild(String branch, String suiteId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        String url = env.teamcityUrl + "/app/rest/latest/builds?locator="
            + enc("buildType:(id:" + suiteId + "),branch:" + branch);
        String lastBody = "";

        while (System.nanoTime() < deadline) {
            IntegrationTestEnvironment.HttpResponse response = request("GET", url, env.basicAuth(), null, null);
            lastBody = response.status + " " + response.body;

            if (response.status == 200
                && response.body.contains("buildTypeId=\"" + suiteId + "\"")
                && response.body.contains("branchName=\"" + branch + "\"")) {
                Matcher matcher = BUILD_ID.matcher(response.body);

                if (matcher.find())
                    return Integer.parseInt(matcher.group(1));
            }

            Thread.sleep(250);
        }

        throw new IllegalStateException("Build was not triggered by bot for " + suiteId + "/" + branch +
            ", last status: " + lastBody);
    }

    /** */
    private static void waitForPrResultsSuiteBuild(String token, String branch, String suiteId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        String url = env.botUrl + "/rest/pr/results?serverId=apache"
            + "&suiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&branchForTc=" + enc(branch)
            + "&action=" + enc("Latest");
        String lastBody = "";

        while (System.nanoTime() < deadline) {
            IntegrationTestEnvironment.HttpResponse response = request("GET", url, "Token " + token, null, null);
            lastBody = response.status + " " + response.body;

            if (response.status == 200
                && response.body.contains("\"suiteId\":\"" + suiteId + "\"")
                && (response.body.contains("\"runningBuildCount\":1")
                    || response.body.contains("\"queuedBuildCount\":1")))
                return;

            Thread.sleep(500);
        }

        throw new IllegalStateException("Suite retrigger did not appear in PR results for " + suiteId + "/" +
            branch + ", last status: " + lastBody);
    }

    /** */
    private static void waitForVisaHistoryEntry(String token, String branch, String ticket, int prNum) throws Exception {
        waitForVisaHistoryEntry(token, branch, ticket, prNum, 30);
    }

    /** */
    private static void waitForVisaHistoryEntry(String token, String branch, String ticket, int prNum, int timeoutSec)
        throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSec).toNanos();
        String url = env.botUrl + "/rest/visa/history?limit=20";
        String lastBody = "";

        while (System.nanoTime() < deadline) {
            IntegrationTestEnvironment.HttpResponse response = request("GET", url, "Token " + token, null, null);
            lastBody = response.status + " " + response.body;

            if (response.status == 200
                && response.body.contains("\"branchName\":\"" + branch + "\"")
                && response.body.contains("\"ticket\":\"" + ticket + "\"")
                && response.body.contains("\"prNum\":" + prNum)
                && response.body.contains("\"commentTargets\":\"JIRA,GITHUB\"")
                && response.body.contains("\"wasEverObserved\":true"))
                return;

            Thread.sleep(500);
        }

        throw new IllegalStateException("Requested visa did not appear in visa history for " + branch
            + ", last status: " + lastBody);
    }

    /** */
    private static String waitForProcess(long processId, String token) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        String url = env.botUrl + "/rest/process/status?id=" + processId;
        String lastBody = "";

        while (System.nanoTime() < deadline) {
            IntegrationTestEnvironment.HttpResponse response = request("GET", url, "Token " + token, null, null);
            lastBody = response.status + " " + response.body;

            if (response.status == 200 && FINISHED_PROCESS.matcher(response.body).find())
                return response.body;

            Thread.sleep(250);
        }

        throw new IllegalStateException("Process did not finish: " + processId + ", last status: " + lastBody);
    }

    /** */
    private static String runTestOnlyAction(String token, String action, long processId) throws Exception {
        IntegrationTestEnvironment.HttpResponse start = request("POST", env.botUrl
            + "/rest/__test__/bot/" + action + "?serverId=apache&processId=" + processId,
            "Token " + token, null, null);

        assertEquals(start.body, 200, start.status);
        assertTrue(start.body, start.body.contains("\"queued\":true"));

        return waitForProcess(processId, token);
    }

    /** */
    private static void authorizeServer(String token) throws Exception {
        IntegrationTestEnvironment.HttpResponse response = request("POST", env.botUrl + "/rest/user/authorize",
            "Token " + token, null, null);

        assertEquals(response.body, 200, response.status);
        assertTrue(response.body, response.body.contains("\"authorizedState\":true"));
    }

    /** */
    private static void triggerObservedVisa(String token, long processId, String branch, String suiteId, String ticket,
        int prNum, boolean commentOnlyIfNoBlockers) throws Exception {
        IntegrationTestEnvironment.HttpResponse trigger = request("GET", env.botUrl + "/rest/build/triggerBuildsAsync"
            + "?srvCode=apache"
            + "&branchName=" + enc(branch)
            + "&parentSuiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&suiteIdList=" + enc(suiteId)
            + "&top=false"
            + "&observe=true"
            + "&ticketId=" + enc(ticket)
            + "&comment=" + enc("JIRA,GITHUB")
            + "&prNum=" + prNum
            + "&commentOnlyIfNoBlockers=" + commentOnlyIfNoBlockers
            + "&cleanRebuild=false"
            + "&processId=" + processId,
            "Token " + token, null, null);

        assertEquals(trigger.body, 200, trigger.status);
        assertTrue(trigger.body, trigger.body.contains("Trigger process started"));
        waitForProcess(processId, token);
    }

    /** */
    private static String refreshRunningVisas(String token, long processId) throws Exception {
        IntegrationTestEnvironment.HttpResponse response = request("GET", env.botUrl
            + "/rest/visa/running?limit=100&processId=" + processId,
            "Token " + token, null, null);

        assertEquals(response.body, 200, response.status);
        waitForProcess(processId, token);

        IntegrationTestEnvironment.HttpResponse refreshed = request("GET", env.botUrl
            + "/rest/visa/running?limit=100",
            "Token " + token, null, null);

        assertEquals(refreshed.body, 200, refreshed.status);

        return refreshed.body;
    }

    /** */
    private static String runPrBuildRefRecheck(String token, long processId, String suiteId, String branch)
        throws Exception {
        IntegrationTestEnvironment.HttpResponse start = request("POST", env.botUrl
            + "/rest/pr/actualizeBuildRefs?serverId=apache"
            + "&suiteId=" + enc(suiteId)
            + "&branchForTc=" + enc(branch)
            + "&processId=" + processId,
            "Token " + token, null, null);

        assertEquals(start.body, 200, start.status);
        assertTrue(start.body, start.body.contains("TeamCity build ref recheck queued"));

        String status = waitForProcess(processId, token);

        assertTrue(status, status.contains("\"kind\":\"teamcityBuildRefRecheck\""));
        assertTrue(status, status.contains("TeamCity build reference rechecked"));

        return status;
    }

    /** */
    private static String commentBuildAnalysis(String token, long processId, String branch, String ticket, int prNum)
        throws Exception {
        IntegrationTestEnvironment.HttpResponse start = request("GET", env.botUrl
            + "/rest/build/commentBuildAnalysis?serverId=apache"
            + "&branchName=" + enc(branch)
            + "&suiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&ticketId=" + enc(ticket)
            + "&comment=" + enc("JIRA,GITHUB")
            + "&prNum=" + prNum
            + "&commentOnlyIfNoBlockers=false"
            + "&processId=" + processId,
            "Token " + token, null, null);

        assertEquals(start.body, 200, start.status);
        assertTrue(start.body, start.body.contains("Comment process started"));

        String status = waitForProcess(processId, token);

        assertTrue(status, status.contains("\"kind\":\"commentBuildAnalysis\""));
        assertTrue(status, !status.contains("wasn't commented"));

        return status;
    }

    /** */
    private static void assertServiceCommentContains(String service, String url, String auth, String... expected)
        throws Exception {
        IntegrationTestEnvironment.HttpResponse response = request("GET", url, auth, null, null);

        assertEquals(response.body, 200, response.status);

        for (String fragment : expected)
            assertTrue(service + " comment should contain " + fragment + ": " + response.body,
                response.body.contains(fragment));
    }

    /** */
    private static void waitForServiceCommentContains(String service, String url, String auth, String... expected)
        throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        String lastBody = "";

        while (System.nanoTime() < deadline) {
            IntegrationTestEnvironment.HttpResponse response = request("GET", url, auth, null, null);
            lastBody = response.status + " " + response.body;

            boolean found = response.status == 200;

            for (String fragment : expected)
                found &= response.body.contains(fragment);

            if (found)
                return;

            Thread.sleep(500);
        }

        throw new IllegalStateException(service + " comment was not found at " + url + ", last response: " +
            lastBody);
    }

    /** */
    private static void waitForObservedVisaComments(String token, long processBase, String branch, String ticket,
        int prNum) throws Exception {
        String jiraUrl = env.jiraUrl + "/rest/api/2/issue/" + ticket + "/comment";
        String githubUrl = env.githubUrl + "/repos/apache/ignite/issues/" + prNum + "/comments";
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        long processId = processBase;
        String lastJira = "";
        String lastGithub = "";

        while (System.nanoTime() < deadline) {
            runBuildObserver(token, processId++);

            IntegrationTestEnvironment.HttpResponse jira = request("GET", jiraUrl, "Bearer jira-test-token", null,
                null);
            IntegrationTestEnvironment.HttpResponse github = request("GET", githubUrl, "Bearer CAFEBABE", null,
                null);

            lastJira = jira.status + " " + jira.body;
            lastGithub = github.status + " " + github.body;

            if (jira.status == 200 && github.status == 200
                && jira.body.contains("tcbot-analysis-comment") && jira.body.contains(branch)
                && github.body.contains("tcbot-analysis-comment") && github.body.contains(branch))
                return;

            Thread.sleep(500);
        }

        throw new IllegalStateException("Observed visa comments were not found for " + branch
            + ", last JIRA response: " + lastJira + ", last GitHub response: " + lastGithub);
    }

    /** */
    private static String runBuildObserver(String token, long processId) throws Exception {
        IntegrationTestEnvironment.HttpResponse start = request("POST", env.botUrl
            + "/rest/__test__/bot/run-build-observer?processId=" + processId,
            "Token " + token, null, null);

        assertEquals(start.body, 200, start.status);
        assertTrue(start.body, start.body.contains("\"queued\":true"));

        String status = waitForProcess(processId, token);

        assertTrue(status, status.contains("Checked "));

        return status;
    }

    /** */
    private static String waitForVisaBlockers(String token, String branch, int blockers) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        String url = env.botUrl + "/rest/visa/visaStatus?serverId=apache"
            + "&suiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&tcBranch=" + enc(branch);
        String expected = "\"blockers\":" + blockers;
        String lastBody = "";

        while (System.nanoTime() < deadline) {
            IntegrationTestEnvironment.HttpResponse response = request("GET", url, "Token " + token, null, null);
            lastBody = response.status + " " + response.body;

            if (response.status == 200 && response.body.contains(expected))
                return response.body;

            Thread.sleep(500);
        }

        throw new IllegalStateException("Visa blockers were not calculated for " + branch + ", last status: "
            + lastBody);
    }

    /** */
    private static String enc(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
