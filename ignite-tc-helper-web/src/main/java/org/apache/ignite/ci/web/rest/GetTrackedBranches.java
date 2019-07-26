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
import java.util.stream.Stream;
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

import org.apache.ignite.tcbot.engine.conf.ChainAtServer;
import org.apache.ignite.ci.tcbot.TcBotGeneralService;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranchesConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedChain;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.Version;
import org.jetbrains.annotations.NotNull;

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
    public List<String> getIdsIfAccessible() {
        Stream<ITrackedBranch> stream = accessibleTrackedBranches();
        return stream
                .map(ITrackedBranch::name)
                .collect(Collectors.toList());
    }

    @NotNull public Stream<ITrackedBranch> accessibleTrackedBranches() {
        ITcBotUserCreds prov = ITcBotUserCreds.get(req);
        Injector injector = CtxListener.getInjector(ctx);
        ITcBotConfig cfg = injector.getInstance(ITcBotConfig.class);
        ITeamcityIgnitedProvider tcProv = injector.getInstance(ITeamcityIgnitedProvider.class);

        return cfg.getTrackedBranches().branchesStream()
            .filter(bt ->
                bt.chainsStream().anyMatch(chain -> tcProv.hasAccess(chain.serverCode(), prov)));
    }

    /**
     * Get Unique suites involved into tracked branches
     * @param trackedBranches
     */
    public Set<ChainAtServer> getSuitesUnique(ITrackedBranchesConfig trackedBranches) {
        return trackedBranches.branchesStream()
                .flatMap(ITrackedBranch::chainsStream)
                .map(ChainAtServer::new) // to produce object with another equals
                .collect(Collectors.toSet());
    }

    /**
     * Return all suites involved into tracked branches.
     *
     * @param srvId Optional service ID to additional filtering of chains.
     */
    @GET
    @Path("suites")
    public Set<ChainAtServer> getSuites(@Nullable @QueryParam("server") String srvId) {
        ITcBotUserCreds prov = ITcBotUserCreds.get(req);
        Injector injector = CtxListener.getInjector(ctx);
        ITcBotConfig cfg = injector.getInstance(ITcBotConfig.class);
        ITeamcityIgnitedProvider tcProv = injector.getInstance(ITeamcityIgnitedProvider.class);

        return getSuitesUnique(cfg.getTrackedBranches())
            .stream()
            .filter(chainAtSrv ->
                Strings.isNullOrEmpty(srvId)
                    || srvId.equals(chainAtSrv.getServerId()))
            .filter(chainAtServer -> tcProv.hasAccess(chainAtServer.serverId, prov))
            .collect(Collectors.toSet());
    }

    /**
     * Return all accessible servers registered in TC Bot config: Both from tracked branches and from
     */
    @GET
    @Path("getServerIds")
    public Set<String> getServerIds() {
        ITcBotUserCreds prov = ITcBotUserCreds.get(req);
        Injector injector = CtxListener.getInjector(ctx);
        ITcBotConfig cfg = injector.getInstance(ITcBotConfig.class);
        ITeamcityIgnitedProvider tcProv = injector.getInstance(ITeamcityIgnitedProvider.class);

        return cfg.getServerIds()
            .stream()
            .filter(srvId -> tcProv.hasAccess(srvId, prov))
            .collect(Collectors.toSet());
    }


    /**
     * Finds all registere unique teamcity branches.
     * @param srvCodeOrAlias Server code or its alisas.
     */
    @GET
    @Path("tcBranches")
    public Set<String> tcBranches(@Nullable @QueryParam("srvCode") String srvCodeOrAlias) {
        ITeamcityIgnited srv = CtxListener.getInjector(ctx)
            .getInstance(ITeamcityIgnitedProvider.class)
            .server(srvCodeOrAlias, ITcBotUserCreds.get(req));

        String srvCode = srv.serverCode();

        return accessibleTrackedBranches()
            .flatMap(tb -> {
                return tb.chainsStream().filter(tc ->
                    Strings.isNullOrEmpty(srvCode)
                        || srvCode.equals(tc.serverCode()));
            })
            .map(ITrackedChain::tcBranch).collect(Collectors.toSet());
    }
}
