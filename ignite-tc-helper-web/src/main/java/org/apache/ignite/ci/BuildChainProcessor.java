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

package org.apache.ignite.ci;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.util.concurrent.MoreExecutors;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.util.FutureUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildChainProcessor {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(BuildChainProcessor.class);

    /**
     * @param teamcity Teamcity.
     * @param includeLatestRebuild Include latest rebuild.
     * @param builds Builds.
     * @param procLogs Process logs.
     * @param includeScheduled Include scheduled.
     * @param showContacts Show contacts.
     * @param tcAnalytics Tc analytics.
     * @param baseBranch Base branch, stable branch to take fail rates from.
     * @param executor Executor service to process TC requests in it.
     */
    public static Optional<FullChainRunCtx> processBuildChains(
            ITeamcity teamcity,
            LatestRebuildMode includeLatestRebuild,
            Collection<BuildRef> builds,
            ProcessLogsMode procLogs,
            boolean includeScheduled,
            boolean showContacts,
            @Nullable ITcAnalytics tcAnalytics,
            @Nullable String baseBranch,
            @Nullable ExecutorService executor) {

        final Properties responsible = showContacts ? getContactPersonProperties(teamcity) : null;

        final FullChainRunCtx val = loadChainsContext(teamcity, builds,
            includeLatestRebuild,
            procLogs, responsible, includeScheduled, tcAnalytics,
            baseBranch, executor);

        return Optional.of(val);
    }

    @Nullable private static Properties getContactPersonProperties(ITeamcity teamcity) {
        return HelperConfig.loadContactPersons(teamcity.serverId());
    }

    public static <R> FullChainRunCtx loadChainsContext(
            ITeamcity teamcity,
            Collection<BuildRef> entryPoints,
            LatestRebuildMode includeLatestRebuild,
            ProcessLogsMode procLog,
            @Nullable Properties contactPersonProps,
            boolean includeScheduledInfo,
            @Nullable ITcAnalytics tcAnalytics,
            @Nullable String failRateBranch,
            @Nullable ExecutorService executor1) {

        ExecutorService executor = executor1 == null ? MoreExecutors.newDirectExecutorService() : executor1;

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
                .filter(ref -> ensureUnique(unique, ref));

        uniqueBuldsInvolved
                .map((buildRef) -> executor.submit(
                        () -> replaceWithRecent(teamcity, includeLatestRebuild, unique, buildRef, entryPoints.size())))
                .map(FutureUtil::getResult)
                .map((s) -> executor.submit(
                        () -> processBuildList(teamcity, buildsCtxMap, s)))
                .forEach(FutureUtil::getResult);

        ArrayList<MultBuildRunCtx> contexts = new ArrayList<>(buildsCtxMap.values());

        contexts.forEach(multiCtx -> {
            analyzeTests(multiCtx, teamcity, procLog, tcAnalytics);

            fillBuildCounts(multiCtx, teamcity, includeScheduledInfo);

            if (contactPersonProps != null && multiCtx.getContactPerson() == null)
                multiCtx.setContactPerson(contactPersonProps.getProperty(multiCtx.suiteId()));
        });

        if (tcAnalytics != null) {
            Function<MultBuildRunCtx, Float> function = ctx -> {
                SuiteInBranch key = new SuiteInBranch(ctx.suiteId(), normalizeBranch(failRateBranch));

                RunStat runStat = tcAnalytics.getBuildFailureRunStatProvider().apply(key);

                if (runStat == null)
                    return 0f;

                //some hack to bring timed out suites to top
                return runStat.getCriticalFailRate() * 3.14f + runStat.getFailRate();
            };

            contexts.sort(Comparator.comparing(function).reversed());
        }
        else if (contactPersonProps != null)
            contexts.sort(Comparator.comparing(MultBuildRunCtx::getContactPersonOrEmpty));
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
                                                      Map<Integer, BuildRef> unique, BuildRef buildRef, int countLimit) {
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
                .limit(countLimit); // applying same limit
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
            Function<List<BuildRef>, Long> countRelatedToThisBuildType = list ->
                list.stream()
                    .filter(ref -> Objects.equals(ref.buildTypeId, outCtx.buildTypeId()))
                    .filter(ref -> Objects.equals(normalizeBranch(outCtx.branchName()), normalizeBranch(ref)))
                    .count();

            outCtx.setRunningBuildCount(teamcity.getRunningBuilds("").thenApply(countRelatedToThisBuildType));
            outCtx.setQueuedBuildCount(teamcity.getQueuedBuilds("").thenApply(countRelatedToThisBuildType));
        }
    }

    private static void analyzeTests(MultBuildRunCtx outCtx, ITeamcity teamcity, ProcessLogsMode procLog,
        ITcAnalytics tcAnalytics) {
        for (SingleBuildRunCtx ctx : outCtx.getBuilds()) {
            if (tcAnalytics != null)
                tcAnalytics.calculateBuildStatistic(ctx);

            if ((procLog == ProcessLogsMode.SUITE_NOT_COMPLETE && ctx.hasSuiteIncompleteFailure())
                || procLog == ProcessLogsMode.ALL)
                ctx.setLogCheckResultsFut(teamcity.analyzeBuildLog(ctx.buildId(), ctx));
        }
    }

    @NotNull protected static String normalizeBranch(@NotNull final BuildRef build) {
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
