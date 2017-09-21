package org.apache.ignite.ci.web.rest;

import java.util.Optional;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.web.CtxListener;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.runners.PrintChainResults.loadChainContext;
import static org.apache.ignite.ci.runners.PrintChainResults.printChainResults;

@Path("failures")
@Produces(MediaType.TEXT_PLAIN)
public class Failures {
    @Context
    private ServletContext context;

    @GET
    public Response getMsg(@Nullable @QueryParam("branch") String branch) {
        Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);
        Optional<FullChainRunCtx> pubCtx;
        Optional<FullChainRunCtx> privCtx;
        boolean includeLatestRebuild = true;
        try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
            String suiteId = "Ignite20Tests_RunAll";
            //todo config
            String branchPub =
                (isNullOrEmpty(branch) || "master".equals(branch)) ? "<default>" : "pull/2508/head";
            pubCtx = loadChainContext(teamcity, suiteId, branchPub, includeLatestRebuild);
        }

        try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
            String suiteId = "id8xIgniteGridGainTests_RunAll";
            //todo config
            String branchPriv =
                (isNullOrEmpty(branch) || "master".equals(branch)) ? "<default>" : "ignite-2.1.5";
            privCtx = loadChainContext(teamcity, suiteId, branchPriv, includeLatestRebuild);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<Public>\n");
        builder.append(printChainResults(pubCtx));
        builder.append("<Private>\n");
        builder.append(printChainResults(privCtx));

        return Response.status(200).entity(builder.toString()).build();

    }

}
