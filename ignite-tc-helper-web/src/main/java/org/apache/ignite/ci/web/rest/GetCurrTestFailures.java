package org.apache.ignite.ci.web.rest;

import com.google.common.base.Strings;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
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
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.LatestRebuildMode;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.rest.model.current.UpdateInfo;
import org.apache.ignite.internal.util.typedef.T3;
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
    public UpdateInfo getTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull) {
        return new UpdateInfo().copyFrom(getTestFails(branchOrNull));
    }

    @GET
    @Path("failures")
    public TestFailuresSummary getTestFails(@Nullable @QueryParam("branch") String branchOrNull) {
        final String key = Strings.nullToEmpty(branchOrNull);
        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(context);
        return updater.get(TEST_FAILURES_SUMMARY_CACHE_NAME, key, this::getTestFailsNoCache);
    }

    @GET
    @Path("failuresNoCache")
    @NotNull public TestFailuresSummary getTestFailsNoCache(@Nullable @QueryParam("branch") String key) {
        final Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);

        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        final String branch = isNullOrEmpty(key) ? "master" : key;
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branch);

        tracked.chains.stream().parallel()
            .map(chainTracked -> {
                final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
                try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, chainTracked.serverId)) {
                    teamcity.setExecutor(CtxListener.getPool(context));

                    Optional<FullChainRunCtx> pubCtx = loadChainsContext(teamcity,
                        chainTracked.getSuiteIdMandatory(),
                        chainTracked.getBranchForRestMandatory(),
                        LatestRebuildMode.LATEST, teamcity);

                    chainStatus.serverName = teamcity.serverId();
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
    @Path("pr")
    public TestFailuresSummary getPrFailures(
        @Nullable @QueryParam("serverId") String serverId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc) {

        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(context);
        final T3<String, String, String> key = new T3<>(serverId, suiteId, branchForTc);

        return updater.get(CURRENT + "PrFailures", key,
            (key1) -> getPrFailuresNoCache(key1.get1(), key1.get2(), key1.get3()));
    }

    @GET
    @Path("prNoCache")
    @NotNull public TestFailuresSummary getPrFailuresNoCache(
        @Nullable @QueryParam("serverId") String srvId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc) {

        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        //using here non persistent TC allows to skip update statistic
        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(CtxListener.getIgnite(context), srvId)) {
            teamcity.setExecutor(CtxListener.getPool(context));
            teamcity.setStatUpdateEnabled(false);

            Optional<FullChainRunCtx> pubCtx = loadChainsContext(teamcity,
                suiteId, branchForTc, LatestRebuildMode.LATEST, teamcity);

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
