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

package org.apache.ignite.ci.web.rest.build;

import com.google.common.collect.BiMap;
import java.text.ParseException;

import com.google.inject.Injector;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.conf.TcServerConfig;
import org.apache.ignite.ci.tcbot.trends.MasterTrendsService;
import org.apache.ignite.ci.teamcity.ignited.SyncMode;
import org.apache.ignite.ci.teamcity.ignited.buildcondition.BuildCondition;
import org.apache.ignite.ci.tcbot.chain.BuildChainProcessor;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.current.BuildStatisticsSummary;
import org.apache.ignite.ci.web.model.hist.BuildsHistory;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.model.current.UpdateInfo;
import org.apache.ignite.ci.web.rest.exception.ServiceUnauthorizedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Strings.isNullOrEmpty;

@Path(GetBuildTestFailures.BUILD)
@Produces(MediaType.APPLICATION_JSON)
public class GetBuildTestFailures {
    public static final String BUILD = "build";

    /** Context. */
    @Context
    private ServletContext ctx;

    /** Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path("failures/updates")
    public UpdateInfo getTestFailsUpdates(
        @QueryParam("serverId") String srvId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) throws ServiceUnauthorizedException {
        return new UpdateInfo().copyFrom(getBuildTestFailsNoSync(srvId, buildId, checkAllLogs));
    }

    @GET
    @Path("failures/txt")
    @Produces(MediaType.TEXT_PLAIN)
    public String getTestFailsText(
        @QueryParam("serverId") String srvId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) throws ServiceUnauthorizedException {
        return getBuildTestFails(srvId, buildId, checkAllLogs).toString();
    }

    @GET
    @Path("failuresNoSync")
    public TestFailuresSummary getBuildTestFailsNoSync(
        @QueryParam("serverId") String srvId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return collectBuildCtxById(srvId, buildId, checkAllLogs, SyncMode.NONE);
    }

    @GET
    @Path("failures")
    @NotNull public TestFailuresSummary getBuildTestFails(
        @QueryParam("serverId") String srvId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return collectBuildCtxById(srvId, buildId, checkAllLogs, SyncMode.RELOAD_QUEUED);
    }

    @NotNull public TestFailuresSummary collectBuildCtxById(@QueryParam("serverId") String srvCode,
        @QueryParam("buildId") Integer buildId,
        @QueryParam("checkAllLogs") @Nullable Boolean checkAllLogs, SyncMode syncMode) {
        final ICredentialsProv prov = ICredentialsProv.get(req);
        final Injector injector = CtxListener.getInjector(ctx);
        ITeamcityIgnitedProvider tcIgnitedProv = injector.getInstance(ITeamcityIgnitedProvider.class);
        ITcServerProvider tcSrvProvider = injector.getInstance(ITcServerProvider.class);
        final BuildChainProcessor buildChainProcessor = injector.getInstance(BuildChainProcessor.class);

        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        tcIgnitedProv.checkAccess(srvCode, prov);

        IAnalyticsEnabledTeamcity teamcity = tcSrvProvider.server(srvCode, prov);
        ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCode, prov);

        String failRateBranch = ITeamcity.DEFAULT;

        ProcessLogsMode procLogs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE;

        final FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(teamcity,
            tcIgnited,
            Collections.singletonList(buildId),
            LatestRebuildMode.NONE,
            procLogs,
            false,
            failRateBranch,
            syncMode);

        final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus(srvCode, ctx.branchName());

        int cnt = (int) ctx.getRunningUpdates().count();
        if (cnt > 0)
            runningUpdates.addAndGet(cnt);

        chainStatus.initFromContext(tcIgnited, ctx, failRateBranch);

        res.addChainOnServer(chainStatus);

        res.postProcess(runningUpdates.get());

        return res;
    }

    /**
     * Mark builds as "valid" or "invalid".
     *
     * @param buildId Build id.
     * @param isValid Is valid.
     * @param field Field.
     * @param srvIdOpt Server code (optional)
     */
    @GET
    @Path("condition")
    public Boolean setBuildCondition(
        @QueryParam("buildId") Integer buildId,
        @QueryParam("isValid") Boolean isValid,
        @QueryParam("field") String field,
        @QueryParam("serverId") String srvIdOpt) {
        Injector injector = CtxListener.getInjector(ctx);

        String srvCode = isNullOrEmpty(srvIdOpt)
            ? injector.getInstance(ITcBotConfig.class).primaryServerCode()
            : srvIdOpt;

        if (buildId == null || isValid == null)
            return null;

        ITeamcityIgnitedProvider tcIgnitedProv = injector.getInstance(ITeamcityIgnitedProvider.class);

        ICredentialsProv prov = ICredentialsProv.get(req);

        tcIgnitedProv.checkAccess(srvCode, prov);

        ITeamcityIgnited tcIgn = tcIgnitedProv.server(srvCode, prov);

        BiMap<String, String> problemNames = BuildStatisticsSummary.fullProblemNames;

        BuildCondition buildCond =
            new BuildCondition(buildId, prov.getPrincipalId(), isValid, problemNames.getOrDefault(field, field));

        return tcIgn.setBuildCondition(buildCond);
    }

    /**
     * @param srvCode Server id.
     * @param buildType Build type.
     * @param branch Branch.
     * @param sinceDate Since date.
     * @param untilDate Until date.
     * @param skipTests Skip tests.
     */
    @GET
    @Path("history")
    public BuildsHistory getBuildsHistory(
        @Nullable @QueryParam("server") String srvCode,
        @Nullable @QueryParam("buildType") String buildType,
        @Nullable @QueryParam("branch") String branch,
        @Nullable @QueryParam("sinceDate") String sinceDate,
        @Nullable @QueryParam("untilDate") String untilDate,
        @Nullable @QueryParam("skipTests") String skipTests)  throws ParseException {

        Injector injector = CtxListener.getInjector(ctx);

        BuildsHistory.Builder builder = new BuildsHistory.Builder()
            .branch(branch)
            .server(srvCode)
            .buildType(buildType)
            .sinceDate(sinceDate)
            .untilDate(untilDate);

        if (Boolean.valueOf(skipTests))
            builder.skipTests();

        BuildsHistory buildsHist = builder.build(injector);

        ICredentialsProv prov = ICredentialsProv.get(req);

        injector.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, prov);

        buildsHist.initialize(prov);

        if (MasterTrendsService.DEBUG)
            System.out.println("MasterTrendsService: Responding");

        return buildsHist;
    }
}
