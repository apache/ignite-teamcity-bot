package org.apache.ignite.ci.analysis;

import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;

/**
 * Test run statistics
 */
public class RunStat {
    public int runs;
    public int failures;
    public long totalDurationMs;
    public int runsWithDuration;

    public void addRun(TestOccurrence testOccurrence) {
        runs++;

        if (testOccurrence.duration != null) {
            totalDurationMs += testOccurrence.duration;
            runsWithDuration++;
        }

        if (testOccurrence.isFailedTest())
            failures++;
    }

    public float getFailRate() {
        return 1.0f * failures / runs;
    }

    String getFailPercentPrintable() {
        return String.format("%.2f", getFailPercent());
    }

    private float getFailPercent() {
        return getFailRate() * 100.0f;
    }

    public long getAverageDurationMs() {
        if (runsWithDuration == 0)
            return 0;
        return (long)(1.0 * totalDurationMs / runsWithDuration);
    }

    public void addRun(Build build) {
        runs++;

        //todo ? add duration
        /*
        if (build.duration != null) {
            totalDurationMs += testOccurrence.duration;
            runsWithDuration++;
        } */

        if (!build.isSuccess())
            failures++;
    }
}
