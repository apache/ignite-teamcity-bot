package org.apache.ignite.ci.web.rest;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.web.CtxListener;
import org.jetbrains.annotations.Nullable;

@Path("build")
@Produces(MediaType.APPLICATION_JSON)
public class TriggerBuild {
    @Context
    private ServletContext context;

    @GET
    @Path("trigger")
    public TriggerResult triggerBuild(
        @Nullable @QueryParam("serverId") String serverId,
        @Nullable @QueryParam("branchName") String branchName,
        @Nullable @QueryParam("suiteId") String suiteId) {

        try (final ITeamcity helper = CtxListener.getTcHelper(context).server(serverId)) {
            helper.triggerBuild(suiteId, branchName);
        }

        return new TriggerResult("OK");
    }

    public static class TriggerResult {
        public String result;

        public TriggerResult(String result) {
            this.result = result;
        }
    }

}
