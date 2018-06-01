package org.apache.ignite.ci.issue;

import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.issue.EventTemplates;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.junit.Test;

import static org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence.STATUS_SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
}