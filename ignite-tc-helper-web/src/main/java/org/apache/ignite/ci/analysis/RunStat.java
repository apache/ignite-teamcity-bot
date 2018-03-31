package org.apache.ignite.ci.analysis;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.Nullable;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.jetbrains.annotations.NotNull;

/**
 * Test or Build run statistics.
 */
@Persisted
public class RunStat {
    public static final int MAX_LATEST_RUNS = 40;
    private static final int RES_OK = 0;
    private static final int RES_FAILURE = 1;
    private static final int RES_MUTED_FAILURE = 2;

    public int runs;
    public int failures;
    public long totalDurationMs;
    public int runsWithDuration;
    public long lastUpdatedMs;
    private String name;

    @Nullable SortedMap<TestId, Integer> latestRunResults;

    /**
     * @param name Name of test or suite.
     */
    public RunStat(String name) {
        this.name = name;
    }

    public void addTestRun(TestOccurrence testOccurrence) {
        addTestRunToLatest(testOccurrence);

        runs++;

        if (testOccurrence.duration != null) {
            totalDurationMs += testOccurrence.duration;
            runsWithDuration++;
        }

        if (testOccurrence.isFailedTest())
            failures++;

        lastUpdatedMs = System.currentTimeMillis();
    }

    public void addTestRunToLatest(TestOccurrence testOccurrence) {
        TestId id = extractFullId(testOccurrence.getId());
        if (id == null) {
            System.err.println("Unable to parse TestOccurrence.id: " + id);

            return;
        }

        addRunToLatest(id, testToResCode(testOccurrence));
    }

    private static TestId extractFullId(String id) {
        Integer buildId = extractIdPrefixed(id, "build:(id:", ")");

        if (buildId == null)
            return null;

        Integer testId = extractIdPrefixed(id, "id:", ",");

        if (testId == null)
            return null;

        return new TestId(buildId, testId);

    }

    public static Integer extractIdPrefixed(String id, String prefix, String postfix) {
        try {
            int buildIdIdx = id.indexOf(prefix);
            if (buildIdIdx < 0)
                return null;

            int buildIdPrefixLen = prefix.length();
            int absBuildIdx = buildIdIdx + buildIdPrefixLen;
            int buildIdEndIdx = id.substring(absBuildIdx).indexOf(postfix);
            if (buildIdEndIdx < 0)
                return null;

            String substring = id.substring(absBuildIdx, absBuildIdx + buildIdEndIdx);

            return Integer.valueOf(substring);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    public int testToResCode(TestOccurrence testOccurrence) {
        int resCode;
        if (testOccurrence.isFailedTest())
            resCode = testOccurrence.isNotMutedOrIgnoredTest() ? RES_FAILURE : RES_MUTED_FAILURE;
        else
            resCode = RES_OK;

        return resCode;
    }

    private void addRunToLatest(TestId id, int resCode) {
        if (latestRunResults == null)
            latestRunResults = new TreeMap<>();

        latestRunResults.put(id, resCode);

        if (latestRunResults.size() > MAX_LATEST_RUNS)
            latestRunResults.remove( latestRunResults.firstKey() );
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

        addRunToLatest(new TestId(build.getId(), 0), resCode);
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
    @Nullable public List<Integer> getLatestRunResults() {
        if (latestRunResults == null)
            return Collections.emptyList();

        return new ArrayList<>(latestRunResults.values());
    }


    private static class TestId implements Comparable<TestId> {
        int buildId ;
        int testId;

        public TestId(Integer buildId, Integer testId) {

            this.buildId = buildId;
            this.testId = testId;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            TestId id = (TestId)o;
            return buildId == id.buildId &&
                testId == id.testId;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return Objects.hashCode(buildId, testId);
        }

        /** {@inheritDoc} */
        @Override public int compareTo(@NotNull TestId o) {
            int buildComp = buildId - o.buildId;
            if (buildComp != 0)
                return buildComp > 0 ? 1 : -1;

            int testComp = testId - o.testId;
            if (testComp != 0)
                return testComp > 0 ? 1 : -1;

            return 0;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("buildId", buildId)
                .add("testId", testId)
                .toString();
        }
    }

}
