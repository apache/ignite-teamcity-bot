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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import com.google.inject.Injector;
import org.apache.ignite.ci.tcbot.condition.BuildCondition;
import org.apache.ignite.ci.tcbot.chain.BuildChainProcessor;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedImpl;
import org.apache.ignite.ci.tcmodel.result.tests.TestRef;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.hist.BuildsHistory;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.model.current.UpdateInfo;
import org.apache.ignite.ci.web.rest.exception.ServiceUnauthorizedException;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
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

@Path(GetBuildTestFailures.BUILD)
@Produces(MediaType.APPLICATION_JSON)
public class GetBuildTestFailures {
    public static final String BUILD = "build";
    public static final String TEST_FAILURES_SUMMARY_CACHE_NAME = BUILD + "TestFailuresSummary";
    public static final String BUILDS_STATISTICS_SUMMARY_CACHE_NAME = BUILD + "sStatisticsSummary";
    @Context
    private ServletContext ctx;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("failures/updates")
    public UpdateInfo getTestFailsUpdates(
        @QueryParam("serverId") String serverId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) throws ServiceUnauthorizedException {
        return new UpdateInfo().copyFrom(getBuildTestFails(serverId, buildId, checkAllLogs));
    }

    @GET
    @Path("failures/txt")
    @Produces(MediaType.TEXT_PLAIN)
    public String getTestFailsText(
        @QueryParam("serverId") String serverId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) throws ServiceUnauthorizedException {
        return getBuildTestFails(serverId, buildId, checkAllLogs).toString();
    }

    @GET
    @Path("failures")
    public TestFailuresSummary getBuildTestFails(
        @QueryParam("serverId") String serverId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs)
        throws ServiceUnauthorizedException {

        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(ctx);

        final ICredentialsProv prov = ICredentialsProv.get(req);

        FullQueryParams param = new FullQueryParams();
        param.setServerId(serverId);
        param.setBuildId(buildId);
        param.setCheckAllLogs(checkAllLogs);
        return updater.get(TEST_FAILURES_SUMMARY_CACHE_NAME, prov, param,
            (k) -> getBuildTestFailsNoCache(k.getServerId(), k.getBuildId(), k.getCheckAllLogs()), true);
    }

    @GET
    @Path("failuresNoCache")
    @NotNull public TestFailuresSummary getBuildTestFailsNoCache(
        @QueryParam("serverId") String serverId,
        @QueryParam("buildId") Integer buildId,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        final ITcHelper helper = CtxListener.getTcHelper(ctx);
        final ICredentialsProv prov = ICredentialsProv.get(req);
        final Injector injector = CtxListener.getInjector(ctx);
        final BuildChainProcessor buildChainProcessor = injector.getInstance(BuildChainProcessor.class);

        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        if(!prov.hasAccess(serverId))
            throw ServiceUnauthorizedException.noCreds(serverId);

        IAnalyticsEnabledTeamcity teamcity = helper.server(serverId, prov);

        //processChainByRef(teamcity, includeLatestRebuild, build, true, true)
        String hrefById = teamcity.getBuildHrefById(buildId);
        BuildRef build = new BuildRef();
        build.setId(buildId);
        build.href = hrefById;
        String failRateBranch = ITeamcity.DEFAULT;

        ProcessLogsMode procLogs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE;

        final FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(teamcity, Collections.singletonList(build),
                LatestRebuildMode.NONE,
                procLogs, false,
            failRateBranch);


        final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus(serverId, ctx.branchName());

        int cnt = (int) ctx.getRunningUpdates().count();
        if (cnt > 0)
            runningUpdates.addAndGet(cnt);

        chainStatus.initFromContext(teamcity, ctx, teamcity, failRateBranch);

        res.addChainOnServer(chainStatus);


        res.postProcess(runningUpdates.get());

        return res;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("testRef")
    public String getTestRef(
        @NotNull @QueryParam("testName") String name,
        @NotNull @QueryParam("suiteName") String suiteName,
        @Nullable @QueryParam("server") String srv,
        @Nullable @QueryParam("projectId") String projectId)
        throws InterruptedException, ExecutionException, ServiceUnauthorizedException {
        final ITcHelper helper = CtxListener.getTcHelper(ctx);

        final ICredentialsProv prov = ICredentialsProv.get(req);

        String project = projectId == null ? "IgniteTests24Java8" : projectId;

        String srvId = srv == null ? "apache" : srv;

        if (!prov.hasAccess(srvId))
            throw ServiceUnauthorizedException.noCreds(srvId);

        IAnalyticsEnabledTeamcity teamcity = helper.server(srvId, prov);

        FullQueryParams key = new FullQueryParams();

        key.setTestName(name);
        key.setProjectId(project);
        key.setServerId(srvId);
        key.setSuiteId(suiteName);

        CompletableFuture<TestRef> ref = teamcity.getTestRef(key);

        return ref.isDone() && !ref.isCompletedExceptionally() ? teamcity.host() + "project.html?"
            + "projectId=" + project
            + "&testNameId=" + ref.get().id
            + "&tab=testDetails" : null;
    }

    /**
     * Mark builds as "valid" or "invalid" for a specific user.
     *
     * @param buildId Build id.
     * @param isValid Is valid.
     * @param field Field.
     * @param serverId Server.
     */
    @GET
    @Path("condition")
    public Boolean setBuildCondition(
        @QueryParam("buildId") Integer buildId,
        @QueryParam("isValid") Boolean isValid,
        @QueryParam("field") String field,
        @QueryParam("serverId") String serverId) {
        String srvId = isNullOrEmpty(serverId) ? "apache" : serverId;

        if (buildId == null || isValid == null)
            return null;

        final ITcHelper tcHelper = CtxListener.getTcHelper(ctx);
        final ICredentialsProv prov = ICredentialsProv.get(req);

        if (!prov.hasAccess(srvId))
            throw ServiceUnauthorizedException.noCreds(srvId);

        IAnalyticsEnabledTeamcity teamcity = tcHelper.server(srvId, prov);

        BiMap<String, String> problemNames = BuildStatisticsSummary.fullProblemNames;

        BuildCondition buildCond =
            new BuildCondition(buildId, prov.getPrincipalId(), isValid, problemNames.getOrDefault(field, field));

        TeamcityIgnitedImpl ignited = CtxListener.getInjector(ctx).getInstance(TeamcityIgnitedImpl.class);

        ignited.init(srvId, teamcity);

        return ignited.setBuildCondition(buildCond);
    }

    @GET
    @Path("history")
    public BuildsHistory getBuildsHistory(
        @Nullable @QueryParam("server") String srvId,
        @Nullable @QueryParam("buildType") String buildType,
        @Nullable @QueryParam("branch") String branch,
        @Nullable @QueryParam("sinceDate") String sinceDate,
        @Nullable @QueryParam("untilDate") String untilDate,
        @Nullable @QueryParam("skipTests") String skipTests)  throws ParseException {
        BuildsHistory.Builder builder = new BuildsHistory.Builder()
            .branch(branch)
            .server(srvId)
            .buildType(buildType)
            .sinceDate(sinceDate)
            .untilDate(untilDate);

        if (Boolean.valueOf(skipTests))
            builder.skipTests();

        BuildsHistory buildsHistory = builder.build();

        buildsHistory.initialize(ICredentialsProv.get(req), ctx);

        return buildsHistory;
    }
}
