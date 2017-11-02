package org.apache.ignite.ci.web.rest;

import com.google.common.base.Strings;
import java.util.Map;
import java.util.Optional;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.FailureDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.runners.PrintChainResults.loadChainContext;

@Path(GetCurrTestFailures.CURRENT)
@Produces(MediaType.APPLICATION_JSON)
public class GetCurrTestFailures {
    public static final String CURRENT = "current";
    @Context
    private ServletContext context;

    @GET
    @Path("failures")
    public FailureDetails getTestFails(@Nullable @QueryParam("branch") String branchOrNull) {
        Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);
        final String key = Strings.nullToEmpty(branchOrNull);
        final BackgroundUpdater updater = (BackgroundUpdater)context.getAttribute(CtxListener.UPDATER);
        return updater.get(CURRENT, key, k -> getTestFailsNoCache(k, ignite));
    }


    @NotNull private FailureDetails getTestFailsNoCache(@Nullable @QueryParam("branch") String branch, Ignite ignite) {
        final FailureDetails res = new FailureDetails();

        boolean includeLatestRebuild = true;
        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
            String suiteId = "Ignite20Tests_RunAll";
            //todo config branches and its names
            String branchPub =
                (isNullOrEmpty(branch) || "master".equals(branch)) ? "<default>" : "pull/2508/head";
            Optional<FullChainRunCtx> pubCtx = loadChainContext(teamcity, suiteId, branchPub, includeLatestRebuild);

            final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
            chainStatus.serverName = teamcity.serverId();

            final Map<String, IgnitePersistentTeamcity.RunStat> map = teamcity.runTestAnalysis();

            pubCtx.ifPresent(ctx -> chainStatus.initFromContext(teamcity, ctx, map));

            res.servers.add(chainStatus);
        }

        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
            String suiteId = "id8xIgniteGridGainTests_RunAll";
            //todo config
            String branchPriv =
                (isNullOrEmpty(branch) || "master".equals(branch)) ? "<default>" : "ignite-2.1.5";
            Optional<FullChainRunCtx> privCtx = loadChainContext(teamcity, suiteId, branchPriv, includeLatestRebuild);

            final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
            chainStatus.serverName = teamcity.serverId();
            final Map<String, IgnitePersistentTeamcity.RunStat> map = teamcity.runTestAnalysis();
            privCtx.ifPresent(ctx -> chainStatus.initFromContext(teamcity, ctx, map));
            res.servers.add(chainStatus);
        }
        return res;
    }

}
