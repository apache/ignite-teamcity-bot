package org.apache.ignite.ci.web.rest.model.current;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.RunStat;

import static org.apache.ignite.ci.util.UrlUtil.escape;
import static org.apache.ignite.ci.web.rest.model.current.SuiteCurrentStatus.branchForLink;

/**
 * Represent Run All chain results/ or RunAll+latest re-runs
 */
@SuppressWarnings("WeakerAccess") public class ChainAtServerCurrentStatus extends AbstractTestMetrics {
    public String chainName;

    public String serverName;

    /** Web Href. to suite runs history*/
    public String webToHist = "";

    /** Web Href. to suite particular run */
    public String webToBuild = "";

    public List<SuiteCurrentStatus> suites = new ArrayList<>();

    public void initFromContext(ITeamcity teamcity,
        FullChainRunCtx ctx,
        @Nullable final Function<String, RunStat> testsRunStatMap,
        @Nullable final Function<String, RunStat> suiteRunStatMap) {
        failedTests = 0;
        failedToFinish = 0;
        ctx.failedChildSuites().forEach(
            suite -> {
                final SuiteCurrentStatus suiteCurStatus = new SuiteCurrentStatus();
                suiteCurStatus.initFromContext(teamcity, suite, testsRunStatMap, suiteRunStatMap);

                failedTests += suiteCurStatus.failedTests;
                if (suite.hasAnyBuildProblemExceptTestOrSnapshot())
                    failedToFinish++;

                this.suites.add(suiteCurStatus);
            }
        );
        durationPrintable = ctx.getDurationPrintable();
        webToHist = buildWebLink(teamcity, ctx);
        webToBuild = buildWebLinkToBuild(teamcity, ctx);
    }

    private static String buildWebLinkToBuild(ITeamcity teamcity, FullChainRunCtx chain) {
        return teamcity.host() + "viewLog.html?buildId=" + chain.getSuiteBuildId() ;
    }

    private static String buildWebLink(ITeamcity teamcity, FullChainRunCtx suite) {
        final String branch = branchForLink(suite.branchName());
        return teamcity.host() + "viewType.html?buildTypeId=" + suite.suiteId()
            + "&branch=" + escape(branch)
            + "&tab=buildTypeStatusDiv";
    }

    /**
     * @return Server name.
     */
    public String serverName() {
        return serverName;
    }
}
