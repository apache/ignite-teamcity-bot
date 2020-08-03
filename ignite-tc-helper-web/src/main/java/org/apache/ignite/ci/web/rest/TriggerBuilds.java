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

import org.apache.ignite.githubservice.IGitHubConnection;
import org.apache.ignite.githubservice.IGitHubConnectionProvider;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.trigger.TriggerResult;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.ci.web.CtxListener;
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

    /**
     * Triggers re-running possible blocker suites.
     *
     * @param srvCodeOrAlias Server code or alias (e.g. Apache Ignite, GridGain, GGPrivate).
     * @param branchForTc Branch name for TeamCity triggering.
     * @param parentSuiteId Parent suite id for suite need to be re-run.
     * @param suiteIdList Suite ids need to be re-run (possible blockers).
     * @param top If {@code true} re-running suites will be placed at the top of TC queue.
     * @param observe If {@code true} JIRA will be commented with current state of possible blockers.
     * @param ticketId JIRA ticket id.
     * @param prNum Pull request number in appropriate project (@code srvCodeOrAlias).
     * @param baseBranchForTc Base branch for possible blockers comparison (e.g. master, 8.8-master)
     * @return Result of triggering suites re-run.
     */
    @GET
    @Path("trigger")
    public TriggerResult triggerBuilds(
        @Nullable @QueryParam("srvCode") String srvCodeOrAlias,
        @Nullable @QueryParam("branchName") String branchForTc,
        @Nonnull @QueryParam("parentSuiteId") String parentSuiteId,
        @Nonnull @QueryParam("suiteIdList") String suiteIdList,
        @Nullable @QueryParam("top") Boolean top,
        @Nullable @QueryParam("observe") Boolean observe,
        @Nullable @QueryParam("ticketId") String ticketId,
        @Nullable @QueryParam("prNum") String prNum,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nonnull @QueryParam("cleanRebuild") Boolean cleanRebuild
    ) {
        ITcBotUserCreds prov = ITcBotUserCreds.get(req);
        Injector injector = CtxListener.getInjector(ctx);

        injector.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCodeOrAlias, prov);

        if (isNullOrEmpty(suiteIdList))
            return new TriggerResult("Error: nothing to run.");

        String jiraRes = injector
            .getInstance(TcBotTriggerAndSignOffService.class)
            .triggerBuildsAndObserve(srvCodeOrAlias, branchForTc, parentSuiteId, suiteIdList, top, observe, ticketId, prNum, baseBranchForTc, cleanRebuild, prov);

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
        @Nullable @QueryParam("ticketId") String ticketId,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc
    ) {
        ITcBotUserCreds prov = ITcBotUserCreds.get(req);

        Injector injector = CtxListener.getInjector(ctx);

        injector.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, prov);

        return injector
            .getInstance(TcBotTriggerAndSignOffService.class)
            .commentJiraEx(srvCode, branchForTc, suiteId, ticketId, baseBranchForTc, prov);
    }

    @GET
    @Path("integrationUrls")
    public Set<ServerIntegrationLinks> getIntegrationUrls(@NotNull @QueryParam("serverIds") String srvCodes) {
        ITcBotUserCreds prov = ITcBotUserCreds.get(req);

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
