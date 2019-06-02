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

package org.apache.ignite.ci.web.model.trends;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.TestOccurrencesRef;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrence;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProblemCompacted;
import org.apache.ignite.internal.util.typedef.T2;

import static org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence.TC_EXECUTION_TIMEOUT;
import static org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence.TC_EXIT_CODE;
import static org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence.TC_JVM_CRASH;
import static org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence.TC_OOME;

/**
 * Summary of build statistics.
 */
@SuppressWarnings("PublicField") public class BuildStatisticsSummary {
    /** String ids. */
    private static final Map<String, Integer> strIds = new ConcurrentHashMap<>();

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

    /** Duration (seconds). */
    public long duration;

    /** Short build run result (without snapshot-dependencies printable result). */
    public Map<String, Long> totalProblems;

    /** Is fake stub. */
    public boolean isFakeStub;

    /**
     * Failed tests: Map from build type string ID -> Map of test name (full) string ID -> (test refrenence, to count of
     * failures).
     */
    private Map<Integer, Map<Integer, T2<Long, Integer>>> failedTests = new HashMap<>();

    /** Is valid. */
    public boolean isValid = true;

    /**
     * @param buildId Build id.
     */
    public BuildStatisticsSummary(Integer buildId) {
        this.buildId = buildId;
    }

    /**
     * @param compactor Compactor.
     */
    public static void initStrings(IStringCompactor compactor) {
        if (strIds.isEmpty()) {
            synchronized (BuildStatisticsSummary.class) {
                if (strIds.isEmpty()) {
                    strIds.put(TestOccurrence.STATUS_SUCCESS, compactor.getStringId(TestOccurrence.STATUS_SUCCESS));
                    strIds.put(TestOccurrence.STATUS_FAILURE, compactor.getStringId(TestOccurrence.STATUS_FAILURE));
                    strIds.put(TC_EXIT_CODE, compactor.getStringId(TC_EXIT_CODE));
                    strIds.put(TC_OOME, compactor.getStringId(TC_OOME));
                    strIds.put(TC_JVM_CRASH, compactor.getStringId(TC_JVM_CRASH));
                    strIds.put(TC_EXECUTION_TIMEOUT, compactor.getStringId(TC_EXECUTION_TIMEOUT));
                    //key is the same with tests. strIds.put(BuildRef.STATUS_SUCCESS, compactor.getStringId(BuildRef.STATUS_SUCCESS));
                }
            }
        }
    }

    public static int getStringId(String failure) {
        Preconditions.checkState(!strIds.isEmpty());

        Integer integer = strIds.get(failure);

        return Preconditions.checkNotNull(integer, "No data for [" + failure + "]");
    }

    /**
     * @param problemName Problem name.
     * @param problems
     */
    private long getProblemsCount(String problemName, List<ProblemCompacted> problems) {
        if (problems == null)
            return 0;

        return problems.stream()
            .filter(Objects::nonNull)
            .filter(p -> p.type() == strIds.get(problemName)).count();
    }

    /**
     * Problems for all snapshot-dependencies.
     *
     * @param builds Builds.
     */
    public List<ProblemCompacted> getProblems(Stream<FatBuildCompacted> builds) {
        return builds.flatMap(build -> build.problems().stream()).collect(Collectors.toList());
    }

    /**
     * Builds without status "Success".
     */
    private List<FatBuildCompacted> getBuildsWithProblems(List<FatBuildCompacted> builds) {
        return builds.stream()
            .filter(b -> b.status() != strIds.get(BuildRef.STATUS_SUCCESS))
            .collect(Collectors.toList());
    }

    /**
     * BuildType problems count (EXECUTION TIMEOUT, JVM CRASH, OOMe, EXIT CODE, TOTAL PROBLEMS COUNT).
     *
     * @param problems
     */
    public Map<String, Long> getBuildTypeProblemsCount(
        List<ProblemCompacted> problems) {
        Map<String, Long> occurrences = new HashMap<>();

        occurrences.put(shortProblemNames.get(TC_EXECUTION_TIMEOUT), getProblemsCount(TC_EXECUTION_TIMEOUT, problems));
        occurrences.put(shortProblemNames.get(TC_JVM_CRASH), getProblemsCount(TC_JVM_CRASH, problems));
        occurrences.put(shortProblemNames.get(TC_OOME), getProblemsCount(TC_OOME, problems));
        occurrences.put(shortProblemNames.get(TC_EXIT_CODE), getProblemsCount(TC_EXIT_CODE, problems));
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
            Objects.equals(duration, that.duration) &&
            Objects.equals(totalProblems, that.totalProblems);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(buildId, startDate, testOccurrences,
            duration, totalProblems, isFakeStub);
    }

    public Map<Integer, Map<Integer, T2<Long, Integer>>> failedTests() {
        return failedTests;
    }
}
