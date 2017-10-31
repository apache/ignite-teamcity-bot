package org.apache.ignite.ci.web.rest;

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
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.current.ChainCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.FailureDetails;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.runners.PrintChainResults.loadChainContext;
import static org.apache.ignite.ci.runners.PrintChainResults.printChainResults;

@Path("current")
@Produces(MediaType.APPLICATION_JSON)
public class CurrentFailures {
    @Context
    private ServletContext context;

    @GET
    public FailureDetails getMsg(@Nullable @QueryParam("branch") String branch) {

        Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);

        final FailureDetails res = new FailureDetails();

        Optional<FullChainRunCtx> pubCtx;
        Optional<FullChainRunCtx> privCtx;

        boolean includeLatestRebuild = true;
        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
            String suiteId = "Ignite20Tests_RunAll";
            //todo config branches and its names
            String branchPub =
                (isNullOrEmpty(branch) || "master".equals(branch)) ? "<default>" : "pull/2508/head";
            pubCtx = loadChainContext(teamcity, suiteId, branchPub, includeLatestRebuild);

            final ChainCurrentStatus chainStatus = new ChainCurrentStatus();
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
            privCtx = loadChainContext(teamcity, suiteId, branchPriv, includeLatestRebuild);

            final ChainCurrentStatus chainStatus = new ChainCurrentStatus();
            chainStatus.serverName = teamcity.serverId();
            final Map<String, IgnitePersistentTeamcity.RunStat> map = teamcity.runTestAnalysis();
            privCtx.ifPresent(ctx -> chainStatus.initFromContext(teamcity, ctx, map));
            res.servers.add(chainStatus);
        }

        String builder = printChainResults(pubCtx, "<Public>") +
            printChainResults(privCtx, "<Private>");

        return res;

    }

}
