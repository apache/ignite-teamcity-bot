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

package org.apache.ignite.ci.tcbot.trends;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.cache.GuavaCached;
import org.apache.ignite.ci.tcbot.chain.BuildChainProcessor;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.SyncMode;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProblemCompacted;
import org.apache.ignite.ci.util.FutureUtil;
import org.apache.ignite.ci.web.model.current.BuildStatisticsSummary;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.ci.tcmodel.hist.BuildRef.STATUS_SUCCESS;
import static org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence.TC_EXECUTION_TIMEOUT;
import static org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence.TC_EXIT_CODE;
import static org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence.TC_JVM_CRASH;
import static org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence.TC_OOME;

/**
 *
 */
public class MasterTrendsService {
    public static final boolean DEBUG = false;
    @Inject private IStringCompactor compactor;

    @Inject private BuildChainProcessor bcp;

    @NotNull
    @GuavaCached(maximumSize = 500, softValues = true)
    @AutoProfiling
    public BuildStatisticsSummary getBuildSummary(ITeamcityIgnited ignited, int buildId) {
        String msg = "Loading build [" + buildId + "] summary";
        System.out.println(msg);

        BuildStatisticsSummary buildsStatistic = new BuildStatisticsSummary(buildId);
        initialize(buildsStatistic, ignited);
        return buildsStatistic;
    }

    /** Initialize build statistics. */
    public void initialize(BuildStatisticsSummary s, @Nonnull final ITeamcityIgnited tcIgn) {
        if (s.strIds.isEmpty()) {
            s.strIds.put(STATUS_SUCCESS, compactor.getStringId(STATUS_SUCCESS));
            s.strIds.put(TC_EXIT_CODE, compactor.getStringId(TC_EXIT_CODE));
            s.strIds.put(TC_OOME, compactor.getStringId(TC_OOME));
            s.strIds.put(TC_JVM_CRASH, compactor.getStringId(TC_JVM_CRASH));
            s.strIds.put(TC_EXECUTION_TIMEOUT, compactor.getStringId(TC_EXECUTION_TIMEOUT));
        }

        FatBuildCompacted build = tcIgn.getFatBuild(s.buildId);

        s.isFakeStub = build.isFakeStub();

        if (s.isFakeStub)
            return;

        Map<Integer, Future<FatBuildCompacted>> builds = bcp.loadAllBuildsInChains(
            Collections.singletonList(s.buildId), SyncMode.RELOAD_QUEUED, tcIgn);

        List<FatBuildCompacted> chainBuilds = FutureUtil.getResults(builds.values()).collect(Collectors.toList());

        if (chainBuilds.stream().allMatch(b -> build.isFakeStub())) {
            s.isFakeStub = true;
            return;
        }

        Date startDate = FutureUtil.getResult(builds.get(s.buildId)).getStartDate();

        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");

        s.startDate = dateFormat.format(startDate);

        int[] arr = new int[4];

        chainBuilds.stream().filter(b -> !b.isComposite())
            .forEach(b -> {
                b.getAllTests().forEach(t -> {
                    if (t.getIgnoredFlag())
                        arr[0]++;
                    else if (t.getMutedFlag())
                        arr[1]++;
                    else if (t.status() != s.strIds.get(STATUS_SUCCESS))
                        arr[2]++;

                    arr[3]++;
                });
            });

        s.testOccurrences.ignored = arr[0];
        s.testOccurrences.muted = arr[1];
        s.testOccurrences.failed = arr[2];
        s.testOccurrences.count = arr[3];
        s.testOccurrences.passed = s.testOccurrences.count - s.testOccurrences.failed - s.testOccurrences.ignored -
            s.testOccurrences.muted;

        Stream<FatBuildCompacted> snapshotDependenciesWithProblems
            = chainBuilds.stream()
            .filter(b -> !b.isComposite())
            .filter(b -> b.status() != BuildStatisticsSummary.strIds.get(STATUS_SUCCESS));

        s.duration = chainBuilds.stream()
            .filter(b -> !b.isComposite())
            .map(b -> b.buildDuration(compactor))
            .filter(Objects::nonNull)
            .mapToLong(ts -> ts / 1000).sum();

        List<ProblemCompacted> problems = s.getProblems(snapshotDependenciesWithProblems);

        s.totalProblems = s.getBuildTypeProblemsCount(problems);
    }


}
