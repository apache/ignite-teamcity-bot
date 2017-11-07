package org.apache.ignite.ci.web.rest;

import com.google.common.base.Strings;
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
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.TestRunStat;
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
    @Path("failures")
    public List<FailingTest> getTopFailingTests(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("count") Integer count) {
        final String key = Strings.nullToEmpty(branchOrNull);
        final Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);
        final String branch = isNullOrEmpty(key) ? "master" : key;
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branch);

        final List<FailingTest> res = new ArrayList<>();
        for (ChainAtServerTracked chainTracked : tracked.chains) {
            try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, chainTracked.serverId)) {
                int cnt = count == null ? 10 : count;
                teamcity.topFailing(cnt).stream().map(this::converToUi).forEach(res::add);
            }
        }
        return res;
    }

    @GET
    @Path("longRunning")
    public List<FailingTest> getMostLongRunningTests(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("count") Integer count) {
        final String key = Strings.nullToEmpty(branchOrNull);
        final Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);
        final String branch = isNullOrEmpty(key) ? "master" : key;
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branch);

        final List<FailingTest> res = new ArrayList<>();
        for (ChainAtServerTracked chainTracked : tracked.chains) {
            try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, chainTracked.serverId)) {
                int cnt = count == null ? 10 : count;
                teamcity.topLongRunning(cnt).stream().map(this::converToUi).forEach(res::add);
            }
        }
        return res;
    }

    @NotNull private FailingTest converToUi(TestRunStat stat) {
        FailingTest e = new FailingTest();
        e.name = stat.name();
        e.failureRate = stat.getFailPercentPrintable();
        e.averageDuration = TimeUtil.getDurationPrintable(stat.getAverageDurationMs());
        return e;
    }

}
