package org.apache.ignite.ci.runners;

import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.model.hist.Build;

/**
 * Created by dpavlov on 20.09.2017.
 */
public class PrintChainResults {
    public static void main(String[] args) {

        FullChainRunCtx pubCtx;
        FullChainRunCtx privCtx;
        Ignite ignite = TcHelperDb.start();
        try {

            IgniteTeamcityHelper aPublic = new IgniteTeamcityHelper("public");
            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, aPublic)) {
                String suiteId = "Ignite20Tests_RunAll";
                String branch = "pull/2508/head";

                List<Build> builds = teamcity.getFinishedBuildsIncludeFailed(suiteId, branch);
                if (!builds.isEmpty()) {
                    Build build = builds.get(builds.size() - 1);
                    System.err.println("ID: " + build.getId());

                    pubCtx = CheckBuildChainResults.loadChainContext(teamcity, build);

                }
                else
                    pubCtx = null;

            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
                String suiteId = "id8xIgniteGridGainTests_RunAll";
                String branch = "ignite-2.1.5";

                List<Build> builds = teamcity.getFinishedBuildsIncludeFailed(suiteId, branch);
                if (!builds.isEmpty()) {
                    Build build = builds.get(builds.size() - 1);
                    System.err.println("ID: " + build.getId());

                    privCtx = CheckBuildChainResults.loadChainContext(teamcity, build);

                }
                else {
                    privCtx = null;
                }

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

    private static void printChainResults(FullChainRunCtx ctx) {
        if (ctx == null) {

            System.err.println("No builds were found ");
            return;
        }
        StringBuilder builder = new StringBuilder();
        ctx.failedChildSuites().forEach(runResult -> {
            String str = runResult.getPrintableStatusString();
            builder.append(str).append("\n");
        });
        System.err.println(builder.toString());
    }

}
