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

package org.apache.ignite.ci.tcbot.chain;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
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
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.SyncMode;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.util.FutureUtil;
import org.apache.ignite.ci.web.TcUpdatePool;
import org.apache.ignite.ci.web.model.long_running.LRTest;
import org.apache.ignite.ci.web.model.long_running.SuiteLRTestsSummary;
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

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * Collects data about all long-running tests (run time more than one minute) across all suites in RunAll chain
     * in master branch.
     *
     * @param teamcityIgnited interface to TC bot database.
     * @param entryPoints
     * @return list of summaries about individual suite runs.
     */
    public List<SuiteLRTestsSummary> loadLongRunningTestsSummary(
        ITeamcityIgnited teamcityIgnited,
        Collection<Integer> entryPoints
    ) {
        final List<SuiteLRTestsSummary> res = new ArrayList<>();

        SyncMode mode = SyncMode.RELOAD_QUEUED;

        if (entryPoints.isEmpty())
            return res;

        Map<Integer, FatBuildCompacted> builds = new ConcurrentHashMap<>();

        final Stream<FatBuildCompacted> entryPointsFatBuilds = entryPoints.stream()
            .filter(Objects::nonNull)
            .filter(id -> !builds.containsKey(id)) //load and propagate only new entry points
            .map(id -> builds.computeIfAbsent(id, teamcityIgnited::getFatBuild));

        final ExecutorService svc = tcUpdatePool.getService();

        final Stream<FatBuildCompacted> depsFirstLevel =
  Stream.of(); //todo implement
            /*entryPointsFatBuilds
            .map(ref -> svc.submit(() -> dependencies(teamcityIgnited, builds, mode, ref)))
            .flatMap(set->set.stream())
            .stream()
            .flatMap(fut -> FutureUtil.getResult(fut));

*/
        depsFirstLevel
            .filter(b -> !b.isComposite() && b.getTestsCount() > 0)
            .forEach(b ->
            {
                List<LRTest> lrTests = new ArrayList<>();

                b.getAllTests()
                    .filter(t -> t.getDuration() > 60 * 1000)
                    .forEach(
                        t -> lrTests
                            .add(new LRTest(t.testName(compactor), t.getDuration(), null))
                    );

                if (!lrTests.isEmpty()) {
                    lrTests.sort((test0, test1) -> {
                        long t0 = test0.time;
                        long t1 = test1.time;

                        if (t0 < t1)
                            return 1;

                        if (t0 == t1)
                            return 0;

                        return -1;
                    });

                    res.add(
                        new SuiteLRTestsSummary(b.buildTypeName(compactor),
                            b.buildDuration(compactor) / b.getTestsCount(),
                            lrTests));
                }
            });

        Collections.sort(res, (s0, s1) -> {
            if (s0.testAvgTime < s1.testAvgTime)
                return 1;

            if (s0.testAvgTime == s1.testAvgTime)
                return 0;

            return -1;
        });

        return res;
    }

    /**
     * @param teamcity Teamcity.
     * @param tcIgn Teamcity Ignited.
     * @param entryPoints Entry point(s): Build(s) to start scan from.
     * @param includeLatestRebuild Include latest rebuild.
     * @param procLog Process logger.
     * @param includeScheduledInfo Include scheduled info.
     * @param failRateBranch Fail rate branch.
     * @param mode background data update mode.
     */
    @AutoProfiling
    public FullChainRunCtx loadFullChainContext(
        IAnalyticsEnabledTeamcity teamcity,
        ITeamcityIgnited tcIgn,
        Collection<Integer> entryPoints,
        LatestRebuildMode includeLatestRebuild,
        ProcessLogsMode procLog,
        boolean includeScheduledInfo,
        @Nullable String failRateBranch,
        SyncMode mode) {

        if (entryPoints.isEmpty())
            return new FullChainRunCtx(Build.createFakeStub());

        Map<Integer, Future<FatBuildCompacted>> builds = new ConcurrentHashMap<>();

        Stream<Future<FatBuildCompacted>> entryPointsFatBuilds = entryPoints.stream()
            .filter(Objects::nonNull)
            .map(id -> builds.computeIfAbsent(id, id0 -> loadBuildAsync(id0, mode, tcIgn)));

        Stream<Integer> dependenciesFirstLevel = entryPointsFatBuilds
            .flatMap(ref -> dependencies(ref, mode, builds, tcIgn).stream());

        Stream<Integer> depsSecondLevel = dependenciesFirstLevel.map(builds::get)
            .peek(val -> Preconditions.checkNotNull(val, "Build future should be in context"))
            .flatMap(ref -> dependencies(ref, mode, builds, tcIgn).stream());

        Set<Integer> collect = depsSecondLevel.collect(Collectors.toSet());
        System.err.println("New deps of second level:" + collect);

        // builds may became non unique because of race in filtering and acquiring deps
        //todo implement
    /*    final List<Future<Stream<FatBuildCompacted>>> phase3Submitted = secondLevelDeps
                .map((fatBuild) -> svc.submit(
                        () -> replaceWithRecent(tcIgn, includeLatestRebuild, mode, builds, fatBuild, entryPoints.size())))
                .collect(Collectors.toList());*/

        Map<String, MultBuildRunCtx> buildsByBt = new ConcurrentHashMap<>();

        builds.values().stream()
                .map(FutureUtil::getResult)
                .forEach((fatBuild) -> createCxt(tcIgn, buildsByBt, fatBuild));

        ArrayList<MultBuildRunCtx> contexts = new ArrayList<>(buildsByBt.values());

        contexts.forEach(multiCtx -> {
            analyzeTests(multiCtx, teamcity, procLog);

            fillBuildCounts(multiCtx, tcIgn, includeScheduledInfo);
        });

        Function<MultBuildRunCtx, Float> function = ctx -> {
            SuiteInBranch key = new SuiteInBranch(ctx.suiteId(), normalizeBranch(failRateBranch));

            //todo place RunStat into suite context to compare
            RunStat runStat = teamcity.getBuildFailureRunStatProvider().apply(key);

            if (runStat == null)
                return 0f;

            //some hack to bring timed out suites to top
            return runStat.getCriticalFailRate() * 3.14f + runStat.getFailRate();
        };

        Integer someEntryPnt = entryPoints.iterator().next();
        Future<FatBuildCompacted> build = builds.computeIfAbsent(someEntryPnt, id -> loadBuildAsync(id, mode, tcIgn));
        FullChainRunCtx fullChainRunCtx = new FullChainRunCtx(FutureUtil.getResult(build).toBuild(compactor));

        contexts.sort(Comparator.comparing(function).reversed());

        fullChainRunCtx.addAllSuites(contexts);

        return fullChainRunCtx;
    }


    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    protected void createCxt(ITeamcityIgnited teamcityIgnited,
                             Map<String, MultBuildRunCtx> buildsCtxMap,
                             FatBuildCompacted buildCompacted) {
        final BuildRef ref = buildCompacted.toBuildRef(compactor);

        if (buildCompacted.isFakeStub() || ref.isFakeStub())
            return;

        final MultBuildRunCtx ctx = buildsCtxMap.computeIfAbsent(ref.buildTypeId,
                k -> new MultBuildRunCtx(ref, compactor));

        ctx.addBuild(loadChanges(buildCompacted, teamcityIgnited));
    }

    /**
     * Runs deep collection of all related statistics for particular build.
     *
     * @param buildCompacted Build ref from history with references to tests.
     * @param tcIgnited
     * @return Full context.
     */
    public SingleBuildRunCtx loadChanges(@Nonnull FatBuildCompacted buildCompacted,
                                                  ITeamcityIgnited tcIgnited) {
        SingleBuildRunCtx ctx = new SingleBuildRunCtx(buildCompacted, compactor);

        ctx.setChanges(tcIgnited.getAllChanges(buildCompacted.changes()));

        return ctx;
    }

    @SuppressWarnings("WeakerAccess")
    @NotNull
    @AutoProfiling
    protected Stream<FatBuildCompacted> replaceWithRecent(ITeamcityIgnited teamcityIgnited,
        LatestRebuildMode includeLatestRebuild,
        SyncMode syncMode,
        Map<Integer, FatBuildCompacted> builds,
        FatBuildCompacted buildCompacted,
        int cntLimit) {
        if (includeLatestRebuild == LatestRebuildMode.NONE)
            return Stream.of(buildCompacted);

        final String branch = getBranchOrDefault(buildCompacted.branchName(compactor));

        final String buildTypeId = buildCompacted.buildTypeId(compactor);
        Stream<BuildRefCompacted> hist = teamcityIgnited.getAllBuildsCompacted(buildTypeId, branch)
            .stream()
            .filter(t -> !t.isCancelled(compactor))
            .filter(t -> t.isFinished(compactor));

        if (includeLatestRebuild == LatestRebuildMode.LATEST) {
            BuildRefCompacted recentRef = hist.max(Comparator.comparing(BuildRefCompacted::id))
                    .orElse(buildCompacted);

            return Stream.of(recentRef)
                .map(b -> builds.computeIfAbsent(b.id(), id -> FutureUtil.getResult(loadBuildAsync(id, syncMode, teamcityIgnited))));
        }

        if (includeLatestRebuild == LatestRebuildMode.ALL) {
            return hist
                .sorted(Comparator.comparing(BuildRefCompacted::id).reversed())
                .limit(cntLimit)
               // .filter(b -> !builds.containsKey(b.id())) // todo removing this causes incorrect count of failures (duplicated builds)
                .map(b -> builds.computeIfAbsent(b.id(), id -> FutureUtil.getResult(loadBuildAsync(id, syncMode, teamcityIgnited))));
        }

        throw new UnsupportedOperationException("invalid mode " + includeLatestRebuild);
    }

    @NotNull private static String getBranchOrDefault(@Nullable String branchName) {
        return branchName == null ? ITeamcity.DEFAULT : branchName;
    }

    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    protected void fillBuildCounts(MultBuildRunCtx outCtx,
        ITeamcityIgnited teamcityIgnited, boolean includeScheduledInfo) {
        if (includeScheduledInfo && !outCtx.hasScheduledBuildsInfo()) {
            final List<BuildRefCompacted> runAllBuilds = teamcityIgnited.getAllBuildsCompacted(outCtx.buildTypeId(), outCtx.branchName());

            long cntRunning = runAllBuilds
                    .stream()
                    .filter(r -> r.isNotCancelled(compactor))
                    .filter(r -> r.isRunning(compactor)).count();

            outCtx.setRunningBuildCount((int) cntRunning);

            long cntQueued =  runAllBuilds
                    .stream()
                    .filter(r -> r.isNotCancelled(compactor))
                    .filter(r -> r.isQueued(compactor)).count();

            outCtx.setQueuedBuildCount((int) cntQueued);
        }
    }

    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    protected void analyzeTests(MultBuildRunCtx outCtx, IAnalyticsEnabledTeamcity teamcity,
                                     ProcessLogsMode procLog) {
        for (SingleBuildRunCtx ctx : outCtx.getBuilds()) {
            tcUpdatePool.getService().submit(() -> {
                teamcity.calculateBuildStatistic(ctx);
            });

            if ((procLog == ProcessLogsMode.SUITE_NOT_COMPLETE && ctx.hasSuiteIncompleteFailure())
                    || procLog == ProcessLogsMode.ALL)
                ctx.setLogCheckResFut(teamcity.analyzeBuildLog(ctx.buildId(), ctx));
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

    /**
     * @param buildFut Chain build future.
     * @param mode Mode.
     * @param builds Builds.
     * @param teamcityIgnited Teamcity ignited.
     * @return Set of new builds found during this dependencies check round.
     */
    @NotNull
    private Set<Integer> dependencies(
        Future<FatBuildCompacted> buildFut,
        SyncMode mode,
        Map<Integer, Future<FatBuildCompacted>> builds,
        ITeamcityIgnited teamcityIgnited) {
        Set<Integer> newBuilds = new HashSet<>();

        IntStream.of(FutureUtil.getResult(buildFut).snapshotDependencies())
            .forEach(id -> builds.computeIfAbsent(id, id0 -> {
                newBuilds.add(id0);

                return loadBuildAsync(id0, mode, teamcityIgnited);
            }));

        return newBuilds;
    }

    public Future<FatBuildCompacted> loadBuildAsync(Integer id, SyncMode mode, ITeamcityIgnited teamcityIgnited) {
        if (mode == SyncMode.NONE)
            return Futures.immediateFuture(teamcityIgnited.getFatBuild(id, SyncMode.NONE));

        return tcUpdatePool.getService().submit(() -> {
            return teamcityIgnited.getFatBuild(id, mode);
        });
    }
}
