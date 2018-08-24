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

package org.apache.ignite.ci.analysis;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.issue.EventTemplate;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.ci.analysis.RunStat.RunStatus.RES_CRITICAL_FAILURE;
import static org.apache.ignite.ci.analysis.RunStat.RunStatus.RES_FAILURE;
import static org.apache.ignite.ci.analysis.RunStat.RunStatus.RES_MUTED_FAILURE;
import static org.apache.ignite.ci.analysis.RunStat.RunStatus.RES_OK;

/**
 * Test or Build run statistics.
 */
@Persisted
public class RunStat {
    public static final int MAX_LATEST_RUNS = 100;

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

    /**
     * @deprecated {@link #latestRuns} should be used.
     */
    @Deprecated
    @Nullable
    SortedMap<TestId, Integer> latestRunResults;

    @Nullable
    SortedMap<TestId, RunInfo> latestRuns;

    /**
     * @param name Name of test or suite.
     */
    public RunStat(String name) {
        this.name = name;
    }

    public void addTestRun(TestOccurrence testOccurrence, Boolean changesExist) {
        addTestRunToLatest(testOccurrence, changesStatus(changesExist));

        runs++;

        if (testOccurrence.duration != null) {
            totalDurationMs += testOccurrence.duration;
            runsWithDuration++;
        }

        if (testOccurrence.isFailedTest())
            failures++;

        lastUpdatedMs = System.currentTimeMillis();
    }

    private ChangesState changesStatus(Boolean changesExist) {
        if (changesExist == null)
            return ChangesState.UNKNOWN;

        return changesExist ? ChangesState.EXIST : ChangesState.NONE;
    }

