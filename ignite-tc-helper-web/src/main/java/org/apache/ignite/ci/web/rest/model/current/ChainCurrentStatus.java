package org.apache.ignite.ci.web.rest.model.current;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;

/**
 * Represent Run All chain results/ or RunAll+latest re-runs
 */
public class ChainCurrentStatus extends AbstractTestMetrics {
    public String serverName;

    public List<SuiteCurrentStatus> suites = new ArrayList<>();

    public void initFromContext(ITeamcity teamcity,
        FullChainRunCtx ctx,
        Map<String, IgnitePersistentTeamcity.RunStat> runStatMap) {
        failedTests = 0;
        failedToFinish = 0;
        ctx.failedChildSuites().forEach(
            suite -> {
                final SuiteCurrentStatus suiteCurStatus = new SuiteCurrentStatus();
                suiteCurStatus.initFromContext(teamcity, suite, runStatMap);

                failedTests += suiteCurStatus.failedTests;
                if(suite.hasAnyBuildProblemExceptTestOrSnapshot())
                    failedToFinish++;

                this.suites.add(suiteCurStatus);
            }
        );
    }
}
