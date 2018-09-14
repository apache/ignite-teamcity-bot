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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.TestOccurrencesRef;
import org.apache.ignite.ci.tcmodel.result.issues.IssueRef;
import org.apache.ignite.ci.tcmodel.result.issues.IssueUsage;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.util.TimeUtil;
import org.apache.ignite.ci.web.IBackgroundUpdatable;

/**
 * Summary of build statistics.
 */
public class BuildStatisticsSummary extends UpdateInfo implements IBackgroundUpdatable {
    /** Short problem names. */
    public static final String TOTAL = "TOTAL";

    private static Map<String, String> shortProblemNames = new HashMap<>();

    static {
        shortProblemNames.put(TOTAL, "TT");
        shortProblemNames.put(ProblemOccurrence.TC_EXECUTION_TIMEOUT, "ET");
        shortProblemNames.put(ProblemOccurrence.TC_JVM_CRASH, "JC");
        shortProblemNames.put(ProblemOccurrence.TC_OOME, "OO");
        shortProblemNames.put(ProblemOccurrence.TC_EXIT_CODE, "EC");
        shortProblemNames.put(ProblemOccurrence.TC_FAILED_TESTS, "FT");
        shortProblemNames.put(ProblemOccurrence.SNAPSHOT_DEPENDENCY_ERROR, "SD");
        shortProblemNames.put(ProblemOccurrence.OTHER, "OT");
    }

    /** Build with test and problems references. */
    public Integer buildId;

    public String date;

    /** Test occurrences. */
    public TestOccurrencesRef testOccurrences;

    /** List of problem occurrences. */
    private List<ProblemOccurrence> problemOccurrenceList;

    /** Snapshot-dependencies build run result. */
    public Map<String, Map<String, Long>> dependenciesProblems;

    /** List of related issues. */
    private List<IssueRef> relatedIssues;

    /** Duration printable. */
    public String durationPrintable;

    /** Short build run result (without snapshot-dependencies printable result). */
    public Map<String, Long> totalProblems;

    /** Build problems count. */
    public long problemsCount;

    /** Is fake stub. */
    public boolean isFakeStub;

    /**
     * @param buildId Build id.
     */
    public BuildStatisticsSummary(Integer buildId){
        this.buildId = buildId;
    }

