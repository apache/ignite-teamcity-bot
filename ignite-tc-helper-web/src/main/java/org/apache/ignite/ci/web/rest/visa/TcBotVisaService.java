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

import com.google.common.base.Strings;
import com.google.inject.Injector;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.tcbot.visa.*;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
            return CtxListener.getInjector(ctx)
                .getInstance(BuildObserver.class)
                .stopObservation(new ContributionKey(srv, branchForTc));
    }

    /**
     * @param srvId Server id.
     */
    @GET
    @Path("history")
    public Collection<VisaStatus> history(@Nullable @QueryParam("serverId") String srvId) {
        return CtxListener.getInjector(ctx)
            .getInstance(TcBotTriggerAndSignOffService.class)
            .getVisasStatus(srvId, ITcBotUserCreds.get(req));
    }

    /**
     * @param srvCode Server id.
     * @return Contribution list for PRs and branches can be checked by TC bot.
     */
    @GET
    @Path("contributions")
    public List<ContributionToCheck> contributions(@Nullable @QueryParam("serverId") String srvCode) {
        ITcBotUserCreds credsProv = ITcBotUserCreds.get(req);

        Injector injector = CtxListener.getInjector(ctx);

        injector.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, credsProv);

        return injector.getInstance(TcBotTriggerAndSignOffService.class).getContributionsToCheck(srvCode, credsProv);
    }

    @GET
    @Path("readyForReview")
    public List<ContributionToCheck> readyForReview(@Nullable @QueryParam("serverId") String srvCode) {
        final List<ContributionToCheck> contributions = contributions(srvCode);

        return contributions.stream().filter(
                c -> !Strings.isNullOrEmpty(c.tcBranchName)
        ).collect(Collectors.toList());
        /*
        todo check current visa status from

        return injector.getInstance(TcBotTriggerAndSignOffService.class)
            .currentVisaStatus(srvCode, prov, suiteId, tcBranch); <-Without caching
         */
    }

    @GET
    @Path("contributionStatus")
    public Set<ContributionCheckStatus> contributionStatus(@Nullable @QueryParam("serverId") String srvCode,
        @QueryParam("prId") String prId) {
        ITcBotUserCreds prov = ITcBotUserCreds.get(req);

        Injector injector = CtxListener.getInjector(ctx);

        injector.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, prov);

        return injector.getInstance(TcBotTriggerAndSignOffService.class).contributionStatuses(srvCode, prov, prId);
    }

    @GET
    @Path("visaStatus")
    public CurrentVisaStatus currentVisaStatus(@Nullable @QueryParam("serverId") String srvCode,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @QueryParam("tcBranch") String tcBranch) {
        Injector injector = CtxListener.getInjector(ctx);

        ITcBotUserCreds prov = ITcBotUserCreds.get(req);

        injector.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, prov);

        return injector.getInstance(TcBotTriggerAndSignOffService.class)
            .currentVisaStatus(srvCode, prov, suiteId, tcBranch);
    }
}
