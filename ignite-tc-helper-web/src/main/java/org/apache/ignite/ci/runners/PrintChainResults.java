package org.apache.ignite.ci.runners;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import javax.annotation.Nonnull;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;

/**
 * Created by dpavlov on 20.09.2017
 */
public class PrintChainResults {
    public static void main(String[] args) {
        Optional<FullChainRunCtx> pubCtx;
        Optional<FullChainRunCtx> privCtx;
        Ignite ignite = TcHelperDb.start();
        try {
            boolean includeLatestRebuild = true;
            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
                String suiteId = "Ignite20Tests_RunAll";
                String branch = "<default>";

                pubCtx = loadChainContext(teamcity, suiteId, branch, includeLatestRebuild);
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
                String suiteId = "id8xIgniteGridGainTests_RunAll";
                String branch = "<default>";
                privCtx = loadChainContext(teamcity, suiteId, branch, includeLatestRebuild);
            }
        }
        finally {
            TcHelperDb.stop(ignite);
        }

        printTwoChains(pubCtx, privCtx);
    }

    private static void printTwoChains(Optional<FullChainRunCtx> pubCtx,
        Optional<FullChainRunCtx> privCtx) {

        System.err.println(printChainResults(pubCtx, "<Public>"));
        System.err.println(printChainResults(privCtx, "<Private>"));
    }

    @Nonnull public static Optional<FullChainRunCtx> loadChainContext(
        ITeamcity teamcity,
        String suiteId,
        String branch,
        boolean includeLatestRebuild) {
        Optional<BuildRef> buildRef = teamcity.getLastBuildIncludeSnDepFailed(suiteId, branch);
        return buildRef.flatMap(build -> processChainByRef(teamcity, includeLatestRebuild, build, true));
    }

    public static Optional<FullChainRunCtx> processChainByRef(ITeamcity teamcity, boolean includeLatestRebuild,
        BuildRef build, boolean processLogs) {

        Build results = teamcity.getBuildResults(build.href);
        if (results == null)
            return Optional.empty();

        final Properties responsible = getContactPersonProperties(teamcity);

        final FullChainRunCtx val = CheckBuildChainResults.loadChainContext(teamcity, results, includeLatestRebuild,
            processLogs, responsible);
        return Optional.of(val);
    }

    private static Properties getContactPersonProperties(ITeamcity teamcity) {
        Properties properties;
        try {
            properties = HelperConfig.loadContactPersons(teamcity.serverId());
        }
        catch (IOException e) {
            properties = new Properties();
        }

        return properties;
    }

    public static String printChainResults(Optional<FullChainRunCtx> ctx) {
        if (!ctx.isPresent())
            return "No builds were found ";
        StringBuilder builder = new StringBuilder();
        ctx.get().failedChildSuites().forEach(runResult -> {
            String str = runResult.getPrintableStatusString();
            builder.append(str).append("\n");
        });
        return (builder.toString());
    }

    public static String printChainResults(Optional<FullChainRunCtx> chainCtxOpt, String srvName) {
        final StringBuilder builder = new StringBuilder();
        final String srvAdditionalInfo = chainCtxOpt.map(chainCtx -> {
            final int critical = chainCtx.timeoutsOomeCrashBuildProblems();
            final String criticalTxt = critical == 0 ? "" : (", Timeouts/OOMEs/JvmCrashes: " + critical);
            return Integer.toString(chainCtx.failedTests()) + criticalTxt + " "
                + (" ") + chainCtx.getDurationPrintable() + " src update: " + chainCtx.getSourceUpdateDurationPrintable();

        }).orElse("?");
        builder.append(srvName).append("\t").append(srvAdditionalInfo).append("\n");
        builder.append(printChainResults(chainCtxOpt));
        return builder.toString();

    }

}
