package org.apache.ignite.ci.analysis;

import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.junit.Test;

public class RunStatTest {
    @Test
    public void testHistoricalIsExcluded() {

        RunStat stat = new RunStat("");
        TestOccurrence occurrence = new TestOccurrence();
        occurrence.status = "SUCCESS";
        occurrence.setId("id:10231,build:(id:1103529)");
        stat.addTestRunToLatest(occurrence);

        assert stat.getLatestRunResults().contains(0);

        occurrence.status = "FAILED";
        occurrence.setId("id:10231,build:(id:1133529)");
        stat.addTestRunToLatest(occurrence);
        assert stat.getLatestRunResults().contains(0);
        assert stat.getLatestRunResults().contains(1);

        for (int i = 0; i < RunStat.MAX_LATEST_RUNS; i++) {
            occurrence.setId("id:10231,build:(id:" + 1133529 + i + ")");

            stat.addTestRunToLatest(occurrence);
        }

        assert !stat.getLatestRunResults().contains(0) : stat.getLatestRunResults();

        System.out.println(stat.getLatestRunResults());

        //success, but too old
        for (int i = 0; i < RunStat.MAX_LATEST_RUNS; i++) {
            occurrence.setId("id:10231,build:(id:" + 1000 + i + ")");
            occurrence.status = "SUCCESS";
            stat.addTestRunToLatest(occurrence);
        }

        assert !stat.getLatestRunResults().contains(0) : stat.getLatestRunResults();

        System.out.println(stat.getLatestRunResults());
    }
}
