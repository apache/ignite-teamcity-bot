package org.apache.ignite.ci.web.rest.model.current;

import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.ignite.ci.ITcAnalytics;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;

import static org.apache.ignite.ci.util.UrlUtil.escape;
import static org.apache.ignite.ci.web.rest.model.current.SuiteCurrentStatus.branchForLink;

/**
 * Represent Run All chain results/ or RunAll+latest re-runs
 */
@SuppressWarnings({"WeakerAccess", "PublicField"})
public class ChainAtServerCurrentStatus {
    public String chainName;

    public String serverName;

    /** Web Href. to suite runs history*/
    public String webToHist = "";

    /** Web Href. to suite particular run */
    public String webToBuild = "";

    /** Suites involved in chain. */
    public List<SuiteCurrentStatus> suites = new ArrayList<>();

    public Integer failedTests;
    /** Count of suites with critical build problems found */
    public Integer failedToFinish;
    public String durationPrintable;

    public void initFromContext(ITeamcity teamcity,
        FullChainRunCtx ctx,
        @Nullable ITcAnalytics tcAnalytics) {
        failedTests = 0;
        failedToFinish = 0;
        Stream<MultBuildRunCtx> stream = ctx.failedChildSuites();
        stream.forEach(
            suite -> {
                final SuiteCurrentStatus suiteCurStatus = new SuiteCurrentStatus();
                suiteCurStatus.initFromContext(teamcity, suite, tcAnalytics);

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

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ChainAtServerCurrentStatus status = (ChainAtServerCurrentStatus)o;
        return Objects.equal(chainName, status.chainName) &&
            Objects.equal(serverName, status.serverName) &&
            Objects.equal(webToHist, status.webToHist) &&
            Objects.equal(webToBuild, status.webToBuild) &&
            Objects.equal(suites, status.suites) &&
            Objects.equal(failedTests, status.failedTests) &&
            Objects.equal(failedToFinish, status.failedToFinish) &&
            Objects.equal(durationPrintable, status.durationPrintable);
    }

    @Override public int hashCode() {
        return Objects.hashCode(chainName, serverName, webToHist, webToBuild, suites,
            failedTests, failedToFinish, durationPrintable);
    }
}
