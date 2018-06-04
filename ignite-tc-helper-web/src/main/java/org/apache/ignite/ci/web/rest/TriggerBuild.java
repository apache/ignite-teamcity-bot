package org.apache.ignite.ci.web.rest;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.login.ServiceUnauthorizedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path("build")
@Produces(MediaType.APPLICATION_JSON)
public class TriggerBuild {
    @Context
    private ServletContext context;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("trigger")
    public TriggerResult triggerBuild(
        @Nullable @QueryParam("serverId") String serverId,
        @Nullable @QueryParam("branchName") String branchName,
        @Nullable @QueryParam("suiteId") String suiteId,
        @Nullable @QueryParam("top") Boolean top) {

        final ICredentialsProv prov = ICredentialsProv.get(req);

        if(!prov.hasAccess(serverId)) {
            throw ServiceUnauthorizedException.noCreds(serverId);
        }

        try (final ITeamcity helper = CtxListener.getTcHelper(context).server(serverId, prov)) {
            helper.triggerBuild(suiteId, branchName, top != null && top);
        }

        return new TriggerResult("OK");
    }

    @GET
    @Path("triggerBuilds")
    public TriggerResult triggerBuilds(
        @Nullable @QueryParam("serverId") String serverId,
        @Nullable @QueryParam("branchName") String branchName,
        @NotNull @QueryParam("suiteIdList") String suiteIdList,
        @Nullable @QueryParam("top") Boolean top) {

        final ICredentialsProv prov = ICredentialsProv.get(req);

        if(!prov.hasAccess(serverId)) {
            throw ServiceUnauthorizedException.noCreds(serverId);
        }

        List<String> strings = Arrays.asList(suiteIdList.split(","));
        if (strings.isEmpty())
            return new TriggerResult("Error: nothing to run");

        try (final ITeamcity helper = CtxListener.getTcHelper(context).server(serverId, prov)) {
            boolean queueToTop = top != null && top;

            for (String suiteId : strings) {
                System.out.println("Triggering [ " + suiteId + "," + branchName + "," + "top=" + queueToTop + "]");

                helper.triggerBuild(suiteId, branchName, queueToTop);
            }
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
