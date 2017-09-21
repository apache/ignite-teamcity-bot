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
                String branch = "pull/2508/head";
                pubCtx = loadChainContext(teamcity, suiteId, branch, includeLatestRebuild);
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
                String suiteId = "id8xIgniteGridGainTests_RunAll";
                String branch = "ignite-2.1.5";
                privCtx = loadChainContext(teamcity, suiteId, branch, includeLatestRebuild);
            }
        }
        finally {
            TcHelperDb.stop(ignite);
        }

        System.err.println("<Public>");
        printChainResults(pubCtx);
        System.err.println("<Private>");
        printChainResults(privCtx);
    }

    @Nullable private static Optional<FullChainRunCtx> loadChainContext(
        ITeamcity teamcity,
        String suiteId,
        String branch, boolean includeLatestRebuild) {

        Optional<BuildRef> buildRef = teamcity.getLastBuildIncludeSnDepFailed(suiteId, branch);

        return buildRef.map(build -> {
            System.err.println("ID: " + build.getId());

            Build results = teamcity.getBuildResults(build.href);
            return CheckBuildChainResults.loadChainContext(teamcity, results, includeLatestRebuild);
        });
    }

    private static void printChainResults(Optional<FullChainRunCtx> ctx) {
        if (!ctx.isPresent()) {
            System.err.println("No builds were found ");
            return;
        }
        StringBuilder builder = new StringBuilder();
        ctx.get().failedChildSuites().forEach(runResult -> {
            String str = runResult.getPrintableStatusString();
            builder.append(str).append("\n");
        });
        System.err.println(builder.toString());
    }

}
