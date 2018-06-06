package org.apache.ignite.ci.web.rest;

import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.model.current.UpdateInfo;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.BuildChainProcessor.loadChainsContext;

@Path(GetCurrTestFailures.CURRENT)
@Produces(MediaType.APPLICATION_JSON)
public class GetCurrTestFailures {
    public static final String CURRENT = "current";
    public static final String TEST_FAILURES_SUMMARY_CACHE_NAME = CURRENT + "TestFailuresSummary";

    @Context
    private ServletContext context;

    @Context
    private HttpServletRequest request;

    @GET
    @Path("failures/updates")
    public UpdateInfo getTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return new UpdateInfo().copyFrom(getTestFails(branchOrNull, checkAllLogs));
    }

    @GET
    @Path("failures/txt")
    @Produces(MediaType.TEXT_PLAIN)
    public String getTestFailsText(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return getTestFails(branchOrNull, checkAllLogs).toString();
    }

    @GET
    @Path("failures")
    public TestFailuresSummary getTestFails(
        @Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(context);

        FullQueryParams param = new FullQueryParams();
        param.setBranch(branchOrNull);
        param.setCheckAllLogs(checkAllLogs);
        return updater.get(TEST_FAILURES_SUMMARY_CACHE_NAME, param,
            (k) -> getTestFailsNoCache(k.getBranch(), k.getCheckAllLogs()), true);
    }

    @GET
    @Path("failuresNoCache")
    @NotNull public TestFailuresSummary getTestFailsNoCache(
        @Nullable @QueryParam("branch") String branch,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        final ITcHelper helper = CtxListener.getTcHelper(context);
        final ICredentialsProv creds = ICredentialsProv.get(request);

        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        final String branchNn = isNullOrEmpty(branch) ? FullQueryParams.DEFAULT_BRANCH_NAME : branch;
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branchNn);
        res.setTrackedBranch(branchNn);

        tracked.chains.stream().parallel()
                .filter(chainTracked -> creds.hasAccess(chainTracked.serverId))
                .map(chainTracked -> {
                    final String srvId = chainTracked.serverId;

                    final String branchForTc = chainTracked.getBranchForRestMandatory();
                    final String failRateBranch = branchForTc; //branch is tracked, so fail rate should be taken from branch data

                    final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus(srvId, branchForTc);

                    try (IAnalyticsEnabledTeamcity teamcity = helper.server(srvId, creds)) {
                        Optional<FullChainRunCtx> pubCtx = loadChainsContext(teamcity,
                                chainTracked.getSuiteIdMandatory(),
                                branchForTc,
                                LatestRebuildMode.LATEST,
                                (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE,
                                failRateBranch);

                        pubCtx.ifPresent(ctx -> {
                            int cnt = (int) ctx.getRunningUpdates().count();
                            if (cnt > 0)
                                runningUpdates.addAndGet(cnt);

                            chainStatus.initFromContext(teamcity, ctx, teamcity, failRateBranch);
                        });
                    }
                    return chainStatus;
                })
                .sorted(Comparator.comparing(ChainAtServerCurrentStatus::serverName))
                .forEach(res::addChainOnServer);

        res.postProcess(runningUpdates.get());

        helper.issueDetector().registerIssuesLater(res, helper, creds);

        return res;
    }
}
