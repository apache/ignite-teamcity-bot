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

import com.google.common.base.Strings;
import java.io.UncheckedIOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.GuavaCached;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrence;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.ProblemCompacted;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.tcbot.common.util.FutureUtil;
import org.apache.ignite.ci.web.model.trends.BuildStatisticsSummary;
import org.apache.ignite.ci.web.model.trends.BuildsHistory;
import org.apache.ignite.internal.util.typedef.T2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class MasterTrendsService {
    public static final boolean DEBUG = false;
    @Inject private IStringCompactor compactor;

    @Inject private BuildChainProcessor bcp;

    @Inject private ITeamcityIgnitedProvider tcIgnitedProv;

    @Inject private Provider<BuildsHistory> buildsHistoryProvider;

    @Inject private ITcBotConfig cfg;

    /** */
    private static final Logger logger = LoggerFactory.getLogger(MasterTrendsService.class);

    @NotNull
    @GuavaCached(maximumSize = 500, softValues = true)
    @AutoProfiling
    public BuildStatisticsSummary getBuildSummary(ITeamcityIgnited ignited, int buildId) {
        String msg = "Loading build [" + buildId + "] summary";

        if (DEBUG)
            System.out.println(msg);

        BuildStatisticsSummary buildsStatistic = new BuildStatisticsSummary(buildId);
        initialize(buildsStatistic, ignited);
        return buildsStatistic;
    }

    /** Initialize build statistics. */
    public void initialize(BuildStatisticsSummary s, @Nonnull final ITeamcityIgnited tcIgn) {
        BuildStatisticsSummary.initStrings(compactor);

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

        Date startDate = build.getStartDate();

        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");

        s.startDate = dateFormat.format(startDate);

        int[] arr = new int[4];

        chainBuilds.stream().filter(b -> !b.isComposite())
            .forEach(b -> {
                b.getAllTests().forEach(t -> {
                    if (t.isIgnoredTest())
                        arr[0]++;
                    else if (t.isMutedTest())
                        arr[1]++;
                    else if (t.status() != BuildStatisticsSummary.getStringId(TestOccurrence.STATUS_SUCCESS))
                        arr[2]++;

                    arr[3]++;

                    if (t.status() == BuildStatisticsSummary.getStringId(TestOccurrence.STATUS_FAILURE)
                        && !t.isIgnoredTest() && !t.isMutedTest()) {
                        Map<Integer, T2<Long, Integer>> btTests = s.failedTests().computeIfAbsent(b.buildTypeId(), k -> new HashMap<>());

                        btTests.merge(t.testName(), new T2<>(t.getTestId(), 1), (v, x) -> {
                            int cnt = (x == null ? 0 : x.get2()) + (v == null ? 0 : v.get2());
                            return new T2<>(t.getTestId(), cnt);
                        });
                    }
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
            .filter(b -> b.status() != BuildStatisticsSummary.getStringId(BuildRef.STATUS_SUCCESS));

        s.duration = chainBuilds.stream()
            .filter(b -> !b.isComposite())
            .map(b -> b.buildDuration(compactor))
            .filter(Objects::nonNull)
            .mapToLong(ts -> ts / 1000).sum();

        List<ProblemCompacted> problems = s.getProblems(snapshotDependenciesWithProblems);

        s.totalProblems = s.getBuildTypeProblemsCount(problems);
    }

    /**
     * @param srvCodeParm Server code.
     * @param buildType Build type.
     * @param branch Branch.
     * @param sinceDate Since date.
     * @param untilDate Until date.
     * @param skipTests flag to skip collection of failed tests info.
     * @param prov Prov.
     */
    @NotNull public BuildsHistory getBuildTrends(
        @Nullable String srvCodeParm,
        @Nullable String buildType,
        @Nullable String branch,
        @Nullable String sinceDate,
        @Nullable String untilDate,
        @Nullable String skipTests,
        ITcBotUserCreds prov) throws ParseException {

        String srvCode = Strings.isNullOrEmpty(srvCodeParm) ? cfg.primaryServerCode() : srvCodeParm;

        tcIgnitedProv.checkAccess(srvCode, prov);

        BuildsHistory.Builder builder = new BuildsHistory.Builder(cfg)
            .branch(branch)
            .buildType(buildType)
            .sinceDate(sinceDate)
            .untilDate(untilDate);

        final BuildsHistory instance = buildsHistoryProvider.get();

        BuildsHistory buildsHist = instance.withParameters(builder);

        initializeBuildTrends(buildsHist, srvCode, prov, Boolean.TRUE.equals(Boolean.valueOf(skipTests)));

        return buildsHist;
    }

    /**
     * Initialize {@link BuildsHistory#mergedTestsBySuites} and {@link BuildsHistory#buildsStatistics} properties using builds which satisfy
     * properties setted by Builder.
     *  @param buildsHist output
     * @param srvCode
     * @param prov Credentials.
     * @param skipTests
     */
    public void initializeBuildTrends(BuildsHistory buildsHist, String srvCode,
                                      ITcBotUserCreds prov, boolean skipTests) {
        ITeamcityIgnited ignitedTeamcity = tcIgnitedProv.server(srvCode, prov);

        buildsHist.tcHost = ignitedTeamcity.host();

        List<Integer> finishedBuildsIds = ignitedTeamcity
            .getFinishedBuildsCompacted(buildsHist.buildTypeId,
                buildsHist.branchName,
                buildsHist.sinceDateFilter,
                buildsHist.untilDateFilter)
            .stream().mapToInt(BuildRefCompacted::id).boxed()
            .collect(Collectors.toList());

        Map<Integer, Boolean> buildIdsWithConditions = finishedBuildsIds.stream()
            .collect(Collectors.toMap(v -> v, ignitedTeamcity::buildIsValid, (e1, e2) -> e1, LinkedHashMap::new));

        initStatistics(buildsHist, ignitedTeamcity, buildIdsWithConditions);

        List<Integer> validBuilds = buildIdsWithConditions.keySet()
            .stream()
            .filter(buildIdsWithConditions::get)
            .collect(Collectors.toList());

        if (!skipTests)
            buildsHist.initFailedTests(validBuilds, buildIdsWithConditions, compactor);

        if (DEBUG)
            System.out.println("Preparing response");
    }


    /**
     * Initialize {@link BuildsHistory#buildsStatistics} property with list of {@link BuildStatisticsSummary} produced for each valid
     * build.
     *
     * @param buildsHist output.
     * @param ignited {@link ITeamcityIgnited} instance.
     * @param buildIdsWithConditions Build ID -> build validation flag.
     */
    private void initStatistics(BuildsHistory buildsHist,
        ITeamcityIgnited ignited,
        Map<Integer, Boolean> buildIdsWithConditions) {
        List<Future<BuildStatisticsSummary>> buildStaticsFutures = new ArrayList<>();

        for (int buildId : buildIdsWithConditions.keySet()) {
            Future<BuildStatisticsSummary> buildFut = CompletableFuture.supplyAsync(() -> {
                BuildStatisticsSummary buildsStatistic = getBuildSummary(ignited, buildId);

                buildsStatistic.isValid = buildIdsWithConditions.get(buildId);

                return buildsStatistic;
            });

            buildStaticsFutures.add(buildFut);
        }

        if (MasterTrendsService.DEBUG)
            System.out.println("Waiting for stat to collect");

        buildStaticsFutures.forEach(fut -> {
            try {
                BuildStatisticsSummary buildsStatistic = fut.get();

                if (buildsStatistic != null && !buildsStatistic.isFakeStub)
                    buildsHist.buildsStatistics.add(buildsStatistic);
            }
            catch (ExecutionException e) {
                if (e.getCause() instanceof UncheckedIOException)
                    logger.error(Arrays.toString(e.getStackTrace()));

                else
                    throw new RuntimeException(e);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
