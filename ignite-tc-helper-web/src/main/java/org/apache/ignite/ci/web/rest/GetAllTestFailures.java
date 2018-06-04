package org.apache.ignite.ci.web.rest;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.model.current.UpdateInfo;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;

@Path(GetAllTestFailures.ALL)
@Produces(MediaType.APPLICATION_JSON)
public class GetAllTestFailures {
    public static final String ALL = "all";
    @Context
    private ServletContext context;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("failures/updates")
    public UpdateInfo getAllTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("count") Integer count,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        return new UpdateInfo().copyFrom(getAllTestFails(branchOrNull, count, checkAllLogs));
    }

    @GET
    @Path("failures")
    public TestFailuresSummary getAllTestFails(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("count") Integer count,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(context);
        FullQueryParams fullKey = new FullQueryParams();
        fullKey.setBranch(branchOrNull);
        fullKey.setCount(count == null ? FullQueryParams.DEFAULT_COUNT : count);
        fullKey.setCheckAllLogs(checkAllLogs != null && checkAllLogs);

        return updater.get("AllTestFailuresSummary",
            fullKey,
            k -> getAllTestFailsNoCache(
                k.getBranch(),
                k.getCount(),
                k.getCheckAllLogs()));
    }

    @GET
    @Path("failuresNoCache")
    @NotNull public TestFailuresSummary getAllTestFailsNoCache(@Nullable @QueryParam("branch") String branchOpt,
        @QueryParam("count") Integer count,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        final ITcHelper helper = CtxListener.getTcHelper(context);
        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();
        final ICredentialsProv creds = ICredentialsProv.get(req);

        final String branch = isNullOrEmpty(branchOpt) ? "master" : branchOpt;
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branch);
        for (ChainAtServerTracked chainAtServerTracked : tracked.chains) {

            final String serverId = chainAtServerTracked.serverId;
            if (!creds.hasAccess(serverId))
                continue;

            try (IAnalyticsEnabledTeamcity teamcity = helper.server(serverId, creds)) {
                final String projectId = chainAtServerTracked.getSuiteIdMandatory();
                final String branchTc = chainAtServerTracked.getBranchForRestMandatory();
                final List<BuildRef> builds = teamcity.getFinishedBuildsIncludeSnDepFailed(
                        projectId,
                        branchTc);

                List<BuildRef> chains = builds.stream()
                        .filter(ref -> !ref.isFakeStub())
                        .sorted(Comparator.comparing(BuildRef::getId).reversed())
                        .limit(count).parallel()
                        .filter(b -> b.getId() != null).collect(Collectors.toList());

                String failRateBranch = branchTc; //for tracked branch reference is also current branch

                Optional<FullChainRunCtx> chainCtxOpt
                        = BuildChainProcessor.processBuildChains(teamcity,
                        LatestRebuildMode.ALL, chains,
                        checkAllLogs != null && checkAllLogs ? ProcessLogsMode.ALL : ProcessLogsMode.DISABLED,
                        false, true, teamcity, failRateBranch);

                final ChainAtServerCurrentStatus chainStatus
                        = new ChainAtServerCurrentStatus(teamcity.serverId(), branchTc);


                chainCtxOpt.ifPresent(chainCtx -> {
                    chainStatus.initFromContext(teamcity, chainCtx, teamcity, failRateBranch);

                    int cnt = (int) chainCtx.getRunningUpdates().count();
                    if (cnt > 0)
                        runningUpdates.addAndGet(cnt);
                });
                res.addChainOnServer(chainStatus);

            }
        }

        res.postProcess(runningUpdates.get());

        return res;
    }

}
