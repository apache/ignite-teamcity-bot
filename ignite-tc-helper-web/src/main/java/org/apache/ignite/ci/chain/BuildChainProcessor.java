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

package org.apache.ignite.ci.chain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.util.FutureUtil;
import org.apache.ignite.ci.web.TcUpdatePool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process whole Build Chain, E.g. runAll at particular server, including all builds involved
 */
public class BuildChainProcessor {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(BuildChainProcessor.class);

    /** TC REST updates pool. */
    @Inject private TcUpdatePool tcUpdatePool;

    /**
     * @param teamcity Teamcity.
     * @param entryPoints Entry points.
     * @param includeLatestRebuild Include latest rebuild.
     * @param procLog Process logger.
     * @param includeScheduledInfo Include scheduled info.
     * @param failRateBranch Fail rate branch.
     */
    @AutoProfiling
    public FullChainRunCtx loadFullChainContext(
        IAnalyticsEnabledTeamcity teamcity,
        Collection<BuildRef> entryPoints,
        LatestRebuildMode includeLatestRebuild,
        ProcessLogsMode procLog,
        boolean includeScheduledInfo,
        @Nullable String failRateBranch) {

        if (entryPoints.isEmpty())
            return new FullChainRunCtx(Build.createFakeStub());

        BuildRef next = entryPoints.iterator().next();
        Build results = teamcity.getBuild(next.href);
        FullChainRunCtx fullChainRunCtx = new FullChainRunCtx(results);

        Map<Integer, BuildRef> unique = new ConcurrentHashMap<>();
        Map<String, MultBuildRunCtx> buildsCtxMap = new ConcurrentHashMap<>();

        Stream<? extends BuildRef> uniqueBuldsInvolved = entryPoints.stream()
                .parallel()
                .unordered()
                .flatMap(ref -> dependencies(teamcity, ref)).filter(Objects::nonNull)
                .flatMap(ref -> dependencies(teamcity, ref)).filter(Objects::nonNull)
                .filter(ref -> ensureUnique(unique, ref))
                ;

        final ExecutorService svc = tcUpdatePool.getService();

        final List<Future<Stream<BuildRef>>> phase1Submitted = uniqueBuldsInvolved
                .map((buildRef) -> svc.submit(
                        () -> replaceWithRecent(teamcity, includeLatestRebuild, unique, buildRef, entryPoints.size())))
                .collect(Collectors.toList());

        final List<Future<? extends Stream<? extends BuildRef>>> phase2Submitted = phase1Submitted.stream()
                .map(FutureUtil::getResult)
                .map((s) -> svc.submit(
                        () -> processBuildList(teamcity, buildsCtxMap, s)))
                .collect(Collectors.toList());

        phase2Submitted.forEach(FutureUtil::getResult);

        ArrayList<MultBuildRunCtx> contexts = new ArrayList<>(buildsCtxMap.values());

        contexts.forEach(multiCtx -> {
            analyzeTests(multiCtx, teamcity, procLog);

            fillBuildCounts(multiCtx, teamcity, includeScheduledInfo);
        });

        if (teamcity != null) {
            Function<MultBuildRunCtx, Float> function = ctx -> {
                SuiteInBranch key = new SuiteInBranch(ctx.suiteId(), normalizeBranch(failRateBranch));

                RunStat runStat = teamcity.getBuildFailureRunStatProvider().apply(key);

                if (runStat == null)
                    return 0f;

                //some hack to bring timed out suites to top
                return runStat.getCriticalFailRate() * 3.14f + runStat.getFailRate();
            };

            contexts.sort(Comparator.comparing(function).reversed());
        }
        else
            contexts.sort(Comparator.comparing(MultBuildRunCtx::suiteName));

        fullChainRunCtx.addAllSuites(contexts);

        return fullChainRunCtx;
    }

    @NotNull
    public static Stream<? extends BuildRef> processBuildList(ITeamcity teamcity,
                                                              Map<String, MultBuildRunCtx> buildsCtxMap,
                                                              Stream<? extends BuildRef> list) {
        list.forEach((BuildRef ref) -> {
            processBuildAndAddToCtx(teamcity, buildsCtxMap, ref);
        });

        return list;
    }

    public static void processBuildAndAddToCtx(ITeamcity teamcity, Map<String, MultBuildRunCtx> buildsCtxMap, BuildRef buildRef) {
        Build build = teamcity.getBuild(buildRef.href);

        if (build == null || build.isFakeStub())
            return;

        MultBuildRunCtx ctx = buildsCtxMap.computeIfAbsent(build.buildTypeId, k -> new MultBuildRunCtx(build));

        ctx.addBuild(teamcity.loadTestsAndProblems(build, ctx));
    }

