package org.apache.ignite.ci;

import com.google.common.base.Stopwatch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.internal.util.typedef.T2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Collections.singletonList;

public class BuildChainProcessor {
    @Nonnull public static Optional<FullChainRunCtx> loadChainsContext(
        ITeamcity teamcity,
        String suiteId,
        String branch,
        boolean includeLatestRebuild) {
        Optional<BuildRef> buildRef = teamcity.getLastBuildIncludeSnDepFailed(suiteId, branch);

        return buildRef.flatMap(
            build -> processBuildChains(teamcity, includeLatestRebuild, singletonList(build),
                true, true, true));
    }

    public static Optional<FullChainRunCtx> processBuildChains(
        ITeamcity teamcity,
        boolean includeLatestRebuild,
        Collection<BuildRef> builds,
        boolean procLogs,
        boolean includeScheduled,
        boolean showContacts) {

        final Properties responsible = showContacts ? getContactPersonProperties(teamcity) : null;

        final FullChainRunCtx val = loadChainsContext(teamcity, builds, includeLatestRebuild,
            procLogs, responsible, includeScheduled);

        return Optional.of(val);
    }

    private static Properties getContactPersonProperties(ITeamcity teamcity) {
        return HelperConfig.loadContactPersons(teamcity.serverId());
    }

    public static <R> FullChainRunCtx loadChainsContext(
        ITeamcity teamcity,
        Collection<BuildRef> entryPoints,
        boolean includeLatestRebuild,
        boolean procLog,
        @Nullable Properties contactPersonProps,
        boolean includeScheduledInfo) {

        assert !entryPoints.isEmpty();
        //todo empty
        BuildRef next = entryPoints.iterator().next();
        Build results = teamcity.getBuildResults(next.href);
        FullChainRunCtx fullChainRunCtx = new FullChainRunCtx(results);

        Map<Integer, BuildRef> unique = new ConcurrentHashMap<>();
        Map<String, MultBuildRunCtx> buildsCtxMap = new ConcurrentHashMap<>();

        entryPoints.stream()
            .parallel()
            .unordered()
            .flatMap(ref -> dependencies(teamcity, ref)).filter(Objects::nonNull)
            .flatMap(ref -> dependencies(teamcity, ref)).filter(Objects::nonNull)
            .filter(ref -> {
                if (ref.isFakeStub())
                    return false;

                String id = ref.buildTypeId;

                Integer buildId = ref.getId();
                T2<String, Integer> key = new T2<>(id, buildId);

                BuildRef prevVal = unique.putIfAbsent(buildId, ref);

                return prevVal == null;
            })
            .map((BuildRef buildRef) -> {
                    BuildRef recentRef = includeLatestRebuild ? teamcity.tryReplaceBuildRefByRecent(buildRef) : buildRef;
                    if (recentRef.isFakeStub())
                        recentRef = buildRef;

                    return recentRef;
                }
            )
            .forEach((BuildRef buildRef) -> {
                Build build = teamcity.getBuildResults(buildRef.href);
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

    @NotNull private static MultBuildRunCtx collectBuildContext(
        MultBuildRunCtx outCtx, ITeamcity teamcity, boolean procLog,
        @Nullable Properties contactPersonProps, boolean includeScheduledInfo, Build build) {

        teamcity.loadTestsAndProblems(build, outCtx);

        if (procLog && (outCtx.hasJvmCrashProblem() || outCtx.hasTimeoutProblem() || outCtx.hasOomeProblem())) {

            final Stopwatch started = Stopwatch.createStarted();

            try {
                teamcity.processBuildLog(outCtx).get();
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println(Thread.currentThread().getName()
                + ": processBuildLog required: " + started.elapsed(TimeUnit.MILLISECONDS)
                + "ms for " + build.suiteId());
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

        return outCtx;
    }

    @Nullable private static Stream<? extends BuildRef> dependencies(ITeamcity teamcity, BuildRef ref) {
        Build results = teamcity.getBuildResults(ref.href);
        if (results == null)
            return null;
        
        List<BuildRef> aNull = results.getSnapshotDependenciesNonNull();
        if(aNull.isEmpty())
            return Stream.of(ref);

        ArrayList<BuildRef> cp = new ArrayList<>(aNull);

        cp.add(ref);

        return cp.stream();
    }
}
