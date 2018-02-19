package org.apache.ignite.ci.analysis;

import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;

/**
 * Test run statistics
 */
@Persisted
public class RunStat {
    public int runs;
    public int failures;
    public long totalDurationMs;
    public int runsWithDuration;
    public long lastUpdatedMs;
    private String name;

    /**
     * @param name Name of test or suite.
     */
    public RunStat(String name) {
        this.name = name;
    }

    public void addTestRun(TestOccurrence testOccurrence) {
        runs++;

        if (testOccurrence.duration != null) {
            totalDurationMs += testOccurrence.duration;
            runsWithDuration++;
        }

        if (testOccurrence.isFailedTest())
            failures++;

        lastUpdatedMs = System.currentTimeMillis();
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

    public void addTestRun(Build build) {
        runs++;

        //todo ? need to add duration from statistics
        /*
        if (build.duration != null) {
            totalDurationMs += testOccurrence.duration;
            runsWithDuration++;
        } */

        if (!build.isSuccess())
            failures++;
    }

    @Override public String toString() {
        return "RunStat{" +
            "name='" + name + '\'' +
            ", failRate=" + getFailPercentPrintable() + "%" +
            '}';
    }

}
