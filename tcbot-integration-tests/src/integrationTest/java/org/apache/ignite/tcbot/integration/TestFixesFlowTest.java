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

import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.ignite.tcbot.integration.IntegrationTestEnvironment.request;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Black-box coverage for test-fix matching.
 */
public class TestFixesFlowTest {
    /** */
    private static final Pattern FINISHED_PROCESS = Pattern.compile("\"finished\"\\s*:\\s*[1-9][0-9]*");

    /** */
    private static IntegrationTestEnvironment env;

    /** */
    @BeforeClass
    public static void startSuite() throws Exception {
        env = IntegrationTestEnvironment.get();
    }

    /** */
    @Before
    public void resetEmulators() throws Exception {
        env.resetEmulators();
    }

    /** */
    @Test
    public void testFixRefreshReturnsOnlyOpenAndResolvedJiraTasksForMatchedTests() throws Exception {
        String token = env.login();

        createIssue("IGNITE-20101",
            "Fix SqlRetryTest.testRetryOnTopologyChange flakiness",
            "Open",
            "The fix is being prepared for SqlRetryTest.testRetryOnTopologyChange.",
            null);
        createIssue("IGNITE-20102",
            "Fix CacheRebalanceTest.testHistoricalRebalance instability",
            "Resolved",
            "Resolved test CacheRebalanceTest.testHistoricalRebalance after master history check.",
            "2026-05-09T12:00:00.000+0000");

        runTestOnlyAction(token, "refresh-jira", 710000001L);
        runTestOnlyAction(token, "refresh-teamcity-builds", 710000002L);

        IntegrationTestEnvironment.HttpResponse master = request("GET",
            env.botUrl + "/rest/tracked/results?branch=master", "Token " + token, null, null);

        assertEquals(master.body, 200, master.status);
        assertTrue(master.body, master.body.contains("SqlRetryTest.testRetryOnTopologyChange"));
        assertTrue(master.body, master.body.contains("CacheRebalanceTest.testHistoricalRebalance"));

        startTestFixRefresh(token, 710000003L);
        waitForProcess(710000003L, token);

        String rows = waitForTestFixRows(token, "IGNITE-20101", "IGNITE-20102");

        assertEquals(rows, 1, count(rows, "\"text\":\"IGNITE-20101\""));
        assertEquals(rows, 1, count(rows, "\"text\":\"IGNITE-20102\""));
        assertEquals(rows, 2, count(rows, "\"sourceType\":\"jira\""));
        assertEquals(rows, 0, count(rows, "\"sourceType\":\"github\""));
        assertTrue(rows, rows.contains("\"status\":\"Open\""));
        assertTrue(rows, rows.contains("\"status\":\"Resolved\""));
        assertTrue(rows, rows.contains("\"closedDate\":\"2026-05-09\""));
    }

    /** */
    private static void createIssue(String key, String summary, String status, String description,
        String resolutionDate) throws Exception {
        String body = "{\"key\":\"" + key + "\","
            + "\"summary\":\"" + summary + "\","
            + "\"status\":\"" + status + "\","
            + "\"description\":\"" + description + "\","
            + "\"updated\":\"2026-05-10T10:00:00.000+0000\""
            + (resolutionDate == null ? "" : ",\"resolutiondate\":\"" + resolutionDate + "\"")
            + "}";

        IntegrationTestEnvironment.HttpResponse issue = request("POST", env.jiraUrl + "/__test__/jira/create-issue",
            null, "application/json", body);

        assertEquals(issue.body, 201, issue.status);
    }

    /** */
    private static void startTestFixRefresh(String token, long processId) throws Exception {
        IntegrationTestEnvironment.HttpResponse refresh = request("GET", env.botUrl
            + "/rest/testFixes/refresh?limit=0&processId=" + processId,
            "Token " + token, null, null);

        assertEquals(refresh.body, 200, refresh.status);
    }

    /** */
    private static String waitForTestFixRows(String token, String... expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        String lastBody = "";

        while (System.nanoTime() < deadline) {
            IntegrationTestEnvironment.HttpResponse response = request("GET", env.botUrl
                + "/rest/testFixes/recent?limit=0", "Token " + token, null, null);
            lastBody = response.status + " " + response.body;

            boolean found = response.status == 200;

            for (String item : expected)
                found &= response.body.contains(item);

            if (found)
                return response.body;

            Thread.sleep(500);
        }

        throw new IllegalStateException("Test fix rows were not found, last response: " + lastBody);
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
    private static int count(String text, String needle) {
        int res = 0;
        int start = 0;

        while (start < text.length()) {
            int idx = text.indexOf(needle, start);

            if (idx < 0)
                return res;

            res++;
            start = idx + needle.length();
        }

        return res;
    }

}
