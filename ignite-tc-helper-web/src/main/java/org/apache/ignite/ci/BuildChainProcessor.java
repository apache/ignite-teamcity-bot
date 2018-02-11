package org.apache.ignite.ci;

import com.google.common.base.Stopwatch;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
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
        return buildRef.flatMap(build -> processChainByRef(teamcity, includeLatestRebuild, build,
            true, true, true));
    }

    public static Optional<FullChainRunCtx> processChainByRef(
        ITeamcity teamcity,
        boolean includeLatestRebuild,
        BuildRef build,
        boolean procLogs,
        boolean includeScheduled,
        boolean showContacts) {

        Build results = teamcity.getBuildResults(build.href);
        if (results == null)
            return Optional.empty();

        final Properties responsible = showContacts ? getContactPersonProperties(teamcity) : null;

        final FullChainRunCtx val = loadChainContext(teamcity, results, includeLatestRebuild,
            procLogs, responsible, includeScheduled);

        return Optional.of(val);
    }

    private static Properties getContactPersonProperties(ITeamcity teamcity) {
        return HelperConfig.loadContactPersons(teamcity.serverId());
    }

    public static FullChainRunCtx loadChainContext(
        ITeamcity teamcity,
        Build chainRoot,
        boolean includeLatestRebuild,
        boolean procLog,
        @Nullable Properties contactPersonProps,
        boolean includeScheduledInfo) {

        List<FullBuildRunContext> suiteCtx = chainRoot.getSnapshotDependenciesNonNull().stream()
            .parallel()
            .unordered()
            .map((BuildRef buildRef) -> {
                final BuildRef recentRef = includeLatestRebuild ? teamcity.tryReplaceBuildRefByRecent(buildRef) : buildRef;
                if (recentRef.getId() == null)
                    return null;

                final FullBuildRunContext ctx = teamcity.loadTestsAndProblems(recentRef);
                if (ctx == null)
                    return null;

                if (procLog && (ctx.hasJvmCrashProblem() || ctx.hasTimeoutProblem() || ctx.hasOomeProblem())) {

                    final Stopwatch started = Stopwatch.createStarted();

                    try {
                        teamcity.processBuildLog(ctx).get();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }

                    System.out.println(Thread.currentThread().getName()
                        + ": processBuildLog required: " + started.elapsed(TimeUnit.MILLISECONDS)
                        + "ms for " + buildRef.suiteId());
                }

                if(includeScheduledInfo) {
                    final String tcBranch = buildRef.branchName == null ? ITeamcity.DEFAULT : buildRef.branchName;
                    ctx.setRunningBuildCount(teamcity.getRunningBuilds(buildRef.buildTypeId, tcBranch).size());

                    int buildCnt = teamcity.getQueuedBuilds(buildRef.buildTypeId, tcBranch).size();

                    if ("refs/heads/master".equals(tcBranch) && buildCnt == 0)
                        buildCnt = teamcity.getQueuedBuilds(buildRef.buildTypeId, ITeamcity.DEFAULT).size();

                    ctx.setQueuedBuildCount(buildCnt);
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
