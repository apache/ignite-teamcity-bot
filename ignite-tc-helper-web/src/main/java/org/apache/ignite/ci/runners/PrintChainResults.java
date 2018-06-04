package org.apache.ignite.ci.runners;

import java.util.Optional;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.db.TcHelperDb;

/**
 * Created by dpavlov on 20.09.2017
 */
@Deprecated
public class PrintChainResults {
    public static void main(String[] args) {
        Optional<FullChainRunCtx> pubCtx;
        Optional<FullChainRunCtx> privCtx;
        Ignite ignite = TcHelperDb.start();
        try {
            LatestRebuildMode includeLatestRebuild = LatestRebuildMode.LATEST;
            try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
                String suiteId = "IgniteTests24Java8_RunAll";
                String branch = "<default>";

                pubCtx = BuildChainProcessor.loadChainsContext(teamcity, suiteId, branch, includeLatestRebuild, ProcessLogsMode.SUITE_NOT_COMPLETE, ITeamcity.DEFAULT);
            }
            try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
                String suiteId = "id8xIgniteGridGainTestsJava8_RunAll";
                String branch = "<default>";
                privCtx = BuildChainProcessor.loadChainsContext(teamcity, suiteId, branch, includeLatestRebuild, ProcessLogsMode.SUITE_NOT_COMPLETE, ITeamcity.DEFAULT);
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
