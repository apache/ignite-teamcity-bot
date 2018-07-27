/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci.web.rest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.conf.ChainAtServer;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.Version;
import org.apache.ignite.lang.IgniteProductVersion;

/**
 * Created by Дмитрий on 05.11.2017.
 */

@Path("branches")
@Produces(MediaType.APPLICATION_JSON)
public class GetTrackedBranches {

    @Context
    private ServletContext context;

    @Context
    private HttpServletRequest request;

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
        return CtxListener.getTcHelper(context).getTrackedBranchesIds();
    }

    @GET
    @Path("suites")
    public Set<ChainAtServer> getSuites() {
        final ICredentialsProv prov = ICredentialsProv.get(request);

        return HelperConfig.getTrackedBranches()
                .chainAtServers()
                .stream()
                .filter(chainAtServer -> prov.hasAccess(chainAtServer.serverId))
                .collect(Collectors.toSet());
    }

    @GET
    @Path("getServerIds")
    public Set<String> getServerIds() {
        final ICredentialsProv prov = ICredentialsProv.get(request);

        return HelperConfig.getTrackedBranches()
                .getServerIds()
                .stream()
                .filter(prov::hasAccess)
                .collect(Collectors.toSet());
    }

}
