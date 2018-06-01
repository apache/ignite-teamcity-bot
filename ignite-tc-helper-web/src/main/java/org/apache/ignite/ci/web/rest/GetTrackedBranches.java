package org.apache.ignite.ci.web.rest;

import java.util.List;
import java.util.Set;
import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.conf.ChainAtServer;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.Version;
import org.apache.ignite.lang.IgniteProductVersion;

/**
 * Created by Дмитрий on 05.11.2017.
 */

@Path("branches")
@Produces(MediaType.APPLICATION_JSON)
public class GetTrackedBranches {

    @Context
    private ServletContext context;

    @GET
    @Path("version")
    @PermitAll
    public Version version() {
        Version version = new Version();

        IgniteProductVersion ignProdVer = CtxListener.getIgnite(context).version();

        String ignVer = ignProdVer.major() + "." + ignProdVer.minor() + "." + ignProdVer.maintenance();

        version.ignVer = ignVer;
        version.ignVerFull = ignProdVer.toString();

        return version;
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

    @GET
    @Path("getServerIds")
    public Set<String> getServerIds() {
        return HelperConfig.getTrackedBranches().getServerIds();
    }

}
