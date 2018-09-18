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
    }

    /** Build with test and problems references. */
    public Integer buildId;

    /** Build start date. */
    public String startDate;

    /** Test occurrences. */
    public TestOccurrencesRef testOccurrences;

    /** List of problem occurrences. */
    private List<ProblemOccurrence> problemOccurrenceList;

    /** Duration printable. */
    public String durationPrintable;

    /** Short build run result (without snapshot-dependencies printable result). */
    public Map<String, Long> totalProblems;

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
        startDate = dateFormat.format(build.getStartDate());

        isFakeStub = build.isFakeStub();

        if (isFakeStub)
            return;

        testOccurrences = build.testOccurrences;

        durationPrintable = TimeUtil
            .getDurationPrintable(build.getFinishDate().getTime() - build.getStartDate().getTime());

        List<BuildRef> snapshotDependencies = getSnapshotDependencies(teamcity, build);

        List<BuildRef> snapshotDependenciesWithProblems = getBuildsWithProblems(snapshotDependencies);

        problemOccurrenceList = getProblems(teamcity, snapshotDependenciesWithProblems);

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
     * Builds without status "Success".
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
     * Short build run result (without snapshot-dependencies result).
     *
     * @return printable result;
     */
    private Map<String, Long> getRes(){ return getBuildTypeProblemsCount(null); }


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
    @Override public void setUpdateRequired(boolean update) {
        updateRequired = update;
    }
}