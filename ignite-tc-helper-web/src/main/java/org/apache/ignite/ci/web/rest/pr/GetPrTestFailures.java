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

package org.apache.ignite.ci.web.rest.pr;

import com.google.inject.Injector;
import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.githubignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.githubservice.IGitHubConnection;
import org.apache.ignite.tcbot.engine.pr.PrChainsProcessor;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.engine.ui.UpdateInfo;
import org.apache.ignite.tcignited.SyncMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Path(GetPrTestFailures.PR)
@Produces(MediaType.APPLICATION_JSON)
public class GetPrTestFailures {
    public static final String PR = "pr";

    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path("updates")
    public UpdateInfo getPrFailuresUpdates(
        @Nullable @QueryParam("serverId") String srvCodeOrAlias,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc) {
        return new UpdateInfo().initCounters(
            CtxListener.getInjector(ctx).getInstance(PrChainsProcessor.class)
                .getPrUpdateCounters(srvCodeOrAlias, branchForTc, baseBranchForTc, ITcBotUserCreds.get(req)));
    }

    @GET
    @Path("resultsNoSync")
    public DsSummaryUi getPrFailuresResultsNoSync(
        @Nullable @QueryParam("serverId") String srvId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String act,
        @Nullable @QueryParam("count") Integer cnt,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        return getPrFailsWithSyncMode(srvId, suiteId, branchForTc, act, cnt, baseBranchForTc, checkAllLogs, SyncMode.NONE);
    }

    public DsSummaryUi getPrFailsWithSyncMode(
        @QueryParam("serverId") @Nullable String srvId,
        @QueryParam("suiteId") @Nonnull String suiteId,
        @QueryParam("branchForTc") @Nonnull String branchForTc,
        @QueryParam("action") @Nonnull String act,
        @QueryParam("count") @Nullable Integer cnt,
        @QueryParam("baseBranchForTc") @Nullable String baseBranchForTc,
        @QueryParam("checkAllLogs") @Nullable Boolean checkAllLogs,
        SyncMode mode) {
        final ITcBotUserCreds creds = ITcBotUserCreds.get(req);
        final Injector injector = CtxListener.getInjector(ctx);
        final PrChainsProcessor prChainsProcessor = injector.getInstance(PrChainsProcessor.class);

        return prChainsProcessor.getTestFailuresSummary(creds, srvId, suiteId, branchForTc, act, cnt, baseBranchForTc,
            checkAllLogs,
            mode);
    }

    /**
     * @param srvId Server id.
     * @param suiteId Suite id.
     * @param branchForTc Branch name in TC identification.
     * @param act Action.
     * @param cnt Count.
     * @param baseBranchForTc Base branch name in TC identification.
     */
    @GET
    @Path("results")
    @NotNull public DsSummaryUi getPrFailures (
        @Nullable @QueryParam("serverId") String srvId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String act,
        @Nullable @QueryParam("count") Integer cnt,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        return getPrFailsWithSyncMode(srvId, suiteId, branchForTc, act, cnt, baseBranchForTc, checkAllLogs, SyncMode.RELOAD_QUEUED);
    }

    @POST
    @Path("notifyGit")
    public String getNotifyGit(
        @Nullable @QueryParam("serverId") String srvId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String act,
        @Nullable @QueryParam("count") Integer cnt,
        @Nonnull @FormParam("notifyMsg") String msg) {
        if (!branchForTc.startsWith("pull/"))
            return "Given branch is not a pull request. Notify works only for pull requests.";

        final Injector injector = CtxListener.getInjector(ctx);
        final IGitHubConnIgnited srv = injector.getInstance(IGitHubConnIgnitedProvider.class).server(srvId);

        PullRequest pr;

        try {
            Integer prId = IGitHubConnection.convertBranchToPrId(branchForTc);

            if (prId == null)
                return "Invalid TC branch name: [" + branchForTc + "]";

            pr = srv.getPullRequest(prId);
        }
        catch (RuntimeException e) {
            return "Exception happened - " + e.getMessage();
        }

        String statusesUrl = pr.getStatusesUrl();

        srv.notifyGit(statusesUrl, msg);

        return "Git was notified.";
    }
}
