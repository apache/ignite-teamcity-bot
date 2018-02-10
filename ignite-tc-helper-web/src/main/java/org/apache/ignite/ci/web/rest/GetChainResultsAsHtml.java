package org.apache.ignite.ci.web.rest;

import java.util.Optional;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.current.ChainAtServerCurrentStatus;

import static javax.ws.rs.core.MediaType.TEXT_HTML;

/**
 * Created by Дмитрий on 10.02.2018.
 */
@Path("chainResults")
public class GetChainResultsAsHtml {

    @Context
    private ServletContext context;

    /*
    @GET
    @Path("failures")
    public TestFailuresSummary getTestFails(@Nullable @QueryParam("branch") String branchOrNull) {
        final String key = Strings.nullToEmpty(branchOrNull);
        final BackgroundUpdater updater = (BackgroundUpdater)context.getAttribute(CtxListener.UPDATER);
        return updater.get("", key, this::getTestFailsNoCache);
    }
    */

    public void showChainOnServersResults(StringBuilder res, Integer buildId, String serverId) {
        final Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);

        final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, serverId)) {
            //processChainByRef(teamcity, includeLatestRebuild, build, true, true)
            String hrefById = teamcity.getBuildHrefById(buildId);
            BuildRef build = new BuildRef();
            build.href = hrefById;
            Optional<FullChainRunCtx> ctx =
                BuildChainProcessor.processChainByRef(teamcity, false, build, true, true);

            res.append(ctx);
        }
    }

    @GET
    @Produces(TEXT_HTML)
    @Path("html")
    public String getChainRes(@QueryParam("serverId") String serverId, @QueryParam("buildId") Integer buildId) {
        StringBuilder builder = new StringBuilder();
        builder.append("<html>\n");
        builder.append("<head>\n");
        builder.append("    <title>Ignite Teamcity - current failures</title>");
        builder.append(" <script src=\"https://code.jquery.com/jquery-1.12.4.js\"></script>\n");
        builder.append("    <script src=\"https://code.jquery.com/ui/1.12.1/jquery-ui.js\"></script>\n");
        builder.append("\n");
        builder.append("    <link rel=\"icon\" href=\"https://pbs.twimg.com/profile_images/568493154500747264/xTBxO73F.png\">\n");
        builder.append("    <link rel=\"stylesheet\" href=\"https://code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css\">");
        builder.append("</head>");
        builder.append("<body>\n");
        builder.append("<script>\n");
        builder.append("$(document).ready(function() {\n");
        builder.append("    $( document ).tooltip();\n");
        builder.append("     \n");
        builder.append("}); \n");
        builder.append("</script>\n");
        builder.append("\n");
        showChainOnServersResults(builder, buildId, serverId)      ;

        builder.append("\n");
        builder.append("</body>\n");
        builder.append("</html>");
        String s = builder.toString();
        return s;
    }

}
