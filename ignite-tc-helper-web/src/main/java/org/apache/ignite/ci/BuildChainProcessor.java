package org.apache.ignite.ci;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.analysis.FullBuildRunContext;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.jetbrains.annotations.Nullable;

public class BuildChainProcessor {
    @Nonnull public static Optional<FullChainRunCtx> loadChainContext(
        ITeamcity teamcity,
        String suiteId,
        String branch,
        boolean includeLatestRebuild) {

        Optional<BuildRef> buildRef = teamcity.getLastBuildIncludeSnDepFailed(suiteId, branch);
        return buildRef.flatMap(build -> processChainByRef(teamcity, includeLatestRebuild, build, true, true));
    }

    public static Optional<FullChainRunCtx> processChainByRef(
        ITeamcity teamcity,
        boolean includeLatestRebuild,
        BuildRef build,
        boolean procLogs,
        boolean includeScheduled) {

        Build results = teamcity.getBuildResults(build.href);
        if (results == null)
            return Optional.empty();

        final Properties responsible = getContactPersonProperties(teamcity);

        final FullChainRunCtx val = loadChainContext(teamcity, results, includeLatestRebuild,
            procLogs, responsible, includeScheduled);
        return Optional.ofNullable(val);
    }

    private static Properties getContactPersonProperties(ITeamcity teamcity) {
        return HelperConfig.loadContactPersons(teamcity.serverId());
    }

    @Nullable public static FullChainRunCtx loadChainContext(
        ITeamcity teamcity,
        Build chainRoot,
        boolean includeLatestRebuild,
        boolean procLog,
        @Nullable Properties contactPersonProps,
        boolean includeScheduledInfo) {

        List<FullBuildRunContext> suiteCtx = chainRoot.getSnapshotDependenciesNonNull().stream()
            .parallel()
            .map((BuildRef buildRef) -> {
                final BuildRef recentRef = includeLatestRebuild ? teamcity.tryReplaceBuildRefByRecent(buildRef) : buildRef;
                if (recentRef.getId() == null)
                    return null;

                final FullBuildRunContext ctx = teamcity.loadTestsAndProblems(recentRef);
                if (ctx == null)
                    return null;

                if (procLog && (ctx.hasJvmCrashProblem() || ctx.hasTimeoutProblem() || ctx.hasOomeProblem())) {
                    try {
                        teamcity.processBuildLog(ctx).get();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if(includeScheduledInfo) {
                    final String tcBranch = buildRef.branchName == null ? ITeamcity.DEFAULT : buildRef.branchName;
                    ctx.setRunningBuildCount(teamcity.getRunningBuilds(buildRef.buildTypeId, tcBranch).size());
                    ctx.setQueuedBuildCount(teamcity.getQueuedBuilds(buildRef.buildTypeId, tcBranch).size());
                }

                if (contactPersonProps != null && contactPersonProps.containsKey(ctx.suiteId()))
                    ctx.setContactPerson(contactPersonProps.getProperty(ctx.suiteId()));

                return ctx;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (contactPersonProps != null)
            suiteCtx.sort(Comparator.comparing(FullBuildRunContext::getContactPersonOrEmpty));
        else
            suiteCtx.sort(Comparator.comparing(FullBuildRunContext::suiteName));

        return new FullChainRunCtx(chainRoot, suiteCtx);
    }
}
