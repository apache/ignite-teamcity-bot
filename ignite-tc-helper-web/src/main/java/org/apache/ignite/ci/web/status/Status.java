package org.apache.ignite.ci.web.status;

import java.io.StringWriter;
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
import org.apache.ignite.ci.runners.GenerateStatusHtml;
import org.apache.ignite.ci.web.CtxListener;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.runners.PrintChainResults.loadChainContext;
import static org.apache.ignite.ci.runners.PrintChainResults.printChainResults;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class Status {
    @Context
    private ServletContext context;

    @GET
    public Response getStatus() throws Exception {
        //Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);

        final StringWriter writer = new StringWriter();
        GenerateStatusHtml.generate(writer);

        return Response.status(200).entity(writer.toString()).build();

    }

}
