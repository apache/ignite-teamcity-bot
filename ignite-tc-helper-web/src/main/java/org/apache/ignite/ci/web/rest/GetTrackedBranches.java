package org.apache.ignite.ci.web.rest;

import java.util.List;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.conf.ChainAtServer;
import org.apache.ignite.ci.web.rest.model.Version;

/**
 * Created by Дмитрий on 05.11.2017.
 */

@Path("branches")
@Produces(MediaType.APPLICATION_JSON)
public class GetTrackedBranches {
    @GET
    @Path("version")
    public Version version() {
        return new Version();
    }

    @GET
    @Path("getIds")
    public List<String> getIds() {
        return HelperConfig.getTrackedBranches().getIds();
    }

    @GET
    @Path("suites")
    public Set<ChainAtServer> getSuites() {
        return HelperConfig.getTrackedBranches().chainAtServers();
    }
    //http://localhost:8080/pr.html?serverId=private&branchForTc=ignite-gg-12790-1&suiteId=id8xIgniteGridGainTests_RunAll
}
