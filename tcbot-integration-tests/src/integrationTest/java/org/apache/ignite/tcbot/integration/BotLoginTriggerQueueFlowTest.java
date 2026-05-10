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
    private static final int FIRST_TRIGGERED_BUILD_ID = 900001;

    /** */
    private static final Pattern FINISHED_PROCESS = Pattern.compile("\"finished\"\\s*:\\s*[1-9][0-9]*");

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
    public void loginThenTriggerBuildQueueThenCompleteBuildInEmulator() throws Exception {
        IntegrationTestEnvironment.HttpResponse primaryServer = request("GET",
            env.botUrl + "/rest/login/primaryServerData", null, null, null);

        assertEquals(primaryServer.body, 200, primaryServer.status);
        assertTrue(primaryServer.body.contains("127.0.0.1:" + env.teamcityPort));

        String token = env.login();
        String processId = "700000001";
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

        waitForBuildQueued();

        IntegrationTestEnvironment.HttpResponse completed = request("POST", env.teamcityUrl
            + "/__test__/teamcity/complete-build", null, "application/json",
            "{\"buildId\":" + FIRST_TRIGGERED_BUILD_ID + ",\"status\":\"SUCCESS\",\"tests\":[]}");

        assertEquals(completed.body, 200, completed.status);
        assertTrue(completed.body.contains("\"state\": \"finished\""));
        assertTrue(completed.body.contains("\"status\": \"SUCCESS\""));
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
        runPrBuildRefsRefresh(token, 700000005L);

        IntegrationTestEnvironment.HttpResponse prResults = request("GET", env.botUrl
            + "/rest/pr/results?serverId=apache"
            + "&suiteId=" + enc("IgniteTests24Java17_RunAll")
            + "&branchForTc=" + enc("pull/12006/head")
            + "&action=" + enc("Latest"),
            "Token " + token, null, null);

        assertEquals(prResults.body, 200, prResults.status);
        assertTrue(prResults.body, prResults.body.contains("org.apache.ignite.sql.SqlRetryTest.testRetryOnTopologyChange"));
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
    private static void waitForBuildQueued() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        String url = env.teamcityUrl + "/app/rest/latest/builds/id:" + FIRST_TRIGGERED_BUILD_ID;

        while (System.nanoTime() < deadline) {
            IntegrationTestEnvironment.HttpResponse response = request("GET", url, env.basicAuth(), null, null);

            if (response.status == 200 && response.body.contains("state=\"running\""))
                return;

            Thread.sleep(250);
        }

        throw new IllegalStateException("Build was not queued by bot in emulator");
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
    private static String runPrBuildRefsRefresh(String token, long processId) throws Exception {
        IntegrationTestEnvironment.HttpResponse start = request("POST", env.botUrl
            + "/rest/pr/actualizeBuildRefs?serverId=apache&processId=" + processId,
            "Token " + token, null, null);

        assertEquals(start.body, 200, start.status);
        assertTrue(start.body, start.body.contains("TeamCity build refs refresh queued"));

        String status = waitForProcess(processId, token);

        assertTrue(status, status.contains("\"kind\":\"teamcityBuildRefsRefresh\""));
        assertTrue(status, status.contains("TeamCity build references refreshed"));

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
