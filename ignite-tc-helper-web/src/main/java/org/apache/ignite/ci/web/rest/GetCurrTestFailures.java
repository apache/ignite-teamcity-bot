package org.apache.ignite.ci.web.rest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
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
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.rest.model.current.UpdateInfo;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.BuildChainProcessor.loadChainsContext;

@Path(GetCurrTestFailures.CURRENT)
@Produces(MediaType.APPLICATION_JSON)
public class GetCurrTestFailures {
    public static final String CURRENT = "current";
    public static final String TEST_FAILURES_SUMMARY_CACHE_NAME = CURRENT + "TestFailuresSummary";
    @Context
    private ServletContext context;

    @GET
    @Path("failures/updates")
    public UpdateInfo getTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return new UpdateInfo().copyFrom(getTestFails(branchOrNull, checkAllLogs));
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

        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        final String branchNn = isNullOrEmpty(branch) ? FullQueryParams.DEFAULT_BRANCH_NAME : branch;
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branchNn);

        tracked.chains.stream().parallel()
            .map(chainTracked -> {
                final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
                String serverId = chainTracked.serverId;
                chainStatus.serverName = serverId;
                try (IAnalyticsEnabledTeamcity teamcity = helper.server(serverId)) {

                    Optional<FullChainRunCtx> pubCtx = loadChainsContext(teamcity,
                        chainTracked.getSuiteIdMandatory(),
                        chainTracked.getBranchForRestMandatory(),
                        LatestRebuildMode.LATEST,
                        (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE);

                    pubCtx.ifPresent(ctx -> {
                        int cnt = (int)ctx.getRunningUpdates().count();
                        if (cnt > 0)
                            runningUpdates.addAndGet(cnt);

                        chainStatus.initFromContext(teamcity, ctx, teamcity);
                    });
                }
                return chainStatus;
            })
            .sorted(Comparator.comparing(ChainAtServerCurrentStatus::serverName))
            .forEach(res::addChainOnServer);

        res.postProcess(runningUpdates.get());

        return res;
    }

    @GET
    @Path("pr/updates")
    public UpdateInfo getPrFailuresUpdates(
        @Nullable @QueryParam("serverId") String serverId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String action,
        @Nullable @QueryParam("count") Integer count) {

        return new UpdateInfo().copyFrom(getPrFailures(serverId, suiteId, branchForTc, action, count));
    }

    @GET
    @Path("pr")
    public TestFailuresSummary getPrFailures(
        @Nullable @QueryParam("serverId") String serverId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String action,
        @Nullable @QueryParam("count") Integer count) {

        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(context);

        final FullQueryParams key = new FullQueryParams(serverId, suiteId, branchForTc, action, count);

        return updater.get(CURRENT + "PrFailures", key,
            (k) -> getPrFailuresNoCache(k.getServerId(), k.getSuiteId(), k.getBranchForTc(), k.getAction(), k.getCount()),
            true);
    }

    @GET
    @Path("prNoCache")
    @NotNull public TestFailuresSummary getPrFailuresNoCache(
        @Nullable @QueryParam("serverId") String srvId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String action,
        @Nullable @QueryParam("count") Integer count) {

        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        //using here non persistent TC allows to skip update statistic
        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(CtxListener.getIgnite(context), srvId)) {
            teamcity.setExecutor(CtxListener.getPool(context));
            teamcity.setStatUpdateEnabled(false);

            LatestRebuildMode rebuild;
            if (FullQueryParams.HISTORY.equals(action))
                rebuild = LatestRebuildMode.ALL;
            else if (FullQueryParams.LATEST.equals(action))
                rebuild = LatestRebuildMode.LATEST;
            else if (FullQueryParams.CHAIN.equals(action))
                rebuild = LatestRebuildMode.NONE;
            else
                rebuild = LatestRebuildMode.LATEST;

            final List<BuildRef> chains;
            if (rebuild == LatestRebuildMode.ALL) {
                List<BuildRef> finishedBuilds = teamcity.getFinishedBuildsIncludeSnDepFailed(
                    suiteId,
                    branchForTc);

                long limit = count == null ? 10 : count;
                chains = finishedBuilds.stream()
                    .filter(ref -> !ref.isFakeStub())
                    .sorted(Comparator.comparing(BuildRef::getId).reversed())
                    .limit(limit).parallel()
                    .filter(b -> b.getId() != null).collect(Collectors.toList());
            } else {
                List<BuildRef> finishedBuilds = new ArrayList<>();
                Optional<BuildRef> buildRef = teamcity.getLastBuildIncludeSnDepFailed(suiteId, branchForTc);

                buildRef.ifPresent(finishedBuilds::add);

                chains= finishedBuilds;
            }

            boolean singleBuild = rebuild != LatestRebuildMode.ALL;
            ProcessLogsMode logs = singleBuild
                ? ProcessLogsMode.SUITE_NOT_COMPLETE
                : ProcessLogsMode.DISABLED;

            Optional<FullChainRunCtx> pubCtx = BuildChainProcessor.processBuildChains(teamcity, rebuild, chains,
                logs,
                singleBuild,
                true, teamcity);

            final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
            chainStatus.serverName = teamcity.serverId();

            pubCtx.ifPresent(ctx -> {
                int cnt = (int)ctx.getRunningUpdates().count();
                if (cnt > 0)
                    runningUpdates.addAndGet(cnt);

                chainStatus.initFromContext(teamcity, ctx, teamcity);
            });

            res.addChainOnServer(chainStatus);
        }

        res.postProcess(runningUpdates.get());

        return res;
    }

}