    public void addTestRunToLatest(TestOccurrence testOccurrence, ChangesState changesState) {
        TestId id = extractFullId(testOccurrence.getId());
        if (id == null) {
            System.err.println("Unable to parse TestOccurrence.id: " + id);

            return;
        }

        addRunToLatest(id, new RunInfo(testToResCode(testOccurrence), changesState));
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

    private RunStatus testToResCode(TestOccurrence testOccurrence) {
        if (!testOccurrence.isFailedTest())
            return RES_OK;

        return testOccurrence.isNotMutedOrIgnoredTest() ? RES_FAILURE : RES_MUTED_FAILURE;
    }

    private void addRunToLatest(TestId id, RunInfo runInfo) {
        if (latestRuns == null)
            latestRuns = new TreeMap<>();

        latestRuns.put(id, runInfo);

        if (latestRuns.size() > MAX_LATEST_RUNS)
            latestRuns.remove(latestRuns.firstKey());
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
        if (latestRuns == null)
            return 0;

        return (int)latestRuns.values().stream().filter(res -> res.status != RES_OK).count();
    }

    public int getCriticalFailuresCount() {
        if (latestRuns == null)
            return 0;

        return (int)latestRuns.values().stream().filter(res -> res.status == RES_CRITICAL_FAILURE).count();
    }

    public int getRunsCount() {
        return latestRuns == null ? 0 : latestRuns.size();
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
        return (long)(1.0 * totalDurationMs / runsWithDuration);
    }

    public void addBuildRun(Build build) {
        runs++;
//        build.lastChanges

        //todo ? add duration from statistics
        /*
        if (build.duration != null) {
            totalDurationMs += testOccurrence.duration;
            runsWithDuration++;
        } */

        if (!build.isSuccess())
            failures++;

        RunStatus resCode = build.isSuccess() ? RES_OK : RES_FAILURE;

        setBuildResCode(build.getId(), new RunInfo(resCode, ChangesState.UNKNOWN));
    }

    private void setBuildResCode(Integer buildId, RunInfo runInfo) {
        addRunToLatest(new TestId(buildId, 0), runInfo);
    }

    public void setBuildCriticalError(Integer bId) {
        setBuildResCode(bId, new RunInfo(RES_CRITICAL_FAILURE, ChangesState.UNKNOWN));
    }

    /**
     * {@inheritDoc}
     */
    @Override public String toString() {
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
        if (latestRuns == null)
            return Collections.emptyList();

        return latestRuns.values().stream().map(info -> info.status.code).collect(Collectors.toList());
    }

    private int[] concatArr(int[] arr1, int[] arr2) {
        int[] arr1and2 = new int[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, arr1and2, 0, arr1.length);
        System.arraycopy(arr2, 0, arr1and2, arr1.length, arr2.length);

        return arr1and2;
    }

    @Nullable
    public TestId detectTemplate(EventTemplate t) {
        if (latestRuns == null)
            return null;

        int centralEvtBuild = t.beforeEvent().length;

        int[] template = concatArr(t.beforeEvent(), t.eventAndAfter());

        assert centralEvtBuild < template.length;
        assert centralEvtBuild >= 0;

        Set<Map.Entry<TestId, RunInfo>> entries = latestRuns.entrySet();

        if (entries.size() < template.length)
            return null;

        List<Map.Entry<TestId, RunInfo>> histAsArr = new ArrayList<>(entries);

        TestId detectedAt = null;
        if (t.shouldBeFirst()) {
            if (histAsArr.size() >= runs) // skip if total runs can't fit to latest runs
                detectedAt = checkTemplateAtPos(template, centralEvtBuild, histAsArr, 0);
        }
        else {
            //start from the end to find most recent
            for (int idx = histAsArr.size() - template.length; idx >= 0; idx--) {
                detectedAt = checkTemplateAtPos(template, centralEvtBuild, histAsArr, idx);

                if (detectedAt != null)
                    break;
            }
        }

        return detectedAt;
    }

    @Nullable
    private TestId checkTemplateAtPos(int[] template, int centralEvtBuild, List<Map.Entry<TestId, RunInfo>> histAsArr,
        int idx) {
        for (int tIdx = 0; tIdx < template.length; tIdx++) {
            RunInfo curStatus = histAsArr.get(idx + tIdx).getValue();

            if (curStatus == null)
                break;

            RunStatus tmpl = RunStatus.byCode(template[tIdx]);

            if ((tmpl == RunStatus.RES_OK_OR_FAILURE && (curStatus.status == RES_OK || curStatus.status == RES_FAILURE))
                || curStatus.status == tmpl) {
                if (tIdx == template.length - 1)
                    return histAsArr.get(idx + centralEvtBuild).getKey();
            }
            else
                break;
        }

        return null;
    }

    public boolean isFlaky() {
        return getFlakyComments() != null;
    }

    @Nullable
    public String getFlakyComments() {
        if (latestRuns == null)
            return null;

        int statusChange = 0;

        RunInfo prev = null;

        for (RunInfo next : latestRuns.values()) {
            if (prev != null && next != null) {
                if (!prev.status.equals(next.status)
                    && next.changesState == ChangesState.NONE
                    && prev.changesState != ChangesState.UNKNOWN)
                    statusChange++;
            }
            prev = next;
        }

        if (statusChange < 1)
            return null;

        return "Test seems to be flaky: " +
            "change status [" + statusChange + "/" + latestRuns.size() + "]";
    }

    /**
     * Migrate data from latestRunResults to latestRuns.
     *
     * @deprecated need to be remove after migrate.
     */
    @Deprecated
    public void migrateLatestRuns(){
        if(latestRunResults == null)
            return;

        if (latestRuns == null)
            latestRuns = new TreeMap<>();
        else
            latestRuns.clear();

        for (Map.Entry<TestId, Integer> entry : latestRunResults.entrySet()) {
            latestRuns.put(entry.getKey(), new RunInfo(RunStatus.byCode(entry.getValue()), ChangesState.UNKNOWN));
        }
    }

    /**
     * Status of run.
     */
    public enum RunStatus {
        /** Result: success. */
        RES_OK(0),
        /** Result: general failure of test or suite. */
        RES_FAILURE(1),
        /** RES OK or RES FAILURE */
        RES_OK_OR_FAILURE(10),
        /** Result of test execution, muted failure found. */
        RES_MUTED_FAILURE(2),
        /** Result of suite: Critical failure, no results. */
        RES_CRITICAL_FAILURE(3);

        /** Mapping of status int -> object. */
        private static Map<Integer, RunStatus> holder = Stream.of(values()).collect(Collectors.toMap(RunStatus::getCode, i -> i));

        /** Represent status in int. */
        int code;

        /** */
        RunStatus(int code) {
            this.code = code;
        }

        /**
         * @return Status as int.
         */
        public int getCode() {
            return code;
        }

        /**
         * @param code Status as int.
         * @return Status of build.
         */
        public static RunStatus byCode(int code) {
            return holder.get(code);
        }
    }

    /** Changes state for run. */
    public enum ChangesState {
        /** Unknown number of changes for run. */
        UNKNOWN,
        /** Run without changes. */
        NONE,
        /** Run with changes. */
        EXIST
    }

    /**
     * Run info for storage in cache.
     */
    public static class RunInfo {
        /** Status of run. */
        RunStatus status;
        /** State of changes for run. */
        ChangesState changesState;

        /**
         *
         */
        public RunInfo(RunStatus status, ChangesState changesState) {
            this.status = status;
            this.changesState = changesState;
        }
    }

    public static class TestId implements Comparable<TestId> {
        int buildId;
        int testId;

        public TestId(Integer buildId, Integer testId) {
            this.buildId = buildId;
            this.testId = testId;
        }

        public int getBuildId() {
            return buildId;
        }

        public int getTestId() {
            return testId;
        }

        /**
         * {@inheritDoc}
         */
        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            TestId id = (TestId)o;
            return buildId == id.buildId &&
                testId == id.testId;
        }

        /**
         * {@inheritDoc}
         */
        @Override public int hashCode() {
            return Objects.hashCode(buildId, testId);
        }

        /**
         * {@inheritDoc}
         */
        @Override public int compareTo(@NotNull TestId o) {
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
        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("buildId", buildId)
                .add("testId", testId)
                .toString();
        }
    }

}
