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

package org.apache.ignite.tcbot.engine.testfixes;

import java.util.Collection;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for test-fix source text matching.
 */
public class TestFixesServiceTest {
    /** Test suite/class name. */
    private static final String SUITE = "SqlPlanHistoryIntegrationTest";

    /** Test method name. */
    private static final String TEST = "cacheQuery";

    /** Short test name. */
    private static final String SHORT_TEST = SUITE + "." + TEST;

    /** Full TeamCity test name. */
    private static final String FULL_TEST =
        "org.apache.ignite.internal.processors.query.calcite.integration." + SHORT_TEST;

    /** Cache run configuration. */
    private static final TestFixesService.TestFixCandidate CACHE8 = candidate("IgniteTests24Java8_Cache8");

    /** Queries run configuration. */
    private static final TestFixesService.TestFixCandidate QUERIES1 = candidate("IgniteTests24Java8_Queries1");

    /** */
    @Test
    public void jiraTextMatchesSuiteDotTestInAllRunConfigurations() {
        Collection<TestFixesService.TestFixCandidate> matches = jiraMatches("Fix " + SHORT_TEST + " flakiness");

        assertEquals(2, matches.size());
        assertTrue(matches.contains(CACHE8));
        assertTrue(matches.contains(QUERIES1));
    }

    /** */
    @Test
    public void jiraTextMatchesSuiteHashTestInAllRunConfigurations() {
        Collection<TestFixesService.TestFixCandidate> matches = jiraMatches("Fix " + SUITE + "#" + TEST);

        assertEquals(2, matches.size());
        assertTrue(matches.contains(CACHE8));
        assertTrue(matches.contains(QUERIES1));
    }

    /** */
    @Test
    public void jiraTextMatchesQualifiedRunConfigurationOnly() {
        Collection<TestFixesService.TestFixCandidate> matches = jiraMatches(
            "Fix IgniteTests24Java8_Cache8::" + SUITE + "#" + TEST);

        assertEquals(1, matches.size());
        assertTrue(matches.contains(CACHE8));
        assertFalse(matches.contains(QUERIES1));
    }

    /** */
    @Test
    public void prTextMatchesSuiteDotTestInAllRunConfigurations() {
        Collection<TestFixesService.TestFixCandidate> matches = prMatches(
            "IGNITE-1 Fix test",
            "This PR fixes " + SHORT_TEST + " in master.");

        assertEquals(2, matches.size());
        assertTrue(matches.contains(CACHE8));
        assertTrue(matches.contains(QUERIES1));
    }

    /** */
    @Test
    public void prTextMatchesSuiteHashTestInAllRunConfigurations() {
        Collection<TestFixesService.TestFixCandidate> matches = prMatches(
            "IGNITE-1 Fix " + SUITE + "#" + TEST,
            "No comments from code review should be needed.");

        assertEquals(2, matches.size());
        assertTrue(matches.contains(CACHE8));
        assertTrue(matches.contains(QUERIES1));
    }

    /** */
    @Test
    public void prTextMatchesQualifiedRunConfigurationOnly() {
        Collection<TestFixesService.TestFixCandidate> matches = prMatches(
            "IGNITE-1 Fix IgniteTests24Java8_Cache8::" + SUITE + "#" + TEST,
            "The same test exists in another suite, but this PR names the run configuration.");

        assertEquals(1, matches.size());
        assertTrue(matches.contains(CACHE8));
        assertFalse(matches.contains(QUERIES1));
    }

    /** */
    @Test
    public void partialWordsDoNotMatch() {
        Collection<TestFixesService.TestFixCandidate> matches = jiraMatches(
            "Fix Not" + SHORT_TEST + " and " + SHORT_TEST + "Extra");

        assertTrue(matches.isEmpty());
    }

    /** */
    @Test
    public void suiteNameAloneDoesNotMatch() {
        Collection<TestFixesService.TestFixCandidate> matches = jiraMatches(
            "Fix " + SUITE + " instability in master");

        assertTrue(matches.isEmpty());
    }

    /** */
    @Test
    public void garbageTeamcityNamesAreNotPlausibleTests() {
        assertFalse(TestFixesService.isPlausibleTestName("0.0\\\\, Culture=neutral\\\\, PublicKeyToken=123\\\\]\\\\]\")"));
        assertFalse(TestFixesService.isPlausibleTestName("Configuration.ConfigurationException: x)"));
        assertFalse(TestFixesService.isPlausibleTestName("Data.PropertyCollection)"));
        assertFalse(TestFixesService.isPlausibleTestName("Log.CustomLoggerTest+CustomEnum)"));

        assertTrue(TestFixesService.isPlausibleTestName("OpenCensusSqlNativeTracingTest.testNextPageRequestFailure"));
        assertTrue(TestFixesService.isPlausibleTestName("SqlPlanHistoryIntegrationTest.cacheQuery"));
    }

    /**
     * @param text JIRA summary plus description.
     */
    private static Collection<TestFixesService.TestFixCandidate> jiraMatches(String text) {
        return TestFixesService.matchingCandidates(text, candidates());
    }

    /**
     * @param title PR title.
     * @param body PR body.
     */
    private static Collection<TestFixesService.TestFixCandidate> prMatches(String title, String body) {
        return TestFixesService.matchingCandidates(title + "\n" + body, candidates());
    }

    /** */
    private static List<TestFixesService.TestFixCandidate> candidates() {
        return List.of(CACHE8, QUERIES1);
    }

    /**
     * @param suiteId Run configuration id.
     */
    private static TestFixesService.TestFixCandidate candidate(String suiteId) {
        return new TestFixesService.TestFixCandidate("master", suiteId, SUITE, FULL_TEST, SHORT_TEST,
            "https://ci.example/buildConfiguration/" + suiteId + "?branch=%3Cdefault%3E");
    }
}
