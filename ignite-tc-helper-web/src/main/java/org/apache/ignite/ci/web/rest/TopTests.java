package org.apache.ignite.ci.web.rest;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.util.TimeUtil;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.top.FailingTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;

@Path(TopTests.TOP_TESTS)
@Produces(MediaType.APPLICATION_JSON)
public class TopTests {
    public static final String TOP_TESTS = "top";
    @Context
    private ServletContext context;

    @GET
    @Path("failing")
    public List<FailingTest> getTopFailingTests(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("count") Integer count) {
        final Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);

        final List<FailingTest> res = new ArrayList<>();
        for (ChainAtServerTracked chainTracked : branchMandatory(branchOrNull).chains) {
            try (IAnalyticsEnabledTeamcity teamcity = CtxListener.getTcHelper(context).server( chainTracked.serverId)) {

                int cnt = count == null ? 10 : count;
                teamcity.topTestFailing(cnt).stream().map(this::converToUiModel).forEach(res::add);
            }
        }
        return res;
    }

    @GET
    @Path("failingSuite")
    public List<FailingTest> getTopFailingSuite(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("count") Integer count) {
        final List<FailingTest> res = new ArrayList<>();
        for (ChainAtServerTracked chainTracked : branchMandatory(branchOrNull).chains) {
            try (IAnalyticsEnabledTeamcity teamcity = CtxListener.getTcHelper(context).server(chainTracked.serverId)) {
                int cnt = count == null ? 10 : count;
                teamcity.topFailingSuite(cnt).stream().map(this::converToUiModel).forEach(res::add);
            }
        }
        return res;
    }

    @GET
    @Path("longRunning")
    public List<FailingTest> getMostLongRunningTests(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("count") Integer count) {
        final BranchTracked tracked = branchMandatory(branchOrNull);

        final List<FailingTest> res = new ArrayList<>();
        for (ChainAtServerTracked chainTracked : tracked.chains) {
            try (IAnalyticsEnabledTeamcity teamcity = CtxListener.getTcHelper(context).server(chainTracked.serverId)) {
                int cnt = count == null ? 10 : count;
                teamcity.topTestsLongRunning(cnt).stream().map(this::converToUiModel).forEach(res::add);
            }
        }
        return res;
    }

    private BranchTracked branchMandatory(@Nullable @QueryParam("branch") String branchOrNull) {
        final String branch = isNullOrEmpty(branchOrNull) ? "master" : branchOrNull;
        return HelperConfig.getTrackedBranches().getBranchMandatory(branch);
    }

    @NotNull private FailingTest converToUiModel(RunStat stat) {
        FailingTest e = new FailingTest();
        e.name = stat.name();
        e.failureRate = stat.getFailPercentPrintable();
        e.averageDuration = TimeUtil.getDurationPrintable(stat.getAverageDurationMs());
        return e;
    }

}
