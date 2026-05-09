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

package org.apache.ignite.tcbot.engine.build;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestFailuresAiPromptBuilderTest {
    @Test
    public void restMaxDetailsCharsUsesHardCap() {
        assertEquals(TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS,
            TestFailuresAiPromptBuilder.restMaxDetailsChars(null));
        assertEquals(TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS,
            TestFailuresAiPromptBuilder.restMaxDetailsChars(0));
        assertEquals(TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS,
            TestFailuresAiPromptBuilder.restMaxDetailsChars(-1));
        assertEquals(TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS,
            TestFailuresAiPromptBuilder.restMaxDetailsChars(TestFailuresAiPromptBuilder.DFLT_MAX_DETAILS_CHARS + 1));
        assertEquals(1024, TestFailuresAiPromptBuilder.restMaxDetailsChars(1024));
    }

    @Test
    public void logSearchRegexIncludesExceptionAndAssertionMessages() {
        TestFailuresAiPromptBuilder builder = new TestFailuresAiPromptBuilder(null);

        String regex = builder.logSearchRegex(Arrays.asList(
            "java.lang.IllegalStateException: Node left topology unexpectedly at " +
                "org.apache.ignite.internal.Test.test(Test.java:42)",
            "Test failed. junit.framework.AssertionFailedError: expected cache size to be 42 at " +
                "junit.framework.Assert.fail(Assert.java:57)",
            "java.lang.AssertionError: Partition did not rebalance",
            "Caused by: org.apache.ignite.IgniteCheckedException",
            "java.lang.OutOfMemoryError",
            "ava.lang.IllegalStateException: Getting affinity for too old topology version that is already out of " +
                "history (try to increase 'IGNITE_AFFINITY_HISTORY_SIZE' system property)"
        ));

        assertTrue(regex.contains("IllegalStateException"));
        assertTrue(regex.contains("Node left topology unexpectedly"));
        assertTrue(regex.contains("AssertionFailedError"));
        assertTrue(regex.contains("expected cache size to be 42"));
        assertTrue(regex.contains("Partition did not rebalance"));
        assertTrue(regex.contains("IgniteCheckedException"));
        assertTrue(regex.contains("OutOfMemoryError"));
        assertTrue(regex.contains("AssertionError"));
        assertTrue(regex.contains("Getting affinity for too old topology version"));
        assertTrue(regex.contains("IGNITE_AFFINITY_HISTORY_SIZE"));
        assertFalse(regex.contains("Test\\.java:42"));
    }

    @Test
    public void fastSummaryExtractsThrowableAndNearestProjectFrame() {
        TestFailuresAiPromptBuilder builder = new TestFailuresAiPromptBuilder(null);
        String details = "java.lang.IllegalStateException: Getting affinity for too old topology version " +
            "that is already out of history\n" +
            "    at org.apache.ignite.internal.processors.cache.GridCacheAffinityManager.cachedAffinity" +
            "(GridCacheAffinityManager.java:188)\n" +
            "    at org.apache.ignite.internal.processors.cache.CacheEventWithTxLabelTest.prepareCache" +
            "(CacheEventWithTxLabelTest.java:402)\n";

        String summary = builder.fastSummary("Cache 4", "master",
            "Cache 4: org.apache.ignite.internal.processors.cache.CacheEventWithTxLabelTest.testPassTxLabelInCashEventForAllCases",
            1, details, 341);

        assertTrue(summary.contains("Single test failure in Cache 4 on master."));
        assertTrue(summary.contains("within ~341 ms"));
        assertTrue(summary.contains("Exception: IllegalStateException: Getting affinity for too old topology version"));
        assertTrue(summary.contains("Nearest project frame: CacheEventWithTxLabelTest.prepareCache:402."));
        assertTrue(summary.contains("Likely area: affinity/topology/cache/near prepareCache."));
    }

    @Test
    public void detailsTailsForPromptKeepsOnlyStdoutAndStderrTails() {
        TestFailuresAiPromptBuilder builder = new TestFailuresAiPromptBuilder(null);
        StringBuilder details = new StringBuilder();

        details.append("junit.framework.AssertionFailedError: expected:<11> but was:<12>\n");
        details.append("    at org.apache.ignite.Test.test(Test.java:42)\n");
        details.append("------- Stdout: -------\n");

        for (int i = 0; i < 30; i++)
            details.append("stdout line ").append(i).append('\n');

        details.append("------- Stderr: -------\n");

        for (int i = 0; i < 3; i++)
            details.append("stderr line ").append(i).append('\n');

        String tails = builder.detailsTailsForPrompt(details.toString());

        assertFalse(tails.contains("expected:<11> but was:<12>"));
        assertFalse(tails.contains("stdout line 0"));
        assertTrue(tails.contains("... skipped 5 earlier lines ..."));
        assertTrue(tails.contains("stdout line 5"));
        assertTrue(tails.contains("stdout line 29"));
        assertTrue(tails.contains("stderr line 0"));
        assertTrue(tails.contains("stderr line 2"));
    }

    @Test
    public void relevantStdoutStderrSummaryKeepsHighValueSignals() {
        TestFailuresAiPromptBuilder builder = new TestFailuresAiPromptBuilder(null);
        String details = "junit.framework.AssertionFailedError: expected:<11> but was:<12>\n" +
            "    at org.apache.ignite.internal.WalDeletionArchiveAbstractTest.test(WalDeletionArchiveAbstractTest.java:212)\n" +
            "------- Stdout: -------\n" +
            "Topology snapshot [ver=1, locNode=abc]\n" +
            "Grid stopped.\n";

        String summary = builder.relevantStdoutStderrSummary(details, 341);

        assertTrue(summary.contains("Test started and failed within ~341 ms."));
        assertTrue(summary.contains("Assertion at WalDeletionArchiveAbstractTest.java:212."));
        assertTrue(summary.contains("Ignite node started and stopped normally."));
        assertTrue(summary.contains("No timeout or crash signal found in included details."));
        assertFalse(summary.contains("expected:<11> but was:<12>"));
    }
}
