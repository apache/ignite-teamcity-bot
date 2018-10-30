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
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.TestCompacted;

/**
 * Summary of build statistics.
 */
public class BuildStatisticsSummary {
    /** Short problem names. */
    public static final String TOTAL = "TOTAL";

    /** Short problem names map. Full name - key, short name - value. */
    public static BiMap<String, String> shortProblemNames = HashBiMap.create();

    /** Full problem names map. Short name - key, full name - value. */
    public static BiMap<String, String> fullProblemNames;

    static {
        shortProblemNames.put(TOTAL, "TT");
        shortProblemNames.put(ProblemOccurrence.TC_EXECUTION_TIMEOUT, "ET");
        shortProblemNames.put(ProblemOccurrence.TC_JVM_CRASH, "JC");
        shortProblemNames.put(ProblemOccurrence.TC_OOME, "OO");
        shortProblemNames.put(ProblemOccurrence.TC_EXIT_CODE, "EC");

        fullProblemNames = shortProblemNames.inverse();
    }

    /** Build with test and problems references. */
    public Integer buildId;

    /** Build start date. */
    public String startDate;

    /** Test occurrences. */
    public TestOccurrencesRef testOccurrences;

    /** List of problem occurrences. */
    private List<ProblemOccurrence> problemOccurrenceList;

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
    public BuildStatisticsSummary(Integer buildId){
        this.buildId = buildId;
    }

    /** Initialize build statistics. */
    public void initialize(@Nonnull final IStringCompactor compactor, @Nonnull final ITeamcityIgnited ignitedTeamcity) {
        FatBuildCompacted build = ignitedTeamcity.getFatBuild(buildId);

        isFakeStub = build.isFakeStub();

        if (isFakeStub)
            return;

        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");

        startDate = dateFormat.format(build.getStartDate());

        testOccurrences = build.getTestOccurrences(compactor);

        duration = (build.getFinishDate().getTime() - build.getStartDate().getTime()) / 1000;

        List<FatBuildCompacted> snapshotDependencies = getSnapshotDependencies(ignitedTeamcity, buildId);

        List<FatBuildCompacted> snapshotDependenciesWithProblems = getBuildsWithProblems(snapshotDependencies, compactor);

        problemOccurrenceList = getProblems(snapshotDependenciesWithProblems, compactor);

        totalProblems = getRes();
    }

    private long getExecutionTimeoutCount(String buildTypeId) {
        return getProblemsStream(buildTypeId).filter(ProblemOccurrence::isExecutionTimeout).count();
    }

    private long getJvmCrashProblemCount(String buildTypeId) {
        return getProblemsStream(buildTypeId).filter(ProblemOccurrence::isJvmCrash).count();
    }

    private long getExitCodeProblemsCount(String buildTypeId) {
        return getProblemsStream(buildTypeId).filter(ProblemOccurrence::isExitCode).count();
    }

    private long getOomeProblemCount(String buildTypeId) {
        return getProblemsStream(buildTypeId).filter(ProblemOccurrence::isOome).count();
    }

    private List<ProblemOccurrence> getProblems(List<FatBuildCompacted> builds, IStringCompactor compactor){
        List<ProblemOccurrence> problemOccurrences = new ArrayList<>();

        for (FatBuildCompacted build : builds)
            problemOccurrences.addAll(
                build.problems(compactor)
            );

        return problemOccurrences;
    }

    /**
     * Snapshot-dependencies for build.
     *
     * @param ignitedTeamcity ignitedTeamcity.
     * @param buildId Build Id.
     */
    private List<FatBuildCompacted> getSnapshotDependencies(@Nonnull final ITeamcityIgnited ignitedTeamcity, Integer buildId){
        List<FatBuildCompacted> snapshotDependencies = new ArrayList<>();
        FatBuildCompacted build = ignitedTeamcity.getFatBuild(buildId);

        if (build.isComposite()){
            for (Integer id : build.snapshotDependencies())
                snapshotDependencies.addAll(getSnapshotDependencies(ignitedTeamcity, id));
        } else
            snapshotDependencies.add(ignitedTeamcity.getFatBuild(buildId));

        return snapshotDependencies;
    }
    /**
     * Builds without status "Success".
     */
    private List<FatBuildCompacted> getBuildsWithProblems(List<FatBuildCompacted> builds, IStringCompactor compactor){
        return builds.stream()
            .filter(b -> !b.isSuccess(compactor))
            .collect(Collectors.toList());
    }

    /**
     * @param buildTypeId Build type id (if null - for all problems).
     */
    private Stream<ProblemOccurrence> getProblemsStream(String buildTypeId) {
        if (problemOccurrenceList == null)
            return Stream.empty();

        return problemOccurrenceList.stream()
            .filter(Objects::nonNull)
            .filter(p -> buildTypeId == null || buildTypeId.equals(p.buildRef.buildTypeId));
    }

    /**
     * Short build run result (without snapshot-dependencies result).
     *
     * @return printable result;
     */
    private Map<String, Long> getRes(){
        return getBuildTypeProblemsCount(null);
    }


    /**
     * BuildType problems count (EXECUTION TIMEOUT, JVM CRASH, OOMe, EXIT CODE, TOTAL PROBLEMS COUNT).
     *
     * @param buildTypeId Build type id.
     */
    private Map<String, Long> getBuildTypeProblemsCount(String buildTypeId){
        Map<String, Long> occurrences = new HashMap<>();

        occurrences.put(shortProblemNames.get(ProblemOccurrence.TC_EXECUTION_TIMEOUT),
            getExecutionTimeoutCount(buildTypeId));
        occurrences.put(shortProblemNames.get(ProblemOccurrence.TC_JVM_CRASH), getJvmCrashProblemCount(buildTypeId));
        occurrences.put(shortProblemNames.get(ProblemOccurrence.TC_OOME), getOomeProblemCount(buildTypeId));
        occurrences.put(shortProblemNames.get(ProblemOccurrence.TC_EXIT_CODE), getExitCodeProblemsCount(buildTypeId));
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
