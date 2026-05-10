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

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.ignite.tcbot.integration.IntegrationTestEnvironment.request;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the executable contract of the integration-test harness.
 */
public class IntegrationHarnessContractTest {
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
    public void apacheIgniteHappyPathHarnessHasProductionWarAndRealisticEmulators() throws Exception {
        Path war = Path.of(System.getProperty("tcbot.integration.war"));

        assertTrue("Integration tests must run against production WAR: " + war, Files.isRegularFile(war));

        assertEquals(200, request("GET", env.githubUrl + "/health", null, null, null).status);
        assertEquals(200, request("GET", env.jiraUrl + "/health", null, null, null).status);
        assertEquals(200, request("GET", env.teamcityUrl + "/health", null, null, null).status);
        IntegrationTestEnvironment.HttpResponse nonAdminTcUser = request("GET",
            env.teamcityUrl + "/app/rest/users/current", env.basicAuth("nonadmin", "nonadmin"), null, null);

        assertEquals(200, nonAdminTcUser.status);
        assertTrue(nonAdminTcUser.body.contains("username=\"nonadmin\""));
        assertFalse(nonAdminTcUser.body.contains("IGNITE_COMMITTER"));

        String nonAdminToken = env.login("nonadmin", "nonadmin");
        IntegrationTestEnvironment.HttpResponse nonAdminBotUser = request("GET",
            env.botUrl + "/rest/user/currentUserName", "Token " + nonAdminToken, null, null);

        assertEquals(200, nonAdminBotUser.status);
        assertTrue(nonAdminBotUser.body.contains("\"admin\":false"));
        assertTrue(nonAdminBotUser.body.contains("\"userAdmin\":false"));

        assertEquals(200, request("GET", env.githubUrl + "/repos/apache/ignite/branches",
            "token CAFEBABE", null, null).status);
        assertEquals(401, request("GET", env.githubUrl + "/repos/apache/ignite/branches",
            "token wrong-token", null, null).status);
        assertEquals(404, request("GET", env.githubUrl + "/repos/apache/ignite/pulls/99999",
            "Bearer CAFEBABE", null, null).status);
        IntegrationTestEnvironment.HttpResponse seededPr = request("GET", env.githubUrl
            + "/repos/apache/ignite/pulls/12005", "Bearer CAFEBABE", null, null);

        assertEquals(200, seededPr.status);
        assertTrue(seededPr.body.contains("IGNITE-20005"));
        assertTrue(seededPr.body.contains(env.githubUrl + "/apache/ignite/pull/12005"));
        assertEquals(200, request("GET", env.githubUrl + "/apache/ignite/pull/12005",
            null, null, null).status);
        IntegrationTestEnvironment.HttpResponse unhappyPr = request("GET", env.githubUrl
            + "/repos/apache/ignite/pulls/12006", "Bearer CAFEBABE", null, null);

        assertEquals(200, unhappyPr.status);
        assertTrue(unhappyPr.body.contains("IGNITE-20006"));
        assertTrue(unhappyPr.body.contains("unhappy-contributor"));

        IntegrationTestEnvironment.HttpResponse pr = request("POST", env.githubUrl + "/__test__/github/create-pr",
            null, "application/json", "{\"issue\":\"IGNITE-20003\",\"number\":12004}");

        assertEquals(201, pr.status);
        assertTrue(pr.body.contains("\"number\": 12004"));

        IntegrationTestEnvironment.HttpResponse issue = request("POST", env.jiraUrl + "/__test__/jira/create-issue",
            null, "application/json", "{\"key\":\"IGNITE-20003\",\"summary\":\"Integration test generated issue\"}");

        assertEquals(201, issue.status);
        assertEquals(200, request("GET", env.jiraUrl + "/rest/api/2/issue/IGNITE-20003",
            "Bearer jira-test-token", null, null).status);
        assertEquals(200, request("GET", env.jiraUrl
            + "/rest/api/2/search?jql=project%3DIGNITE+order+by+updated+DESC&fields=status,summary&maxResults=100",
            "Bearer jira-test-token", null, null).status);
        assertEquals(200, request("GET", env.jiraUrl
            + "/rest/api/2/search?jql=project%3DIGNITE+order+by+updated+DESC&fields=status,summary&maxResults=100",
            "Basic ", null, null).status);
        assertEquals(200, request("GET", env.jiraUrl + "/rest/api/2/issue/IGNITE-20005",
            "Bearer jira-test-token", null, null).status);
        assertEquals(200, request("GET", env.jiraUrl + "/browse/IGNITE-20005",
            null, null, null).status);
        assertEquals(401, request("GET", env.jiraUrl
            + "/rest/api/2/search?jql=project%3DIGNITE+order+by+updated+DESC&fields=status,summary&maxResults=100",
            "Basic wrong-token", null, null).status);

        IntegrationTestEnvironment.HttpResponse build = request("POST", env.teamcityUrl + "/app/rest/buildQueue",
            env.basicAuth(), "application/xml",
            "<build branchName=\"pull/12004/head\"><buildType id=\"IgniteTests24Java17_RunAll\"/></build>");

        assertEquals(200, build.status);
        assertTrue(build.body, build.body.contains("state=\"queued\"") || build.body.contains("state=\"running\""));
        IntegrationTestEnvironment.HttpResponse seededBuild = request("GET",
            env.teamcityUrl + "/app/rest/latest/builds/id:800101", env.basicAuth(), null, null);

        assertEquals(200, seededBuild.status);
        assertTrue(seededBuild.body.contains("branchName=\"pull/12005/head\""));
        assertTrue(seededBuild.body.contains("status=\"SUCCESS\""));
        assertTrue(seededBuild.body.contains("state=\"finished\""));
        IntegrationTestEnvironment.HttpResponse unhappyBuild = request("GET",
            env.teamcityUrl + "/app/rest/latest/builds/id:800201", env.basicAuth(), null, null);

        assertEquals(200, unhappyBuild.status);
        assertTrue(unhappyBuild.body.contains("branchName=\"pull/12006/head\""));
        assertTrue(unhappyBuild.body.contains("status=\"FAILURE\""));
        IntegrationTestEnvironment.HttpResponse unhappyTests = request("GET", env.teamcityUrl
            + "/app/rest/latest/testOccurrences?locator=build:(id:800201)&fields=testOccurrence(id)",
            env.basicAuth(), null, null);

        assertEquals(200, unhappyTests.status);
        assertTrue(unhappyTests.body.contains("org.apache.ignite.sql.SqlRetryTest.testRetryOnTopologyChange"));
        assertTrue(unhappyTests.body.contains("status=\"FAILURE\""));
        IntegrationTestEnvironment.HttpResponse masterGreen = request("GET",
            env.teamcityUrl + "/app/rest/latest/builds/id:800301", env.basicAuth(), null, null);

        assertEquals(200, masterGreen.status);
        assertTrue(masterGreen.body.contains("branchName=\"&lt;default&gt;\""));
        assertTrue(masterGreen.body.contains("status=\"SUCCESS\""));

        IntegrationTestEnvironment.HttpResponse compilationFailedBuild = request("GET",
            env.teamcityUrl + "/app/rest/latest/builds/id:800401", env.basicAuth(), null, null);

        assertEquals(200, compilationFailedBuild.status);
        assertTrue(compilationFailedBuild.body.contains("branchName=\"pull/12007/head\""));
        assertTrue(compilationFailedBuild.body.contains("buildTypeId=\"IgniteTests24Java17_Build\""));
        assertTrue(compilationFailedBuild.body.contains("buildTypeId=\"IgniteTests24Java17_Cache1\""));
        assertTrue(compilationFailedBuild.body.contains("status=\"FAILURE\""));
        assertTrue(compilationFailedBuild.body.contains("status=\"UNKNOWN\""));

        IntegrationTestEnvironment.HttpResponse compilationProblem = request("GET", env.teamcityUrl
            + "/app/rest/latest/problemOccurrences?locator=build:(id:800402)&fields=problemOccurrence(id)",
            env.basicAuth(), null, null);

        assertEquals(200, compilationProblem.status);
        assertTrue(compilationProblem.body.contains("type=\"TC_COMPILATION_ERROR\""));
        assertTrue(compilationProblem.body.contains("Compilation failed during Build stage"));

        IntegrationTestEnvironment.HttpResponse masterTests = request("GET", env.teamcityUrl
            + "/app/rest/latest/testOccurrences?locator=build:(id:800301)&fields=testOccurrence(id)",
            env.basicAuth(), null, null);

        assertEquals(200, masterTests.status);
        assertTrue(masterTests.body.contains("org.apache.ignite.sql.SqlRetryTest.testRetryOnTopologyChange"));
        assertTrue(masterTests.body.contains("status=\"SUCCESS\""));
        assertEquals(200, request("GET", env.teamcityUrl + "/app/rest/latest/projects",
            env.basicAuth(), null, null).status);
        assertEquals(200, request("GET", env.teamcityUrl + "/app/rest/latest/projects/ApacheIgnite",
            env.basicAuth(), null, null).status);
        IntegrationTestEnvironment.HttpResponse runAllBuildType = request("GET",
            env.teamcityUrl + "/app/rest/latest/buildTypes/id:IgniteTests24Java17_RunAll",
            env.basicAuth(), null, null);

        assertEquals(200, runAllBuildType.status);
        assertTrue(runAllBuildType.body.contains("<settings count="));
        assertTrue(runAllBuildType.body.contains("<parameters count="));
        assertTrue(runAllBuildType.body.contains("<snapshot-dependencies count=\"5\""));
        assertTrue(runAllBuildType.body.contains("source-buildType id=\"IgniteTests24Java17_Build\""));
        assertEquals(200, request("GET",
            env.teamcityUrl + "/buildConfiguration/IgniteTests24Java17_RunAll",
            null, null, null).status);

        IntegrationTestEnvironment.HttpResponse completed = request("POST",
            env.teamcityUrl + "/__test__/teamcity/complete-build", null, "application/json",
            "{\"buildId\":900001,\"status\":\"FAILURE\",\"problems\":[\"deterministic blocker\"]}");

        assertEquals(200, completed.status);
        assertTrue(completed.body.contains("\"status\": \"FAILURE\""));
        assertEquals(200, request("GET", env.teamcityUrl
            + "/app/rest/latest/problemOccurrences?locator=build:(id:900001)&fields=problemOccurrence(id)",
            env.basicAuth(), null, null).status);
    }
}
