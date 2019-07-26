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

package org.apache.ignite.tcbot.engine.chain;

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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.ParametersCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.util.FutureUtil;
import org.apache.ignite.tcbot.engine.pool.TcUpdatePool;
import org.apache.ignite.tcbot.engine.ui.LrTestUi;
import org.apache.ignite.tcbot.engine.ui.LrTestsSuiteSummaryUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.buildlog.IBuildLogProcessor;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.history.RunHistSync;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.Build;
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
     * Collects data about all long-running tests (run time more than one minute) across all suites in RunAll chain in
     * master branch.
     *
     * @param teamcityIgnited interface to TC bot database.
     * @param entryPoints
     * @return list of summaries about individual suite runs.
     */
    public List<LrTestsSuiteSummaryUi> loadLongRunningTestsSummary(
        ITeamcityIgnited teamcityIgnited,
        Collection<Integer> entryPoints
    ) {
        final List<LrTestsSuiteSummaryUi> res = new ArrayList<>();

        SyncMode mode = SyncMode.RELOAD_QUEUED;

        if (entryPoints.isEmpty())
            return res;

        Map<Integer, Future<FatBuildCompacted>> builds = loadAllBuildsInChains(entryPoints, mode, teamcityIgnited);

        builds.values().stream().map(FutureUtil::getResult)
            .filter(b -> !b.isComposite() && b.getTestsCount() > 0)
            .forEach(b ->
            {
                List<LrTestUi> lrTests = new ArrayList<>();

                b.getAllTests()
                    .filter(t -> {
                        Integer duration = t.getDuration();

                        return duration !=null && duration > 60 * 1000;
                    })
                    .forEach(
                        t -> lrTests
                            .add(new LrTestUi(t.testName(compactor), t.getDuration(), null))
                    );

                if (!lrTests.isEmpty()) {
                    lrTests.sort((test0, test1) -> {
                        long t0 = test0.time;
                        long t1 = test1.time;

                        return Long.compare(t1, t0);

                    });

                    res.add(
                        new LrTestsSuiteSummaryUi(b.buildTypeName(compactor),
                            b.buildDuration(compactor) / b.getTestsCount(),
                            lrTests));
                }
            });

        res.sort((s0, s1) -> Long.compare(s1.testAvgTime, s0.testAvgTime));

        return res;
    }

    /**
     * @param tcIgn Teamcity Ignited.
     * @param entryPoints Entry point(s): Build(s) to start scan from.
     * @param includeLatestRebuild Include latest rebuild.
     * @param procLog Process logger.
     * @param includeScheduledInfo Include scheduled info.
     * @param failRateBranch Fail rate branch.
     * @param mode background data update mode.
     * @param sortOption how to sort suites in context, default is by failure rate (most often - first).
     */
    @AutoProfiling
    public FullChainRunCtx loadFullChainContext(
        ITeamcityIgnited tcIgn,
        Collection<Integer> entryPoints,
        LatestRebuildMode includeLatestRebuild,
        ProcessLogsMode procLog,
        boolean includeScheduledInfo,
        @Nullable String failRateBranch,
        SyncMode mode,
        @Nullable SortOption sortOption) {

        if (entryPoints.isEmpty())
            return new FullChainRunCtx(Build.createFakeStub());

        Integer failRateBranchId = compactor.getStringIdIfPresent(RunHistSync.normalizeBranch(failRateBranch));

        Map<Integer, Future<FatBuildCompacted>> builds = loadAllBuildsInChains(entryPoints, mode, tcIgn);

        Map<String, List<Future<FatBuildCompacted>>> freshRebuilds = new ConcurrentHashMap<>();

        groupByBuildType(builds).forEach(
            (k, buildsForBt) -> {
                List<Future<FatBuildCompacted>> futures = replaceWithRecent(buildsForBt,
                    entryPoints.size(),
                    includeLatestRebuild,
                    builds,
                    mode,
                    tcIgn);

                freshRebuilds.put(k, futures);
            }
        );

        List<MultBuildRunCtx> contexts = new ArrayList<>(freshRebuilds.size());

        freshRebuilds.forEach((bt, listBuilds) -> {
            List<FatBuildCompacted> buildsForSuite = FutureUtil.getResults(listBuilds)
                .filter(buildCompacted -> !buildCompacted.isFakeStub())
                .collect(Collectors.toList());

            if (buildsForSuite.isEmpty())
                return;

            BuildRef ref = buildsForSuite.iterator().next().toBuildRef(compactor);

            final MultBuildRunCtx ctx = new MultBuildRunCtx(ref, compactor);

            buildsForSuite.forEach(buildCompacted -> ctx.addBuild(loadChanges(buildCompacted, tcIgn)));

            //ask for history for the suite in parallel
            tcUpdatePool.getService().submit(() -> {
                ctx.history(tcIgn, failRateBranchId);
            });

            analyzeTests(ctx, tcIgn, procLog);

            fillBuildCounts(ctx, tcIgn, includeScheduledInfo);

            contexts.add(ctx);
        });


        Integer someEntryPnt = entryPoints.iterator().next();
        Future<FatBuildCompacted> build = getOrLoadBuild(someEntryPnt, mode, builds, tcIgn);
        FullChainRunCtx fullChainRunCtx = new FullChainRunCtx(FutureUtil.getResult(build).toBuild(compactor));

        Function<MultBuildRunCtx, Double> function = null;

        if (sortOption == null || sortOption == SortOption.FailureRate) {
            function = ctx -> {
                IRunHistory runStat = ctx.history(tcIgn, failRateBranchId);

                if (runStat == null)
                    return 0d;

                //some hack to bring timed out suites to top
                return runStat.getCriticalFailRate() * 3.14d + runStat.getFailRate();
            };
        }
        else if (sortOption == SortOption.SuiteDuration) {
            function = ctx -> {
                Long duration = ctx.buildDuration();

                return duration == null ? 0 : (double)duration;
            };
        }

        if (function != null)
            contexts.sort(Comparator.comparing(function).reversed());

        fullChainRunCtx.addAllSuites(contexts);

        return fullChainRunCtx;
    }

    @Nonnull
    public Map<Integer, Future<FatBuildCompacted>> loadAllBuildsInChains(Collection<Integer> entryPoints,
        SyncMode mode,
        ITeamcityIgnited tcIgn) {
        Map<Integer, Future<FatBuildCompacted>> builds = new ConcurrentHashMap<>();

        Stream<Future<FatBuildCompacted>> entryPointsFatBuilds = entryPoints.stream()
            .filter(Objects::nonNull)
            .map(id -> getOrLoadBuild(id, mode, builds, tcIgn));

        Set<Integer> remainedUnloadedDeps = entryPointsFatBuilds
            .flatMap(ref -> dependencies(ref, mode, builds, tcIgn).stream()).collect(Collectors.toSet());

        for (int level = 1; level < 5; level++) {
            if (remainedUnloadedDeps.isEmpty())
                break;

            Set<Integer> depsNextLevel = remainedUnloadedDeps
                .stream()
                .map(builds::get)
                .peek(val -> Preconditions.checkNotNull(val, "Build future should be in context"))
                .flatMap(ref -> dependencies(ref, mode, builds, tcIgn).stream()).collect(Collectors.toSet());

            if(logger.isDebugEnabled())
                logger.debug("Level [" + level + "] dependencies:" + depsNextLevel);

            remainedUnloadedDeps = depsNextLevel;
        }

        return builds;
    }

    @Nonnull
    public Map<String, List<FatBuildCompacted>> groupByBuildType(Map<Integer, Future<FatBuildCompacted>> builds) {
        Map<String, List<FatBuildCompacted>> buildsByBt = new ConcurrentHashMap<>();
        builds.values().forEach(bFut -> {
            FatBuildCompacted b = FutureUtil.getResult(bFut);

            String buildTypeId = b.buildTypeId(compactor);
            if (buildTypeId == null)
                logger.error("Invalid build type ID for build " + b.getId());
            else
                buildsByBt.computeIfAbsent(buildTypeId, k -> new ArrayList<>()).add(b);
        });
        return buildsByBt;
    }

    public Future<FatBuildCompacted> getOrLoadBuild(Integer id, SyncMode mode,
        Map<Integer, Future<FatBuildCompacted>> builds, ITeamcityIgnited tcIgn) {
        return builds.computeIfAbsent(id, id0 -> loadBuildAsync(id0, mode, tcIgn));
    }

    /**
     * Runs deep collection of all related statistics for particular build.
     *
     * @param buildCompacted Build ref from history with references to tests.
     * @param tcIgnited TC connection.
     * @return Full context.
     */
    public SingleBuildRunCtx loadChanges(@Nonnull FatBuildCompacted buildCompacted,
        ITeamcityIgnited tcIgnited) {
        SingleBuildRunCtx ctx = new SingleBuildRunCtx(buildCompacted, compactor);

        ctx.setChanges(tcIgnited.getAllChanges(buildCompacted.changes()));

        ParametersCompacted parameters = buildCompacted.parameters();
        if (parameters != null)
            ctx.addTagsFromParameters(parameters, tcIgnited.config(), this.compactor);

        return ctx;
    }

    @SuppressWarnings("WeakerAccess")
    @Nonnull
    @AutoProfiling
    protected List<Future<FatBuildCompacted>> replaceWithRecent(List<FatBuildCompacted> builds,
        int cntLimit,
        LatestRebuildMode includeLatestRebuild,
        Map<Integer, Future<FatBuildCompacted>> allBuildsMap,
        SyncMode syncMode,
        ITeamcityIgnited tcIgn) {
        if (includeLatestRebuild == LatestRebuildMode.NONE || builds.isEmpty())
            return completed(builds);

        Optional<FatBuildCompacted> maxIdBuildOpt = builds.stream()
            .filter(b -> b.branchName() >= 0)
            .max(Comparator.comparing(BuildRefCompacted::id));
        if (!maxIdBuildOpt.isPresent())
            return completed(builds);

        FatBuildCompacted freshBuild = maxIdBuildOpt.get();

        final String branch = freshBuild.branchName(compactor);

        final String buildTypeId = freshBuild.buildTypeId(compactor);
        Stream<BuildRefCompacted> hist = tcIgn.getAllBuildsCompacted(buildTypeId, branch)
            .stream()
            .filter(bref -> !bref.isCancelled(compactor))
            .filter(bref -> bref.isFinished(compactor));

        if (includeLatestRebuild == LatestRebuildMode.LATEST) {
            BuildRefCompacted recentRef = hist.max(Comparator.comparing(BuildRefCompacted::id))
                .orElse(freshBuild);

            return Collections.singletonList(
                getOrLoadBuild(recentRef.id(), syncMode, allBuildsMap, tcIgn));
        }

        if (includeLatestRebuild == LatestRebuildMode.ALL) {
            return hist
                .sorted(Comparator.comparing(BuildRefCompacted::id).reversed())
                .limit(cntLimit)
                .map(bref -> getOrLoadBuild(bref.id(), syncMode, allBuildsMap, tcIgn))
                .collect(Collectors.toList());
        }

        throw new UnsupportedOperationException("invalid mode " + includeLatestRebuild);
    }

    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    protected void fillBuildCounts(MultBuildRunCtx outCtx,
        ITeamcityIgnited teamcityIgnited, boolean includeScheduledInfo) {
        if (includeScheduledInfo && !outCtx.hasScheduledBuildsInfo()) {
            List<BuildRefCompacted> runAllBuilds = teamcityIgnited.getAllBuildsCompacted(outCtx.suiteId(), outCtx.branchName());

            long cntRunning = runAllBuilds
                .stream()
                .filter(r -> r.isNotCancelled(compactor))
                .filter(r -> r.isRunning(compactor)).count();

            outCtx.setRunningBuildCount((int)cntRunning);

            long cntQueued = runAllBuilds
                .stream()
                .filter(r -> r.isNotCancelled(compactor))
                .filter(r -> r.isQueued(compactor)).count();

            outCtx.setQueuedBuildCount((int)cntQueued);
        }
    }

    @Inject IBuildLogProcessor buildLogProcessor;

    @SuppressWarnings("WeakerAccess")
    @AutoProfiling
    protected void analyzeTests(MultBuildRunCtx outCtx, ITeamcityIgnited teamcity,
                                ProcessLogsMode procLog) {
        for (SingleBuildRunCtx ctx : outCtx.getBuilds()) {
            boolean incompleteFailure = ctx.hasSuiteIncompleteFailure();
            if ((procLog == ProcessLogsMode.SUITE_NOT_COMPLETE && incompleteFailure)
                    || procLog == ProcessLogsMode.ALL)
                ctx.setLogCheckResFut(
                        CompletableFuture.supplyAsync(
                                () -> buildLogProcessor.analyzeBuildLog(teamcity,
                                        ctx.buildId(),
                                    incompleteFailure),
                                tcUpdatePool.getService()));
        }
    }

    /**
     * @param buildFut Chain build future.
     * @param mode Mode.
     * @param builds Build futures map.
     * @param teamcityIgnited Teamcity ignited.
     * @return Set of new builds found during this dependencies check round.
     */
    @Nonnull
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

        return tcUpdatePool.getService().submit(() -> teamcityIgnited.getFatBuild(id, mode));
    }

    private List<Future<FatBuildCompacted>> completed(List<FatBuildCompacted> builds) {
        return builds.stream().map(Futures::immediateFuture).collect(Collectors.toList());
    }
}