    @NotNull
    public static Stream< BuildRef> replaceWithRecent(ITeamcity teamcity,
                                                      LatestRebuildMode includeLatestRebuild,
                                                      Map<Integer, BuildRef> unique, BuildRef buildRef, int cntLimit) {
        if (includeLatestRebuild == LatestRebuildMode.NONE)
            return Stream.of(buildRef);

        final String branch = getBranchOrDefault(buildRef.branchName);

        final List<BuildRef> builds = teamcity.getFinishedBuilds(buildRef.buildTypeId, branch);

        if (includeLatestRebuild == LatestRebuildMode.LATEST) {
            BuildRef recentRef = builds.stream().max(Comparator.comparing(BuildRef::getId)).orElse(buildRef);

            return Stream.of(recentRef.isFakeStub() ? buildRef : recentRef);
        }

        if (includeLatestRebuild == LatestRebuildMode.ALL) {
            return builds.stream()
                .filter(ref -> !ref.isFakeStub())
                .filter(ref -> ensureUnique(unique, ref))
                .sorted(Comparator.comparing(BuildRef::getId).reversed())
                .limit(cntLimit); // applying same limit
        }

        throw new UnsupportedOperationException("invalid mode " + includeLatestRebuild);
    }

    @NotNull private static String getBranchOrDefault(@Nullable String branchName) {
        return branchName == null ? ITeamcity.DEFAULT : branchName;
    }

    private static boolean ensureUnique(Map<Integer, BuildRef> unique, BuildRef ref) {
        if (ref.isFakeStub())
            return false;

        BuildRef prevVal = unique.putIfAbsent(ref.getId(), ref);

        return prevVal == null;
    }

    private static void fillBuildCounts(MultBuildRunCtx outCtx, ITeamcity teamcity, boolean includeScheduledInfo) {
        if (includeScheduledInfo && !outCtx.hasScheduledBuildsInfo()) {
            Function<List<BuildRef>, Long> cntRelatedToThisBuildType = list ->
                list.stream()
                    .filter(ref -> Objects.equals(ref.buildTypeId, outCtx.buildTypeId()))
                    .filter(ref -> Objects.equals(normalizeBranch(outCtx.branchName()), normalizeBranch(ref)))
                    .count();

            outCtx.setRunningBuildCount(teamcity.getRunningBuilds("").thenApply(cntRelatedToThisBuildType));
            outCtx.setQueuedBuildCount(teamcity.getQueuedBuilds("").thenApply(cntRelatedToThisBuildType));
        }
    }

    private static void analyzeTests(MultBuildRunCtx outCtx, IAnalyticsEnabledTeamcity teamcity,
        ProcessLogsMode procLog) {
        for (SingleBuildRunCtx ctx : outCtx.getBuilds()) {
            if (teamcity != null)
                teamcity.calculateBuildStatistic(ctx);

            if ((procLog == ProcessLogsMode.SUITE_NOT_COMPLETE && ctx.hasSuiteIncompleteFailure())
                || procLog == ProcessLogsMode.ALL)
                ctx.setLogCheckResultFut(teamcity.analyzeBuildLog(ctx.buildId(), ctx));
        }
    }

    @NotNull public static String normalizeBranch(@NotNull final BuildRef build) {
        return normalizeBranch(build.branchName);
    }

    @NotNull public static String normalizeBranch(@Nullable String branchName) {
        String branch = getBranchOrDefault(branchName);

        if ("refs/heads/master".equals(branch))
            return ITeamcity.DEFAULT;

        return branch;
    }

    @Nullable private static Stream<? extends BuildRef> dependencies(ITeamcity teamcity, BuildRef ref) {
        Build results = teamcity.getBuild(ref.href);
        if (results == null)
            return Stream.of(ref);

        List<BuildRef> deps = results.getSnapshotDependenciesNonNull();

        if(deps.isEmpty())
            return Stream.of(ref);

        if(logger.isDebugEnabled())
            logger.debug("Snapshot deps found: " +
                ref.suiteId() + "->" + deps.stream().map(BuildRef::suiteId).collect(Collectors.toList()));

        Collection<BuildRef> buildAndDeps = new ArrayList<>(deps);

        buildAndDeps.add(ref);

        return buildAndDeps.stream();
    }
}
