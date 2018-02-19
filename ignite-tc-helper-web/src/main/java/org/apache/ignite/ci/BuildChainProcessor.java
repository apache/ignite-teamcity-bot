package org.apache.ignite.ci;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.LatestRebuildMode;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.SingleBuildRunCtx;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Collections.singletonList;

public class BuildChainProcessor {
    @Nonnull public static Optional<FullChainRunCtx> loadChainsContext(
        ITeamcity teamcity,
        String suiteId,
        String branch,
        LatestRebuildMode includeLatestRebuild) {
        Optional<BuildRef> buildRef = teamcity.getLastBuildIncludeSnDepFailed(suiteId, branch);

        return buildRef.flatMap(
            build -> processBuildChains(teamcity, includeLatestRebuild, singletonList(build),
                true, true, true));
    }

    public static Optional<FullChainRunCtx> processBuildChains(
        ITeamcity teamcity,
        LatestRebuildMode includeLatestRebuild,
        Collection<BuildRef> builds,
        boolean procLogs,
        boolean includeScheduled,
        boolean showContacts) {

        final Properties responsible = showContacts ? getContactPersonProperties(teamcity) : null;

        final FullChainRunCtx val = loadChainsContext(teamcity, builds,
            includeLatestRebuild,
            procLogs, responsible, includeScheduled);

        return Optional.of(val);
    }

    private static Properties getContactPersonProperties(ITeamcity teamcity) {
        return HelperConfig.loadContactPersons(teamcity.serverId());
    }

    public static <R> FullChainRunCtx loadChainsContext(
        ITeamcity teamcity,
        Collection<BuildRef> entryPoints,
        LatestRebuildMode includeLatestRebuild,
        boolean procLog,
        @Nullable Properties contactPersonProps,
        boolean includeScheduledInfo) {

        assert !entryPoints.isEmpty();
        //todo empty
        BuildRef next = entryPoints.iterator().next();
        Build results = teamcity.getBuild(next.href);
        FullChainRunCtx fullChainRunCtx = new FullChainRunCtx(results);

        Map<Integer, BuildRef> unique = new ConcurrentHashMap<>();
        Map<String, MultBuildRunCtx> buildsCtxMap = new ConcurrentHashMap<>();

        entryPoints.stream()
            .parallel()
            .unordered()
            .flatMap(ref -> dependencies(teamcity, ref)).filter(Objects::nonNull)
            .flatMap(ref -> dependencies(teamcity, ref)).filter(Objects::nonNull)
            .filter(ref -> enshureUnique(unique, ref))
            .flatMap((BuildRef buildRef) -> {
                    if (includeLatestRebuild == LatestRebuildMode.NONE)
                        return Stream.of(buildRef);

                    String branch = buildRef.branchName == null ? ITeamcity.DEFAULT : buildRef.branchName;
                    final List<BuildRef> builds = teamcity.getFinishedBuilds(buildRef.buildTypeId, branch);

                    if (includeLatestRebuild == LatestRebuildMode.LATEST) {
                        BuildRef recentRef = builds.stream().max(Comparator.comparing(BuildRef::getId)).orElse(buildRef);

                        return Stream.of(recentRef.isFakeStub() ? buildRef : recentRef);
                    }

                    if (includeLatestRebuild == LatestRebuildMode.ALL) {
                        return builds.stream()
                            .filter(ref -> !ref.isFakeStub())
                            .filter(ref -> enshureUnique(unique, ref))
                            .sorted(Comparator.comparing(BuildRef::getId).reversed())
                            .limit(entryPoints.size()); // applying same limit
                    }

                    throw new UnsupportedOperationException("invalid mode " + includeLatestRebuild);
                }
            )
            .forEach((BuildRef buildRef) -> {
                Build build = teamcity.getBuild(buildRef.href);
                if (build == null || build.isFakeStub())
                    return;

                String buildTypeId = build.buildTypeId;
                MultBuildRunCtx ctx = buildsCtxMap.computeIfAbsent(buildTypeId, k -> new MultBuildRunCtx(build));

                collectBuildContext(ctx, teamcity, procLog, contactPersonProps, includeScheduledInfo, build);
            });


        Collection<MultBuildRunCtx> values = buildsCtxMap.values();
        ArrayList<MultBuildRunCtx> contexts = new ArrayList<>(values);
        if (contactPersonProps != null)
            contexts.sort(Comparator.comparing(MultBuildRunCtx::getContactPersonOrEmpty));
        else
            contexts.sort(Comparator.comparing(MultBuildRunCtx::suiteName));

        fullChainRunCtx.addAllSuites(contexts);

        return fullChainRunCtx;
    }

    public static boolean enshureUnique(Map<Integer, BuildRef> unique, BuildRef ref) {
        if (ref.isFakeStub())
            return false;

        BuildRef prevVal = unique.putIfAbsent(ref.getId(), ref);

        return prevVal == null;
    }

    @NotNull private static void collectBuildContext(
        MultBuildRunCtx outCtx, ITeamcity teamcity, boolean procLog,
        @Nullable Properties contactPersonProps, boolean includeScheduledInfo, Build build) {

        SingleBuildRunCtx ctx = teamcity.loadTestsAndProblems(build, outCtx);
        outCtx.addBuild(ctx);

        if (procLog && (outCtx.hasJvmCrashProblem() || outCtx.hasTimeoutProblem() || outCtx.hasOomeProblem())) {
            ctx.setLogCheckResultsFut(teamcity.getLogCheckResults(ctx.buildId(), ctx));
        }

        if (includeScheduledInfo && !outCtx.hasScheduledBuildsInfo()) {
            final String tcBranch = build.branchName == null ? ITeamcity.DEFAULT : build.branchName;
            outCtx.setRunningBuildCount(teamcity.getRunningBuilds(build.buildTypeId, tcBranch).size());

            int buildCnt = teamcity.getQueuedBuilds(build.buildTypeId, tcBranch).size();

            if ("refs/heads/master".equals(tcBranch) && buildCnt == 0)
                buildCnt = teamcity.getQueuedBuilds(build.buildTypeId, ITeamcity.DEFAULT).size();

            outCtx.setQueuedBuildCount(buildCnt);
        }

        if (contactPersonProps != null && outCtx.getContactPerson() == null)
            outCtx.setContactPerson(contactPersonProps.getProperty(outCtx.suiteId()));


    }

    @Nullable private static Stream<? extends BuildRef> dependencies(ITeamcity teamcity, BuildRef ref) {
        Build results = teamcity.getBuild(ref.href);
        if (results == null)
            return Stream.of(ref);
        
        List<BuildRef> aNull = results.getSnapshotDependenciesNonNull();
        if(aNull.isEmpty())
            return Stream.of(ref);

        ArrayList<BuildRef> cp = new ArrayList<>(aNull);

        cp.add(ref);

        return cp.stream();
    }
}
