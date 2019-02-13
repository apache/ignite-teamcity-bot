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

package org.apache.ignite.ci.issue;

import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.apache.ignite.ci.analysis.RunStat.ChangesState.UNKNOWN;
import static org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence.STATUS_SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Issue detection test
 */
public class DetectingFailureTest {
    @Test
    public void detectFirstFailure() {
        RunStat stat = new RunStat("");

        TestOccurrence occurrence = new TestOccurrence().setStatus(STATUS_SUCCESS);

        for (int i = 0; i < 5; i++)
            stat.addTestRunToLatest(occurrence.setId(fakeTestId(113 + i)), UNKNOWN);

        occurrence.status = "FAILED";

        int firstFailedBuildId = 150;

        for (int i = 0; i < 5; i++)
            stat.addTestRunToLatest(occurrence.setId(fakeTestId(firstFailedBuildId + i)), UNKNOWN);

        Integer buildId = stat.detectTemplate(EventTemplates.newFailure);

        assertNotNull(buildId);
        assertEquals(firstFailedBuildId, buildId.intValue());

        assertNull(stat.detectTemplate(EventTemplates.fixOfFailure));
    }

    /**
     * @param buildId Build Id.
     */
    @NotNull private String fakeTestId(int buildId) {
        return "id:10231,build:(id:" + buildId + ")";
    }

    @Test
    public void detectSuiteFailure() {
        RunStat stat = new RunStat("");

        Build occurrence = new Build();

        for (int i = 0; i < 5; i++) {
            occurrence.setId(113 + i);
            boolean ok = (int)(Math.random() * 1000) % 2 == 0;
            occurrence.status = ok ? Build.STATUS_SUCCESS : "FAILURE";

            stat.addBuildRun(occurrence);
        }

        int firstFailedBuildId = 150;

        occurrence.status = "FAILED";
        for (int i = 0; i < 4; i++)
            stat.setBuildCriticalError(firstFailedBuildId + i);

        Integer buildId = stat.detectTemplate(EventTemplates.newCriticalFailure);

        System.out.println(stat.getLatestRunResults());
        assertNotNull(buildId);
        assertEquals(firstFailedBuildId, buildId.intValue());
    }

    @Test
    public void detectFlakyTest() {
        RunStat stat = new RunStat("");

        TestOccurrence occurrence = new TestOccurrence().setStatus(STATUS_SUCCESS);

        final int[] ints = {
            0, 0, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 0, 1, 0,
            1, 0, 0, 0, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0};

        for (int i = 0; i < 50; i++) {
            occurrence.status = ints[i] == 0 ? Build.STATUS_SUCCESS : "FAILURE";

            stat.addTestRunToLatest(occurrence.setId(fakeTestId(100 + i)), RunStat.ChangesState.NONE);
        }

        occurrence.status = "FAILED";

        int firstFailedBuildId = 150;
        for (int i = 0; i < 4; i++)
            stat.addTestRunToLatest(occurrence.setId(fakeTestId(firstFailedBuildId + i)), UNKNOWN);

        assertTrue(stat.isFlaky());

        System.out.println(stat.getLatestRunResults());
        Integer buildId = stat.detectTemplate(EventTemplates.newFailure);
        assertNotNull(buildId);
        assertEquals(firstFailedBuildId, buildId.intValue());
    }

    @Test
    public void detectNewContributedTestFailure() {
        RunStat statWithHist = new RunStat("");

        TestOccurrence occurrence = new TestOccurrence().setStatus(STATUS_SUCCESS);

        final int[] results = {0, 0, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 0, 1, 0};

        for (int i = 0; i < results.length; i++) {
            statWithHist.addTestRunToLatest(occurrence
                .setStatus(results[i] == 0 ? Build.STATUS_SUCCESS : "FAILURE")
                .setId(fakeTestId(100 + i)), UNKNOWN);
        }

        occurrence.setStatus("FAILED");

        int firstFailedBuildId = 150;
        for (int i = 0; i < 15; i++)
            statWithHist.addTestRunToLatest(occurrence.setId(fakeTestId(firstFailedBuildId + i)), UNKNOWN);

        assertNull(statWithHist.detectTemplate(EventTemplates.newContributedTestFailure));

        RunStat contributedTestStat = new RunStat("");
        for (int i = 0; i < 5; i++)
            contributedTestStat.addTestRunToLatest(occurrence.setId(fakeTestId(firstFailedBuildId + i)), UNKNOWN);

        Integer buildId = contributedTestStat.detectTemplate(EventTemplates.newContributedTestFailure);
        assertNotNull(buildId);
        assertEquals(firstFailedBuildId, buildId.intValue());
    }

    @Test
    public void detectSuiteFailureIsOnlyOnce() {
        RunStat stat = new RunStat("");

        Build occurrence = new Build();

        int startBuildId = 113;
        int okOrFailedBuildsCnt = 5;
        for (int i = 0; i < okOrFailedBuildsCnt; i++) {
            occurrence.setId(startBuildId + i);
            boolean ok = (int)(Math.random() * 1000) % 2 == 0;
            occurrence.status = ok ? Build.STATUS_SUCCESS : "FAILURE";

            stat.addBuildRun(occurrence);
        }

        int firstFailedBuildId = startBuildId + okOrFailedBuildsCnt;

        int timedOutBuildCnt = 4;
        for (int i = 0; i < timedOutBuildCnt; i++)
            stat.setBuildCriticalError(firstFailedBuildId + i);

        Integer buildId = stat.detectTemplate(EventTemplates.newCriticalFailure);

        assertNotNull(buildId);
        assertEquals(firstFailedBuildId, buildId.intValue());

        for (int i = 0; i < 4; i++)
            stat.setBuildCriticalError(timedOutBuildCnt + firstFailedBuildId + i);

        Integer buildId2 = stat.detectTemplate(EventTemplates.newCriticalFailure);

        System.out.println(stat.getLatestRunResults());
        System.out.println(buildId);
        System.out.println(buildId2);

        assertEquals(buildId, buildId2);

    }
}