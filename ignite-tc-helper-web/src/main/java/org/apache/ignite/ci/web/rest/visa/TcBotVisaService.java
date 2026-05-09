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
package org.apache.ignite.ci.web.rest.visa;

import org.apache.ignite.tcbot.common.application.TcBotApplicationContext;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.tcbot.visa.ContributionCheckStatus;
import org.apache.ignite.ci.tcbot.visa.ContributionToCheck;
import org.apache.ignite.ci.tcbot.visa.CurrentVisaStatus;
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.ci.tcbot.visa.VisaStatus;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path("visa")
@Produces(MediaType.APPLICATION_JSON)
public class TcBotVisaService {
    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    /** */
    @GET
    @Path("cancel")
    public boolean stopObservation(@NotNull @QueryParam("server") String srv,
        @NotNull @QueryParam("branch") String branchForTc) {
            return CtxListener.getApplicationContext(ctx)
                .getInstance(BuildObserver.class)
                .stopObservation(new ContributionKey(srv, branchForTc));
    }

    /**
     */
    @GET
    @Path("history")
    public Collection<VisaStatus> history(@Nullable @QueryParam("limit") Integer limit) {
        return CtxListener.getApplicationContext(ctx)
            .getInstance(TcBotTriggerAndSignOffService.class)
            .getVisasStatus(ITcBotUserCreds.get(req), limit == null ? 300 : limit);
    }

    /**
     * @param srvCode Server id.
     * @return Contribution list for PRs and branches can be checked by TC bot.
     */
    @GET
    @Path("contributions")
    public List<ContributionToCheck> contributions(@Nullable @QueryParam("serverId") String srvCode) {
        ITcBotUserCreds credsProv = ITcBotUserCreds.get(req);

        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);

        appCtx.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, credsProv);

        return appCtx.getInstance(TcBotTriggerAndSignOffService.class).getContributionsToCheck(srvCode, credsProv);
    }

    /**
     * @param srvCode Server id.
     * @return Contribution list after immediate GitHub refresh.
     */
    @GET
    @Path("contributions/refresh")
    public List<ContributionToCheck> refreshContributions(@Nullable @QueryParam("serverId") String srvCode,
        @Nullable @QueryParam("processId") Long processId) {
        ITcBotUserCreds credsProv = ITcBotUserCreds.get(req);

        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        BotProcessMonitor process = appCtx.getInstance(BotProcessMonitor.class);

        process.start(processId, "refreshContributions", "Sending PR refresh request to the bot REST API.");

        try {
            appCtx.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, credsProv);

            List<ContributionToCheck> res = appCtx.getInstance(TcBotTriggerAndSignOffService.class)
                .refreshContributionsToCheck(srvCode, credsProv, processId);

            process.finish(processId, "count=" + res.size());

            return res;
        }
        catch (RuntimeException e) {
            process.fail(processId, e);

            throw e;
        }
    }

    @GET
    @Path("contributionStatus")
    public Set<ContributionCheckStatus> contributionStatus(@Nullable @QueryParam("serverId") String srvCode,
        @QueryParam("prId") String prId) {
        ITcBotUserCreds prov = ITcBotUserCreds.get(req);

        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);

        appCtx.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, prov);

        return appCtx.getInstance(TcBotTriggerAndSignOffService.class).contributionStatuses(srvCode, prov, prId);
    }

    @GET
    @Path("visaStatus")
    public CurrentVisaStatus currentVisaStatus(@Nullable @QueryParam("serverId") String srvCode,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @QueryParam("tcBranch") String tcBranch) {
        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);

        ITcBotUserCreds prov = ITcBotUserCreds.get(req);

        appCtx.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, prov);

        return appCtx.getInstance(TcBotTriggerAndSignOffService.class)
            .currentVisaStatus(srvCode, prov, suiteId, tcBranch);
    }
}
