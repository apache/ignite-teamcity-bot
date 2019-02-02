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

import com.google.common.base.Strings;
import com.google.inject.Injector;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.conf.ChainAtServer;
import org.apache.ignite.ci.tcbot.TcBotGeneralService;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.Version;

/**
 * Service for returning tracked branches, servers, and the bot version.
 */
@Path("branches")
@Produces(MediaType.APPLICATION_JSON)
public class GetTrackedBranches {
    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path("version")
    @PermitAll
    public Version version() {
        return CtxListener.getInjector(ctx).getInstance(TcBotGeneralService.class).version();
    }

    @GET
    @Path("getIds")
    public List<String> getIds() {
        return CtxListener.getInjector(ctx).getInstance(ITcBotConfig.class).getTrackedBranchesIds();
    }

    /**
     * Return all suites involved into tracked branches.
     *
     * @param srvId Optional service ID to additiona filtering of chains.
     */
    @GET
    @Path("suites")
    public Set<ChainAtServer> getSuites(@Nullable @QueryParam("server") String srvId) {
        final ICredentialsProv prov = ICredentialsProv.get(req);

        Injector injector = CtxListener.getInjector(ctx);
        ITcBotConfig cfg = injector.getInstance(ITcBotConfig.class);
        return cfg.getTrackedBranches()
            .getSuitesUnique()
            .stream()
            .filter(chainAtSrv ->
                Strings.isNullOrEmpty(srvId)
                    || srvId.equals(chainAtSrv.serverId))
            .filter(chainAtServer -> prov.hasAccess(chainAtServer.serverId))
            .collect(Collectors.toSet());
    }

    /**
     * Return all servers registered in TC Bot config: Both from tracked branches and from
     */
    @GET
    @Path("getServerIds")
    public Set<String> getServerIds() {
        final ICredentialsProv prov = ICredentialsProv.get(req);

        Injector injector = CtxListener.getInjector(ctx);
        ITcBotConfig cfg = injector.getInstance(ITcBotConfig.class);

        ITeamcityIgnitedProvider tcProv = injector.getInstance(ITeamcityIgnitedProvider.class);
        return cfg.getServerIds()
            .stream()
            .filter(srvId ->
            {
                return tcProv.hasAccess(srvId, prov);

            })
            .collect(Collectors.toSet());
    }

}
