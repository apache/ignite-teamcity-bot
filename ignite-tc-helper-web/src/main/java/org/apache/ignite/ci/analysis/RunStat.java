package org.apache.ignite.ci.analysis;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.util.*;
import javax.annotation.Nullable;

import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.issue.EventTemplate;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.jetbrains.annotations.NotNull;

/**
 * Test or Build run statistics.
 */
@Persisted
public class RunStat {
    public static final int MAX_LATEST_RUNS = 50;
    public static final int RES_OK = 0;

    /**
     * Result: general failure of test or suite.
     */
    public static final int RES_FAILURE = 1;

    /**
     * RES OK or RES FAILURE
     */
    public static final int RES_OK_OR_FAILURE = 10;

    /**
     * Result of test execution, muted failure found.
     */
    private static final int RES_MUTED_FAILURE = 2;

    /**
     * Result of suite: Critical failure, no results.
     */
    public static final int RES_CRITICAL_FAILURE = 3;

    /**
     * Runs registered all the times.
     */
    private int runs;

    /**
     * Failures registered all the times.
     */
    private int failures;

    public long totalDurationMs;
    public int runsWithDuration;

    /**
     * timestamp of last write to entry
     */
    public long lastUpdatedMs;

    /**
     * Name: Key in run stat cache
     */
    private String name;

    @Nullable
    SortedMap<TestId, Integer> latestRunResults;

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
        } catch (Exception ignored) {
            return null;
        }
    }

    private int testToResCode(TestOccurrence testOccurrence) {
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
            latestRunResults.remove(latestRunResults.firstKey());
    }

    public String name() {
        return name;
    }

    public float getFailRateAllHist() {
        if (runs == 0)
            return 1.0f;

        return 1.0f * failures / runs;
    }

    public int getFailuresAllHist() {
        return failures;
    }

    public int getRunsAllHist() {
        return runs;
    }

    /**
     * @return
     */
    public String getFailPercentAllHistPrintable() {
        return getPercentPrintable(getFailRateAllHist() * 100.0f);
    }

    /**
     * @return float representing fail rate
     */
    public float getFailRate() {
        int runs = getRunsCount();

        if (runs == 0)
            return 1.0f;

        return 1.0f * getFailuresCount() / runs;
    }


    /**
     * @return float representing fail rate
     */
    public float getCriticalFailRate() {
        int runs = getRunsCount();

        if (runs == 0)
            return 1.0f;

        return 1.0f * getCriticalFailuresCount() / runs;
    }

    public int getFailuresCount() {
        if (latestRunResults == null)
            return 0;

        return (int) latestRunResults.values().stream().filter(res -> res != RES_OK).count();
    }

    public int getCriticalFailuresCount() {
        if (latestRunResults == null)
            return 0;

        return (int) latestRunResults.values().stream().filter(res -> res == RES_CRITICAL_FAILURE).count();
    }

    public int getRunsCount() {
        return latestRunResults == null ? 0 : latestRunResults.size();
    }

    public String getFailPercentPrintable() {
        return getPercentPrintable(getFailRate() * 100.0f);
    }

    public String getCriticalFailPercentPrintable() {
        return getPercentPrintable(getCriticalFailRate() * 100.0f);
    }

    private static String getPercentPrintable(float percent) {
        return String.format("%.1f", percent).replace(".", ",");
    }

    public long getAverageDurationMs() {
        if (runsWithDuration == 0)
            return 0;
        return (long) (1.0 * totalDurationMs / runsWithDuration);
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

        setBuildResCode(build.getId(), resCode);
    }

    private void setBuildResCode(Integer buildId, int resCode) {
        addRunToLatest(new TestId(buildId, 0), resCode);
    }

    public void setBuildCriticalError(Integer bId) {
        setBuildResCode(bId, RES_CRITICAL_FAILURE);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "RunStat{" +
                "name='" + name + '\'' +
                ", failRate=" + getFailPercentPrintable() + "%" +
                '}';
    }

    /**
     * @return
     */
    @Nullable
    public List<Integer> getLatestRunResults() {
        if (latestRunResults == null)
            return Collections.emptyList();

        return new ArrayList<>(latestRunResults.values());
    }

    private int[] concatArr(int[] array1, int[] array2) {
        int[] array1and2 = new int[array1.length + array2.length];
        System.arraycopy(array1, 0, array1and2, 0, array1.length);
        System.arraycopy(array2, 0, array1and2, array1.length, array2.length);

        return array1and2;
    }

    @Nullable
    public TestId detectTemplate(EventTemplate t) {
        if (latestRunResults == null)
            return null;

        int centralEventBuild = t.beforeEvent().length;

        int[] template = concatArr(t.beforeEvent(), t.eventAndAfter());

        assert centralEventBuild < template.length;
        assert centralEventBuild >= 0;

        Set<Map.Entry<TestId, Integer>> entries = latestRunResults.entrySet();

        if (entries.size() < template.length)
            return null;

        ArrayList<Map.Entry<TestId, Integer>> histAsArray = new ArrayList<>(entries);

        //start from the end to find most recent
        for (int idx = histAsArray.size() - template.length; idx >=0; idx--) {
            for (int tIdx = 0; tIdx < template.length; tIdx++) {
                if (histAsArray.get(idx + tIdx).getValue().equals(template[tIdx])) {
                    if (tIdx == template.length - 1)
                        return histAsArray.get(idx + centralEventBuild).getKey();
                } else {
                    break;
                }
            }
        }
        return null;
    }

    public static class TestId implements Comparable<TestId> {
        public int getBuildId() {
            return buildId;
        }

        public int getTestId() {
            return testId;
        }

        int buildId;
        int testId;

        public TestId(Integer buildId, Integer testId) {

            this.buildId = buildId;
            this.testId = testId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            TestId id = (TestId) o;
            return buildId == id.buildId &&
                    testId == id.testId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hashCode(buildId, testId);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(@NotNull TestId o) {
            int buildComp = buildId - o.buildId;
            if (buildComp != 0)
                return buildComp > 0 ? 1 : -1;

            int testComp = testId - o.testId;
            if (testComp != 0)
                return testComp > 0 ? 1 : -1;

            return 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("buildId", buildId)
                    .add("testId", testId)
                    .toString();
        }
    }

}
