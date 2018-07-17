package org.apache.ignite.ci.issue;

import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.issue.EventTemplates;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.junit.Test;

import static org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence.STATUS_SUCCESS;
import static org.junit.Assert.*;

public class DetectingFailureTest {
    @Test
    public void detectFirstFailure() {
        RunStat stat = new RunStat("");

        TestOccurrence occurrence = new TestOccurrence().setStatus(STATUS_SUCCESS);

        for (int i = 0; i < 5; i++)
            stat.addTestRunToLatest(occurrence.setId("id:10231,build:(id:" + (113 + i) + ")"));

        int firstFailedBuildId = 150;

        occurrence.status = "FAILED";
        for (int i = 0; i < 5; i++)
            stat.addTestRunToLatest(occurrence.setId("id:10231,build:(id:" + (firstFailedBuildId + i) + ")"));

        RunStat.TestId testId = stat.detectTemplate(EventTemplates.newFailure);

        assertNotNull(testId);
        assertEquals(firstFailedBuildId, testId.getBuildId());

        assertNull(stat.detectTemplate(EventTemplates.fixOfFailure));
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

        RunStat.TestId testId = stat.detectTemplate(EventTemplates.newCriticalFailure);

        System.out.println(stat.getLatestRunResults());
        assertNotNull(testId);
        assertEquals(firstFailedBuildId, testId.getBuildId());
    }

    @Test
    public void detectFlakyTest() {
        RunStat stat = new RunStat("");

        TestOccurrence occurrence = new TestOccurrence().setStatus(STATUS_SUCCESS);


        final int[] ints = {0, 0, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0};

        for (int i = 0; i < 50; i++) {
            occurrence.status = ints[i] == 0 ? Build.STATUS_SUCCESS : "FAILURE";

            stat.addTestRunToLatest(occurrence.setId("id:10231,build:(id:" + (100 + i) + ")"));
        }

        int firstFailedBuildId = 150;

        occurrence.status = "FAILED";
        for (int i = 0; i < 4; i++)
            stat.addTestRunToLatest(occurrence.setId("id:10231,build:(id:" + (firstFailedBuildId + i) + ")"));



        assertTrue(stat.isFlaky());

        System.out.println(stat.getLatestRunResults());
        RunStat.TestId testId = stat.detectTemplate(EventTemplates.newFailure);
        assertNotNull(testId);
        assertEquals(firstFailedBuildId, testId.getBuildId());
    }
}