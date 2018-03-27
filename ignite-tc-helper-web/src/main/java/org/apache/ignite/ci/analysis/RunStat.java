package org.apache.ignite.ci.analysis;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;

/**
 * Test or Build run statistics.
 */
@Persisted
public class RunStat {
    private static final int MAX_LATEST_RUNS = 30;
    private static final int RES_OK = 0;
    private static final int RES_FAILURE = 1;
    private static final int RES_MUTED_FAILURE = 2;

    public int runs;
    public int failures;
    public long totalDurationMs;
    public int runsWithDuration;
    public long lastUpdatedMs;
    private String name;

    @Nullable List<Integer> latestRuns;

    /**
     * @param name Name of test or suite.
     */
    public RunStat(String name) {
        this.name = name;
    }

    public void addTestRun(TestOccurrence testOccurrence) {
        //todo need to store map ts/id->run
        addRunToLatest(testToResCode(testOccurrence));

        runs++;

        if (testOccurrence.duration != null) {
            totalDurationMs += testOccurrence.duration;
            runsWithDuration++;
        }

        if (testOccurrence.isFailedTest())
            failures++;

        lastUpdatedMs = System.currentTimeMillis();
    }

    public int testToResCode(TestOccurrence testOccurrence) {
        int resCode;
        if (testOccurrence.isFailedTest())
            resCode = testOccurrence.isNotMutedOrIgnoredTest() ? RES_FAILURE : RES_MUTED_FAILURE;
        else
            resCode = RES_OK;

        return resCode;
    }

    public void addRunToLatest(int resCode) {
        if (latestRuns == null)
            latestRuns = new ArrayList<>();

        latestRuns.add(resCode);

        if (latestRuns.size() > MAX_LATEST_RUNS)
            latestRuns.remove(0);
    }

    public String name() {
        return name;
    }

    public float getFailRate() {
        return 1.0f * failures / runs;
    }

    public String getFailPercentPrintable() {
        float percent = getFailPercent();
        return getPercentPrintable(percent);
    }

    private String getPercentPrintable(float percent) {
        return String.format("%.1f", percent).replace(".", ",");
    }

    private float getFailPercent() {
        return getFailRate() * 100.0f;
    }

    public long getAverageDurationMs() {
        if (runsWithDuration == 0)
            return 0;
        return (long)(1.0 * totalDurationMs / runsWithDuration);
    }

    public void addBuildRun(Build build) {
        runs++;

        //todo ? need to add duration from statistics
        /*
        if (build.duration != null) {
            totalDurationMs += testOccurrence.duration;
            runsWithDuration++;
        } */

        if (!build.isSuccess())
            failures++;

        int resCode = build.isSuccess() ? RES_OK : RES_FAILURE;

        addRunToLatest(resCode);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return "RunStat{" +
            "name='" + name + '\'' +
            ", failRate=" + getFailPercentPrintable() + "%" +
            '}';
    }

    /**
     * @return
     */
    @Nullable public List<Integer> getLatestRuns() {
        return latestRuns;
    }
}
