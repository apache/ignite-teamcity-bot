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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.issues.IssueRef;
import org.apache.ignite.ci.tcmodel.result.issues.IssueUsage;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.util.TimeUtil;
import org.apache.ignite.ci.web.IBackgroundUpdatable;

/**
 * Summary of build statistics.
 */
public class BuildStatisticsSummary extends UpdateInfo implements IBackgroundUpdatable {
    /** Build with test and problems references. */
    public Build build;

    /** List of problem occurrences. */
    private List<ProblemOccurrence> problems;

    /** List of related issues. */
    public List<IssueRef> relatedIssues;

    /** Duration printable. */
    public String durationPrintable;

    /** Short build run result (without snapshot-dependencies printable result). */
    public String shortRes;

    /** Snapshot-dependencies build run result. */
    public List<String> fullRes;

    /** Snapshot-dependency. */
    private List<BuildRef> snapshotDependencies;

    /** Build problems count. */
    public long problemsCount;

    public BuildStatisticsSummary(Build build){
        this.build = build;
    }

    /** Initialize build statistics. */
    public void initialize(@Nonnull final ITeamcity teamcity) {

        if (build.isFakeStub())
            return;

        relatedIssues = teamcity.getIssuesUsagesList(build.relatedIssuesRef.href).getIssuesUsagesNonNull().stream()
            .map(IssueUsage::getIssue).collect(Collectors.toList());

        durationPrintable = TimeUtil
            .getDurationPrintable(build.getFinishDate().getTime() - build.getStartDate().getTime());

        snapshotDependencies = getSnapshotDependencies(teamcity, build);

        problems = getProblems(teamcity);

        shortRes = getShortRes();

        fullRes = getFullRes();

        problemsCount = getAllProblemsCount(null);
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
    private List<ProblemOccurrence> getProblems(@Nonnull final ITeamcity teamcity){
        if (snapshotDependencies == null)
            return Collections.emptyList();

        List<ProblemOccurrence> problemOccurrences = new ArrayList<>();

        List<BuildRef> snapshotDependencyWithProblems = getSnapshotDependenciesWithProblems();

        for (BuildRef buildRef : snapshotDependencyWithProblems)
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
    private List<BuildRef> getSnapshotDependenciesWithProblems(){
        if (snapshotDependencies == null)
            return Collections.emptyList();

        return snapshotDependencies.stream()
            .filter(b -> !b.isSuccess())
            .collect(Collectors.toList());
    }

    /**
     * @param buildTypeId Build type id (if null - for all problems).
     */
    private Stream<ProblemOccurrence> getProblemsStream(String buildTypeId) {
        if (problems == null)
            return Stream.empty();

        return problems.stream()
            .filter(Objects::nonNull)
            .filter(p -> {
                    if (buildTypeId == null)
                        return true;
                    if (p.buildRef == null && buildTypeId == null)
                        return true;
                    if (p.buildRef != null && buildTypeId.equals(p.buildRef.buildTypeId))
                        return true;
                    return false;
                }
            );
    }

    /**
     * Full build run result (snapshot-dependencies printable result).
     *
     * @return printable result;
     */
    private List<String> getFullRes(){
        List<String> fullRes = new ArrayList<>();

        List<BuildRef> snapshotDependencyWithProblems = getSnapshotDependenciesWithProblems();

        for (BuildRef build : snapshotDependencyWithProblems)
            fullRes.add(getRes(build.buildTypeId));

        return fullRes.stream()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Short build run result (without snapshot-dependencies printable result).
     *
     * @return printable result;
     */
    private String getShortRes(){
        return getRes(null);
    }

    /**
     * Build run result for buildTypeId.
     *
     * @param buildTypeId buildTypeId.
     *
     * @return printable result.
     */
    private String getRes(String buildTypeId){
        StringBuilder res = new StringBuilder();

        addKnownProblemCnt(res, ProblemOccurrence.TC_EXECUTION_TIMEOUT, getExecutionTimeoutCount(buildTypeId));
        addKnownProblemCnt(res, ProblemOccurrence.TC_JVM_CRASH, getJvmCrashProblemCount(buildTypeId));
        addKnownProblemCnt(res, ProblemOccurrence.TC_OOME, getOomeProblemCount(buildTypeId));
        addKnownProblemCnt(res, ProblemOccurrence.TC_EXIT_CODE, getExitCodeProblemsCount(buildTypeId));
        addKnownProblemCnt(res, ProblemOccurrence.TC_FAILED_TESTS, getFailedTestsProblemCount(buildTypeId));
        addKnownProblemCnt(res, ProblemOccurrence.SNAPSHOT_DEPENDENCY_ERROR, getSnapshotDepProblemCount(buildTypeId));
        addKnownProblemCnt(res, ProblemOccurrence.OTHER, getOtherProblemCount(buildTypeId));

        res.insert(0, (buildTypeId != null ? buildTypeId : "TOTAL") + " [" + getAllProblemsCount(buildTypeId) + "]"
            + (res.length() != 0 ? ": " : " "));

        return res.toString();
    }

    /**
     * @param res Response.
     * @param nme Name of problem.
     * @param execToCnt Execute to count.
     */
    private void addKnownProblemCnt(StringBuilder res, String nme, long execToCnt) {
        if (execToCnt > 0) {
            if (res.length() > 0)
                res.append(", ");

            res.append(nme)
                .append(execToCnt > 1 ? " [" + execToCnt + "]" : "");
        }
    }

    /** {@inheritDoc} */
    @Override public void setUpdateRequired(boolean update) {
        updateRequired = update;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildStatisticsSummary))
            return false;

        BuildStatisticsSummary that = (BuildStatisticsSummary)o;

        return problemsCount == that.problemsCount &&
            Objects.equals(build, that.build) &&
            Objects.equals(problems, that.problems) &&
            Objects.equals(relatedIssues, that.relatedIssues) &&
            Objects.equals(durationPrintable, that.durationPrintable) &&
            Objects.equals(getShortRes(), that.getShortRes()) &&
            Objects.equals(getFullRes(), that.getFullRes()) &&
            Objects.equals(snapshotDependencies, that.snapshotDependencies);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {

        return Objects.hash(build, problems, relatedIssues, durationPrintable, getShortRes(), getFullRes(),
            snapshotDependencies, problemsCount);
    }
}