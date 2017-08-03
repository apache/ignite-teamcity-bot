package org.apache.ignite.ci.runners;

import java.util.Iterator;
import java.util.List;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.model.result.FullBuildInfo;

/**
 * Created by dpavlov on 03.08.2017
 */
public class CheckBuildChainResults {

    public static void main(String[] args) throws Exception {
        try (IgniteTeamcityHelper teamcity = new IgniteTeamcityHelper("public")) {
            //todo  status!="UNKNOWN";
            List<Build> all = teamcity.getBuildHistory("Ignite20Tests_RunAll",
                "refs/heads/master",
                false,
                "finished");
            if(!all.isEmpty()) {
                Build latest = all.get(0);
                String href = latest.href;
                FullBuildInfo results = teamcity.getBuildResults(href);
                List<Build> builds = results.getSnapshotDependenciesNonNull();
                System.err.println(results);
                for (Build next : builds) {
                    FullBuildInfo dep = teamcity.getBuildResults(next.href);
                    System.err.println(dep);
                }
            }


        }
    }
}
