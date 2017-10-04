package org.apache.ignite.ci.runners;

import java.util.Optional;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.jetbrains.annotations.Nullable;

/**
 * Created by dpavlov on 20.09.2017
 */
public class PrintChainResults {
    public static void main(String[] args) {
        Optional<FullChainRunCtx> pubCtx;
        Optional<FullChainRunCtx> privCtx;
        Ignite ignite = TcHelperDb.start();
        try {
            boolean includeLatestRebuild =true;
            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite,"public")) {
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

    @Nullable public static Optional<FullChainRunCtx> loadChainContext(
        ITeamcity teamcity,
        String suiteId,
        String branch,
        boolean includeLatestRebuild) {

        Optional<BuildRef> buildRef = teamcity.getLastBuildIncludeSnDepFailed(suiteId, branch);

        return buildRef.map(build -> {
            System.err.println("ID: " + build.getId());
            Build results = teamcity.getBuildResults(build.href);
            return CheckBuildChainResults.loadChainContext(teamcity, results, includeLatestRebuild,
                true);
        });
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

    public static String printChainResults(Optional<FullChainRunCtx> chainCtx,String srvName) {
        StringBuilder builder= new StringBuilder();
        final String srvAdditionalInfo = chainCtx.map(res -> {
            final int critical = res.timeoutsAndCrashBuildProblems();
            final String criticalTxt = critical == 0 ? "" : (", Timeouts/JvmCrashes: " + critical);
            return Integer.toString(res.failedTests()) + criticalTxt;

        }).orElse("?");
        builder.append(srvName).append("\t").append(srvAdditionalInfo).append("\n");
        builder.append(printChainResults(chainCtx));
        return builder.toString();

    }
}
