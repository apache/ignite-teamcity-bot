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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.ITeamcity;
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

    /** Build run result printable. */
    public String result;

    public BuildStatisticsSummary(Build build){
        this.build = build;
    }

    /** Initialize build statistics. */
    public void initialize(@Nonnull final ITeamcity teamcity) {

        problems = teamcity.getProblems(build).getProblemsNonNull();

        relatedIssues = teamcity.getIssuesUsagesList(build.relatedIssuesRef.href).getIssuesUsagesNonNull().stream()
            .map(IssueUsage::getIssue).collect(Collectors.toList());

        durationPrintable = TimeUtil
            .getDurationPrintable(build.getFinishDate().getTime() - build.getStartDate().getTime());

        result = getResult();
    }

    private long getExecutionTimeoutCount() {
        return getProblemsStream().filter(ProblemOccurrence::isExecutionTimeout).count();
    }

    private long getJvmCrashProblemCount() {
        return getProblemsStream().filter(ProblemOccurrence::isJvmCrash).count();
    }

    private long getExitCodeProblemsCount() {
        return getProblemsStream().filter(ProblemOccurrence::isExitCode).count();
    }

    private long getOomeProblemCount() {
        return getProblemsStream().filter(ProblemOccurrence::isOome).count();
    }

    private long getFailedTestsProblemCount() {
        return getProblemsStream().filter(ProblemOccurrence::isFailedTests).count();
    }

    private long getSnapshotDepProblemCount() {
        return getProblemsStream().filter(ProblemOccurrence::isSnapshotDepProblem).count();
    }

    private long getOtherProblemCount() {
        return getProblemsStream().filter(p ->
            !p.isFailedTests()
                && !p.isSnapshotDepProblem()
                && !p.isExecutionTimeout()
                && !p.isJvmCrash()
                && !p.isExitCode()
                && !p.isOome()).count();
    }

    private Stream<ProblemOccurrence> getProblemsStream() {
        if (problems == null)
            return Stream.empty();

        return problems.stream().filter(Objects::nonNull);
    }

    /**
     * Build Run Result (filled if failed).
     *
     * @return printable result.
     */
    private String getResult() {
        StringBuilder res = new StringBuilder();

        addKnownProblemCnt(res, "TIMEOUT", getExecutionTimeoutCount());
        addKnownProblemCnt(res, "JVM CRASH", getJvmCrashProblemCount());
        addKnownProblemCnt(res, "OOMe", getOomeProblemCount());
        addKnownProblemCnt(res, "EXIT CODE", getExitCodeProblemsCount());
        addKnownProblemCnt(res, "FAILED TESTS", getFailedTestsProblemCount());
        addKnownProblemCnt(res, "SNAPSHOT DEPENDENCY ERROR", getSnapshotDepProblemCount());
        addKnownProblemCnt(res, "OTHER", getOtherProblemCount());

        res.insert(0, "ALL [" + build.problemOccurrences.count + "]" + (res.length() != 0 ? ": " : " "));

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
                .append(" ")
                .append(execToCnt > 1 ? "[" + execToCnt + "]" : "");
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof BuildStatisticsSummary))
            return false;

        BuildStatisticsSummary that = (BuildStatisticsSummary)o;

        return Objects.equals(build, that.build) &&
            Objects.equals(problems, that.problems) &&
            Objects.equals(relatedIssues, that.relatedIssues) &&
            Objects.equals(durationPrintable, that.durationPrintable) &&
            Objects.equals(getResult(), that.getResult());
    }

    @Override public int hashCode() {

        return Objects.hash(build, problems, relatedIssues, durationPrintable, getResult());
    }

    @Override public void setUpdateRequired(boolean update) {
        updateRequired = update;
    }
}