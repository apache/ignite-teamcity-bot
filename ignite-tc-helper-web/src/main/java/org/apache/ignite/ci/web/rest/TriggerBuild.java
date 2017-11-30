package org.apache.ignite.ci.web.rest;

import com.google.common.base.Strings;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.IgniteTeamcityHelper;
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

        try (final IgniteTeamcityHelper helper = new IgniteTeamcityHelper(serverId)) {
            helper.triggerBuild(suiteId, branchName);
        }

        final Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);

        final IgniteCache<Object, Object> cache = ignite.cache(GetCurrTestFailures.CACHE_NAME);
        if (cache != null)
            cache.remove(branchName);

        return new TriggerResult("OK");
    }

    public static class TriggerResult {
        public String result;

        public TriggerResult(String result) {
            this.result = result;
        }
    }

}
