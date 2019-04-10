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

import com.google.inject.Injector;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.conf.ServerIntegrationLinks;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.github.pure.IGitHubConnectionProvider;
import org.apache.ignite.ci.jira.pure.IJiraIntegration;
import org.apache.ignite.ci.jira.pure.IJiraIntegrationProvider;
import org.apache.ignite.ci.tcbot.conf.IJiraServerConfig;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.trigger.TriggerResult;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.exception.ServiceUnauthorizedException;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;

@Path("build")
@Produces(MediaType.APPLICATION_JSON)
public class TriggerBuilds {
    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path("trigger")
    public TriggerResult triggerBuilds(
        @Nullable @QueryParam("serverId") String srvCode,
        @Nullable @QueryParam("branchName") String branchForTc,
        @Nonnull @QueryParam("parentSuiteId") String parentSuiteId,
        @Nonnull @QueryParam("suiteIdList") String suiteIdList,
        @Nullable @QueryParam("top") Boolean top,
        @Nullable @QueryParam("observe") Boolean observe,
        @Nullable @QueryParam("ticketId") String ticketId,
        @Nullable @QueryParam("prNum") String prNum
    ) {
        ICredentialsProv prov = ICredentialsProv.get(req);
        Injector injector = CtxListener.getInjector(ctx);

        injector.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, prov);

        if (isNullOrEmpty(suiteIdList))
            return new TriggerResult("Error: nothing to run.");

        String jiraRes = injector
            .getInstance(TcBotTriggerAndSignOffService.class)
            .triggerBuildsAndObserve(srvCode, branchForTc, parentSuiteId, suiteIdList, top, observe, ticketId, prNum, prov);

        return new TriggerResult("Tests started." + (!jiraRes.isEmpty() ? "<br>" + jiraRes : ""));
    }

    /**
     * @param srvCode Server id.
     * @param branchForTc Branch for tc.
     * @param suiteId Suite id.
     * @param ticketId Ticket full name with IGNITE- prefix.
     */
    @GET
    @Path("commentJira")
    public SimpleResult commentJira(
        @Nullable @QueryParam("serverId") String srvCode,
        @Nullable @QueryParam("branchName") String branchForTc,
        @Nullable @QueryParam("suiteId") String suiteId,
        @Nullable @QueryParam("ticketId") String ticketId
    ) {
        ICredentialsProv prov = ICredentialsProv.get(req);

        Injector injector = CtxListener.getInjector(ctx);

        injector.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, prov);

        return injector
            .getInstance(TcBotTriggerAndSignOffService.class)
            .commentJiraEx(srvCode, branchForTc, suiteId, ticketId, prov);
    }

    @GET
    @Path("integrationUrls")
    public Set<ServerIntegrationLinks> getIntegrationUrls(@NotNull @QueryParam("serverIds") String srvCodes) {
        ICredentialsProv prov = ICredentialsProv.get(req);

        Injector injector = CtxListener.getInjector(ctx);

        ITcBotConfig cfg = injector.getInstance(ITcBotConfig.class);
        ITeamcityIgnitedProvider tcIgnProv = injector.getInstance(ITeamcityIgnitedProvider.class);

        String[] srvCodesArr = srvCodes.split(",");

        return Arrays.stream(srvCodesArr).map(srvCode -> {
            if (!tcIgnProv.hasAccess(srvCode, prov))
                return null;

            IGitHubConnection gh = injector.getInstance(IGitHubConnectionProvider.class).server(srvCode);

            IJiraServerConfig jiraCfg = cfg.getJiraConfig(srvCode);

            return new ServerIntegrationLinks(srvCode, gh.config().gitApiUrl(), jiraCfg.restApiUrl());
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
