package org.apache.ignite.ci.web.rest;

import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.HelperConfig;

/**
 * Created by Дмитрий on 05.11.2017.
 */

@Path("branches")
@Produces(MediaType.APPLICATION_JSON)
public class GetTrackedBranches {

    @GET
    @Path("getIds")
    public List<String> getIds() {
        return HelperConfig.getTrackedBranches().getIds();
    }

}
