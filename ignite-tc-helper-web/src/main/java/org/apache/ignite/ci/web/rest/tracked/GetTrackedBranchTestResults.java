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

package org.apache.ignite.ci.web.rest.tracked;

import com.google.inject.Injector;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.tcbot.chain.TrackedBranchChainsProcessor;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.model.current.UpdateInfo;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.apache.ignite.internal.util.typedef.F;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedImpl.DEFAULT_PROJECT_ID;

@Path(GetTrackedBranchTestResults.TRACKED)
@Produces(MediaType.APPLICATION_JSON)
public class GetTrackedBranchTestResults {
    public static final String TRACKED = "tracked";

    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path("updates")
    public UpdateInfo getTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull,
                                          @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return new UpdateInfo().copyFrom(getTestFailsResultsNoSync(branchOrNull, checkAllLogs));
    }

    @GET
    @Path("results/txt")
    @Produces(MediaType.TEXT_PLAIN)
    public String getTestFailsText(@Nullable @QueryParam("branch") String branchOrNull,
                                   @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return getTestFailsResultsNoSync(branchOrNull, checkAllLogs).toString();
    }

    @GET
    @Path("resultsNoSync")
    public TestFailuresSummary getTestFailsResultsNoSync(
            @Nullable @QueryParam("branch") String branch,
            @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return latestBuildResults(branch, checkAllLogs, SyncMode.NONE);
    }

    @GET
    @Path("results")
    @NotNull
    public TestFailuresSummary getTestFailsNoCache(
            @Nullable @QueryParam("branch") String branch,
            @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return latestBuildResults(branch, checkAllLogs, SyncMode.RELOAD_QUEUED);
    }

    @NotNull public TestFailuresSummary latestBuildResults(
        @QueryParam("branch") @Nullable String branch,
        @QueryParam("checkAllLogs") @Nullable Boolean checkAllLogs,
        SyncMode mode) {
        ITcBotUserCreds creds = ITcBotUserCreds.get(req);

        Injector injector = CtxListener.getInjector(ctx);

        return injector.getInstance(TrackedBranchChainsProcessor.class)
            .getTrackedBranchTestFailures(branch, checkAllLogs, 1, creds, mode);
    }

    @GET
    @Path("mergedUpdates")
    public UpdateInfo getAllTestFailsUpdates(@Nullable @QueryParam("branch") String branch,
        @Nullable @QueryParam("count") Integer cnt,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return new UpdateInfo().copyFrom(getAllTestFailsNoSync(branch, cnt, checkAllLogs));
    }

    @GET
    @Path("mergedResultsNoSync")
    public TestFailuresSummary getAllTestFailsNoSync(@Nullable @QueryParam("branch") String branch,
        @Nullable @QueryParam("count") Integer cnt,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return mergedBuildsResults(branch, cnt, checkAllLogs, SyncMode.NONE);
    }

    @GET
    @Path("mergedResults")
    @NotNull
    public TestFailuresSummary getAllTestFailsForMergedBuidls(@Nullable @QueryParam("branch") String branchOpt,
        @QueryParam("count") Integer cnt,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return mergedBuildsResults(branchOpt, cnt, checkAllLogs, SyncMode.RELOAD_QUEUED);
    }

    @NotNull public TestFailuresSummary mergedBuildsResults(
        @QueryParam("branch") @Nullable String branchOpt,
        @QueryParam("count") Integer cnt,
        @QueryParam("checkAllLogs") @Nullable Boolean checkAllLogs,
        SyncMode mode) {
        ITcBotUserCreds creds = ITcBotUserCreds.get(req);
        int cntLimit = cnt == null ? FullQueryParams.DEFAULT_COUNT : cnt;
        Injector injector = CtxListener.getInjector(ctx);

        return injector.getInstance(TrackedBranchChainsProcessor.class)
            .getTrackedBranchTestFailures(branchOpt, checkAllLogs, cntLimit, creds, mode);
    }

    /**
     * @param srvCode Server id.
     * @param projectId Project id.
     * @return Mutes for given server-project pair.
     */
    @GET
    @Path("mutes")
    public Set<MuteInfo> mutes(
        @Nullable @QueryParam("serverId") String srvCode,
        @Nullable @QueryParam("projectId") String projectId
    ) {
        ITcBotUserCreds creds = ITcBotUserCreds.get(req);

        Injector injector = CtxListener.getInjector(ctx);
        ITcBotConfig cfg = injector.getInstance(ITcBotConfig.class);

        if (F.isEmpty(srvCode))
            srvCode = cfg.primaryServerCode();

        if (F.isEmpty(projectId))
            projectId = DEFAULT_PROJECT_ID;

        injector.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, creds);

        return injector
            .getInstance(TcBotTriggerAndSignOffService.class)
            .getMutes(srvCode, projectId, creds);
    }
}
