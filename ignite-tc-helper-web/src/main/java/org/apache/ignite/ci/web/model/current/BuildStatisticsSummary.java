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

package org.apache.ignite.ci.web.model.current;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProblemCompacted;

import static org.apache.ignite.ci.tcmodel.hist.BuildRef.STATUS_SUCCESS;
import static org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence.*;

/**
 * Summary of build statistics.
 */
public class BuildStatisticsSummary {
    /** String ids. */
    public static HashMap<String, Integer> strIds = new HashMap<>();

    /** Short problem names. */
    public static final String TOTAL = "TOTAL";

    /** Short problem names map. Full name - key, short name - value. */
    public static BiMap<String, String> shortProblemNames = HashBiMap.create();

    /** Full problem names map. Short name - key, full name - value. */
    public static BiMap<String, String> fullProblemNames;

    static {
        shortProblemNames.put(TOTAL, "TT");
        shortProblemNames.put(TC_EXECUTION_TIMEOUT, "ET");
        shortProblemNames.put(TC_JVM_CRASH, "JC");
        shortProblemNames.put(TC_OOME, "OO");
        shortProblemNames.put(TC_EXIT_CODE, "EC");

        fullProblemNames = shortProblemNames.inverse();
    }

    /** Build with test and problems references. */
    public Integer buildId;

    /** Build start date. */
    public String startDate;

    /** Test occurrences. */
    public TestOccurrencesRef testOccurrences = new TestOccurrencesRef();

    /** List of problem occurrences. */
    private List<ProblemCompacted> problemOccurrenceList;

    /** Duration (seconds). */
    public long duration;

    /** Short build run result (without snapshot-dependencies printable result). */
    public Map<String, Long> totalProblems;

    /** Is fake stub. */
    public boolean isFakeStub;

    /** Is valid. */
    public boolean isValid = true;

    /**
     * @param buildId Build id.
     */
    public BuildStatisticsSummary(Integer buildId) {
        this.buildId = buildId;
    }

    /** Initialize build statistics. */
    public void initialize(@Nonnull final IStringCompactor compactor, @Nonnull final ITeamcityIgnited ignitedTeamcity) {
        if (strIds.isEmpty()) {
            strIds.put(STATUS_SUCCESS, compactor.getStringId(STATUS_SUCCESS));
            strIds.put(TC_EXIT_CODE, compactor.getStringId(TC_EXIT_CODE));
            strIds.put(TC_OOME, compactor.getStringId(TC_OOME));
            strIds.put(TC_JVM_CRASH, compactor.getStringId(TC_JVM_CRASH));
            strIds.put(TC_EXECUTION_TIMEOUT, compactor.getStringId(TC_EXECUTION_TIMEOUT));
        }

        FatBuildCompacted build = ignitedTeamcity.getFatBuild(buildId);

        isFakeStub = build.isFakeStub();

        if (isFakeStub)
            return;

        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");

        startDate = dateFormat.format(build.getStartDate());

        int[] arr = new int[4];

        build.getAllTests().forEach(t -> {
                if (t.getIgnoredFlag())
                    arr[0]++;
                else if (t.getMutedFlag())
                    arr[1]++;
                else if (t.status() != strIds.get(STATUS_SUCCESS))
                    arr[2]++;

            arr[3]++;
        });

        testOccurrences.ignored = arr[0];
        testOccurrences.muted = arr[1];
        testOccurrences.failed = arr[2];
        testOccurrences.count = arr[3];
        testOccurrences.passed = testOccurrences.count - testOccurrences.failed - testOccurrences.ignored -
        testOccurrences.muted;

        duration = build.buildDuration(compactor) / 1000;

        List<FatBuildCompacted> snapshotDependencies = getSnapshotDependencies(ignitedTeamcity, buildId);

        List<FatBuildCompacted> snapshotDependenciesWithProblems = getBuildsWithProblems(snapshotDependencies);

        problemOccurrenceList = getProblems(snapshotDependenciesWithProblems);

        totalProblems = getBuildTypeProblemsCount();
    }

    /**
     * @param problemName Problem name.
     */
    private long getProblemsCount(String problemName) {
        if (problemOccurrenceList == null)
            return 0;

        return problemOccurrenceList.stream()
            .filter(Objects::nonNull)
            .filter(p -> p.type() == strIds.get(problemName)).count();
    }

    /**
     * Problems for all snapshot-dependencies.
     *
     * @param builds Builds.
     */
    private List<ProblemCompacted> getProblems(List<FatBuildCompacted> builds) {
        List<ProblemCompacted> problemOccurrences = new ArrayList<>();

        for (FatBuildCompacted build : builds) {
            problemOccurrences.addAll(
                build.problems()
            );
        }
        return problemOccurrences;
    }

    /**
     * Snapshot-dependencies for build.
     *
     * @param ignitedTeamcity ignitedTeamcity.
     * @param buildId Build Id.
     */
    private List<FatBuildCompacted> getSnapshotDependencies(@Nonnull final ITeamcityIgnited ignitedTeamcity,
        Integer buildId) {
        List<FatBuildCompacted> snapshotDependencies = new ArrayList<>();
        FatBuildCompacted build = ignitedTeamcity.getFatBuild(buildId);

        if (build.snapshotDependencies().length > 0) {
            for (Integer id : build.snapshotDependencies())
                snapshotDependencies.addAll(getSnapshotDependencies(ignitedTeamcity, id));
        }

        snapshotDependencies.add(build);

        return snapshotDependencies;
    }
    /**
     * Builds without status "Success".
     */
    private List<FatBuildCompacted> getBuildsWithProblems(List<FatBuildCompacted> builds) {
        return builds.stream()
            .filter(b -> b.status() != strIds.get(STATUS_SUCCESS))
            .collect(Collectors.toList());
    }

    /**
     * BuildType problems count (EXECUTION TIMEOUT, JVM CRASH, OOMe, EXIT CODE, TOTAL PROBLEMS COUNT).
     */
    private Map<String, Long> getBuildTypeProblemsCount() {
        Map<String, Long> occurrences = new HashMap<>();

        occurrences.put(shortProblemNames.get(TC_EXECUTION_TIMEOUT), getProblemsCount(TC_EXECUTION_TIMEOUT));
        occurrences.put(shortProblemNames.get(TC_JVM_CRASH), getProblemsCount(TC_JVM_CRASH));
        occurrences.put(shortProblemNames.get(TC_OOME), getProblemsCount(TC_OOME));
        occurrences.put(shortProblemNames.get(TC_EXIT_CODE), getProblemsCount(TC_EXIT_CODE));
        occurrences.put(shortProblemNames.get(TOTAL), occurrences.values().stream().mapToLong(Long::longValue).sum());

        return occurrences;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildStatisticsSummary))
            return false;

        BuildStatisticsSummary that = (BuildStatisticsSummary)o;

        return isFakeStub == that.isFakeStub &&
            Objects.equals(buildId, that.buildId) &&
            Objects.equals(startDate, that.startDate) &&
            Objects.equals(testOccurrences, that.testOccurrences) &&
            Objects.equals(problemOccurrenceList, that.problemOccurrenceList) &&
            Objects.equals(duration, that.duration) &&
            Objects.equals(totalProblems, that.totalProblems);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(buildId, startDate, testOccurrences, problemOccurrenceList,
            duration, totalProblems, isFakeStub);
    }
}