    /** Initialize build statistics. */
    public void initialize(@Nonnull final ITeamcity teamcity) {
        Build build = teamcity.getBuild(buildId);

        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");
        dateFormat.format(build.getFinishDate());
        date = dateFormat.format(build.getFinishDate());

        isFakeStub = build.isFakeStub();

        if (isFakeStub)
            return;

        testOccurrences = build.testOccurrences;

        relatedIssues = teamcity.getIssuesUsagesList(build.relatedIssuesRef.href).getIssuesUsagesNonNull().stream()
            .map(IssueUsage::getIssue).collect(Collectors.toList());

        durationPrintable = TimeUtil
            .getDurationPrintable(build.getFinishDate().getTime() - build.getStartDate().getTime());

        List<BuildRef> snapshotDependencies = getSnapshotDependencies(teamcity, build);

        List<BuildRef> snapshotDependenciesWithProblems = getBuildsWithProblems(snapshotDependencies);

        problemOccurrenceList = getProblems(teamcity, snapshotDependenciesWithProblems);

        //dependenciesProblems = getFullRes(snapshotDependenciesWithProblems);

        totalProblems = getShortRes();

        problemsCount = getAllProblemsCount(null);

        problemOccurrenceList = null;
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

    private long getFailedTestsProblemCount(String buildTypeId) {
        return getProblemsStream(buildTypeId).filter(ProblemOccurrence::isFailedTests).count();
    }

    private long getSnapshotDepProblemCount(String buildTypeId) {
        return getProblemsStream(buildTypeId).filter(ProblemOccurrence::isSnapshotDepProblem).count();
    }

    private long getOtherProblemCount(String buildTypeId) {
        return getProblemsStream(buildTypeId).filter(ProblemOccurrence::isOther).count();
    }

    private long getAllProblemsCount(String buildTypeId) {
        return getProblemsStream(buildTypeId).count();
    }

    /**
     * Problems for all snapshot-dependencies.
     *
     * @param teamcity Teamcity.
     */
    private List<ProblemOccurrence> getProblems(@Nonnull final ITeamcity teamcity, List<BuildRef> builds){

        List<ProblemOccurrence> problemOccurrences = new ArrayList<>();

        for (BuildRef buildRef : builds)
            problemOccurrences.addAll(teamcity
                .getProblems(teamcity.getBuild(buildRef.href))
                .getProblemsNonNull());

        return problemOccurrences;
    }

    /**
     * Snapshot-dependencies for build.
     *
     * @param teamcity Teamcity.
     * @param buildRef Build reference.
     */
    private List<BuildRef> getSnapshotDependencies(@Nonnull final ITeamcity teamcity, BuildRef buildRef){
        List<BuildRef> snapshotDependencies = new ArrayList<>();

        if (buildRef.isComposite()){
            Build build = teamcity.getBuild(buildRef.href);

            for (BuildRef snDep : build.getSnapshotDependenciesNonNull())
                snapshotDependencies.addAll(getSnapshotDependencies(teamcity, snDep));
        } else
            snapshotDependencies.add(buildRef);

        return snapshotDependencies;
    }

    /**
     * Snapshot-dependencies without status "Success".
     */
    private List<BuildRef> getBuildsWithProblems(List<BuildRef> builds){
        return builds.stream()
            .filter(b -> !b.isSuccess())
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
            .filter(p -> buildTypeId == null || buildTypeId.equals(p.buildRef.buildTypeId)
            );
    }

    /**
     * Full build run result (snapshot-dependencies result).
     *
     * @return printable result;
     */
    private Map<String, Map<String, Long>> getFullRes(List<BuildRef> builds){
        Map<String, Map<String, Long>> buildProblemOccurrences = new HashMap<>();

        for (BuildRef build : builds)
            buildProblemOccurrences.put(build.buildTypeId, getBuildTypeOccurrences(build.buildTypeId));

        return buildProblemOccurrences;
    }

    /**
     * Short build run result (without snapshot-dependencies result).
     *
     * @return printable result;
     */
    private Map<String, Long> getShortRes(){ return getBuildTypeOccurrences(null);
    }


    private Map<String, Long> getBuildTypeOccurrences(String buildTypeId){
        Map<String, Long> occurrences = new HashMap<>();

        occurrences.put(shortProblemNames.get(TOTAL), getAllProblemsCount(buildTypeId));
        occurrences.put(shortProblemNames.get(ProblemOccurrence.TC_EXECUTION_TIMEOUT), getExecutionTimeoutCount(buildTypeId));
        occurrences.put(shortProblemNames.get(ProblemOccurrence.TC_JVM_CRASH), getJvmCrashProblemCount(buildTypeId));
        occurrences.put(shortProblemNames.get(ProblemOccurrence.TC_OOME), getOomeProblemCount(buildTypeId));
        occurrences.put(shortProblemNames.get(ProblemOccurrence.TC_EXIT_CODE), getExitCodeProblemsCount(buildTypeId));
        occurrences.put(shortProblemNames.get(ProblemOccurrence.TC_FAILED_TESTS), getFailedTestsProblemCount(buildTypeId));
        occurrences.put(shortProblemNames.get(ProblemOccurrence.SNAPSHOT_DEPENDENCY_ERROR), getSnapshotDepProblemCount(buildTypeId));
        occurrences.put(shortProblemNames.get(ProblemOccurrence.OTHER), getOtherProblemCount(buildTypeId));

        return occurrences;
    }

    /** {@inheritDoc} */
    @Override public void setUpdateRequired(boolean update) {
        updateRequired = update;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildStatisticsSummary))
            return false;

        BuildStatisticsSummary that = (BuildStatisticsSummary)o;

        return problemsCount == that.problemsCount &&
            isFakeStub == that.isFakeStub &&
            Objects.equals(buildId, that.buildId) &&
            Objects.equals(testOccurrences, that.testOccurrences) &&
            Objects.equals(problemOccurrenceList, that.problemOccurrenceList) &&
            Objects.equals(dependenciesProblems, that.dependenciesProblems) &&
            Objects.equals(relatedIssues, that.relatedIssues) &&
            Objects.equals(durationPrintable, that.durationPrintable) &&
            Objects.equals(totalProblems, that.totalProblems);
    }

    @Override public int hashCode() {

        return Objects.hash(buildId, testOccurrences, problemOccurrenceList, dependenciesProblems, relatedIssues, durationPrintable,
            totalProblems, problemsCount, isFakeStub);
    }
}