package org.apache.ignite.ci.web.rest.build;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.rest.login.ServiceUnauthorizedException;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.model.current.UpdateInfo;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path(GetBuildTestFailures.BUILD)
@Produces(MediaType.APPLICATION_JSON)
public class GetBuildTestFailures {
    public static final String BUILD = "build";
    public static final String TEST_FAILURES_SUMMARY_CACHE_NAME = BUILD + "TestFailuresSummary";
    @Context
    private ServletContext context;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("failures/updates")
    public UpdateInfo getTestFailsUpdates(
        @QueryParam("serverId") String serverId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) throws ServiceUnauthorizedException {
        return new UpdateInfo().copyFrom(getBuildTestFails(serverId, buildId, checkAllLogs));
    }

    @GET
    @Path("failures/txt")
    @Produces(MediaType.TEXT_PLAIN)
    public String getTestFailsText(
        @QueryParam("serverId") String serverId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) throws ServiceUnauthorizedException {
        return getBuildTestFails(serverId, buildId, checkAllLogs).toString();
    }

    @GET
    @Path("failures")
    public TestFailuresSummary getBuildTestFails(
        @QueryParam("serverId") String serverId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs)
            throws ServiceUnauthorizedException {

        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(context);

        FullQueryParams param = new FullQueryParams();
        param.setServerId(serverId);
        param.setBuildId(buildId);
        param.setCheckAllLogs(checkAllLogs);
        return updater.get(TEST_FAILURES_SUMMARY_CACHE_NAME, param,
            (k) -> getBuildTestFailsNoCache(k.getServerId(), k.getBuildId(), k.getCheckAllLogs()), true);
    }

    @GET
    @Path("failuresNoCache")
    @NotNull public TestFailuresSummary getBuildTestFailsNoCache(
        @QueryParam("serverId") String serverId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        final ITcHelper helper = CtxListener.getTcHelper(context);
        final ICredentialsProv prov = ICredentialsProv.get(req);

        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        if(!prov.hasAccess(serverId))
            throw ServiceUnauthorizedException.noCreds(serverId);

        try (IAnalyticsEnabledTeamcity teamcity = helper.server(serverId, prov)) {
            //processChainByRef(teamcity, includeLatestRebuild, build, true, true)
            String hrefById = teamcity.getBuildHrefById(buildId);
            BuildRef build = new BuildRef();
            build.setId(buildId);
            build.href = hrefById;
            String failRateBranch = ITeamcity.DEFAULT;

            Optional<FullChainRunCtx> pubCtx =
                BuildChainProcessor.processBuildChains(teamcity, LatestRebuildMode.NONE,
                    Collections.singletonList(build),
                    (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE,
                    false, false, teamcity, failRateBranch);


            pubCtx.ifPresent(ctx -> {
                final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus(serverId, ctx.branchName());

                int cnt = (int)ctx.getRunningUpdates().count();
                if (cnt > 0)
                    runningUpdates.addAndGet(cnt);

                chainStatus.initFromContext(teamcity, ctx, teamcity, failRateBranch);

                res.addChainOnServer(chainStatus);
            });
        }

        res.postProcess(runningUpdates.get());

        return res;
    }
}
