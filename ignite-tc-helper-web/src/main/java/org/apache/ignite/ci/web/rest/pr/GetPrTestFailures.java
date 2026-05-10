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

import org.apache.ignite.tcbot.common.application.TcBotApplicationContext;
import javax.annotation.Nonnull;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.auth.AuthenticationFilter;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.tcbot.engine.build.TestFailuresAiPromptBuilder;
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
import org.apache.ignite.tcbot.engine.pr.PrChainsProcessor;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.engine.ui.UpdateInfo;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
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
            CtxListener.getApplicationContext(ctx).getInstance(PrChainsProcessor.class)
                .getPrUpdateCounters(srvCodeOrAlias, branchForTc, baseBranchForTc, ITcBotUserCreds.get(req)));
    }

    /**
     * Starts explicit TeamCity build refs actualization for PR report pages.
     *
     * @param srvId Server id.
     * @param processId User-visible process id.
     */
    @POST
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("actualizeBuildRefs")
    public SimpleResult actualizeBuildRefs(@Nullable @QueryParam("serverId") String srvId,
        @Nullable @QueryParam("suiteId") String suiteId,
        @Nullable @QueryParam("branchForTc") String branchForTc,
        @Nullable @QueryParam("action") String action,
        @Nullable @QueryParam("processId") Long processId) {
        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        ITcBotUserCreds creds = ITcBotUserCreds.get(req);
        ITeamcityIgnitedProvider tcProv = appCtx.getInstance(ITeamcityIgnitedProvider.class);
        BotProcessMonitor process = appCtx.getInstance(BotProcessMonitor.class);
        String taskName = "Pr.actualizeBuildRefs." + String.valueOf(srvId);
        String pageCtx = pageContext(srvId, suiteId, branchForTc, action);

        process.start(processId, "teamcityBuildRefsRefresh",
            "Admin TeamCity build refs refresh request accepted. " + pageCtx);

        try {
            process.status(processId, "Checking TeamCity credentials for server " + srvId + ".");
            tcProv.checkAccess(srvId, creds);
        }
        catch (RuntimeException e) {
            process.fail(processId, e);

            throw e;
        }

        boolean accepted = appCtx.getInstance(IScheduler.class).runNamedNow(taskName, () -> {
            try {
                process.status(processId, "Refreshing recent TeamCity build references for server " + srvId +
                    ". Requested from: " + pageCtx);
                process.status(processId, "Calling TeamCity recent build refs actualization. This updates the bot " +
                    "server-wide recent refs cache used by PR reports; page branch context is " +
                    valueOrAny(branchForTc) + ".");
                String res = tcProv.server(srvId, creds).actualizeRecentBuildRefs();
                process.status(processId, "TeamCity build refs actualization result for server " + srvId +
                    ": " + res);
                process.finish(processId, "TeamCity build references refreshed for server " + srvId +
                    ". " + res + ". Refresh context was: " + pageCtx);
            }
            catch (RuntimeException e) {
                process.fail(processId, e);

                throw e;
            }
        }, processId);

        if (!accepted) {
            process.fail(processId, "TeamCity build refs refresh is already queued or running for server " + srvId + ".");

            throw new ClientErrorException("TeamCity build refs refresh is already queued or running for server "
                + srvId, Response.Status.CONFLICT);
        }

        return new SimpleResult("TeamCity build refs refresh queued for server " + srvId + ".");
    }

    /**
     * @param srvId Server id.
     * @param suiteId Suite id.
     * @param branchForTc TeamCity branch.
     * @param action PR report action.
     */
    private static String pageContext(@Nullable String srvId, @Nullable String suiteId,
        @Nullable String branchForTc, @Nullable String action) {
        return "Page context: server=" + valueOrAny(srvId) + ", suite=" + valueOrAny(suiteId) +
            ", branch=" + valueOrAny(branchForTc) + ", action=" + valueOrAny(action) + ".";
    }

    /**
     * @param val Value.
     */
    private static String valueOrAny(@Nullable String val) {
        return val == null || val.trim().isEmpty() ? "<any>" : val;
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
        final TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        final PrChainsProcessor prChainsProcessor = appCtx.getInstance(PrChainsProcessor.class);

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

    /**
     * @param srvId Server id.
     * @param suiteId Suite id.
     * @param branchForTc Branch name in TC identification.
     * @param act Action.
     * @param cnt Count.
     * @param baseBranchForTc Base branch name in TC identification.
     * @param maxDetailsChars Max chars per TeamCity failure details block. Non-positive means default cap.
     * @param testName Optional full test name filter.
     * @param promptSuiteId Optional suite id filter.
     * @param waitForTc Wait for fresh TeamCity context and build log processing.
     */
    @GET
    @Path("results/aiPrompt")
    @Produces(MediaType.TEXT_PLAIN)
    @NotNull public String getPrFailuresAiPrompt(
        @Nullable @QueryParam("serverId") String srvId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String act,
        @Nullable @QueryParam("count") Integer cnt,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nullable @QueryParam("maxDetailsChars") Integer maxDetailsChars,
        @Nullable @QueryParam("testName") String testName,
        @Nullable @QueryParam("promptSuiteId") String promptSuiteId,
        @Nullable @QueryParam("waitForTc") Boolean waitForTc,
        @Nullable @QueryParam("processId") Long processId) {
        final TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);

        return appCtx.getInstance(PrChainsProcessor.class).getPrFailuresAiPrompt(
            ITcBotUserCreds.get(req),
            srvId,
            suiteId,
            branchForTc,
            act,
            cnt,
            baseBranchForTc,
            TestFailuresAiPromptBuilder.restMaxDetailsChars(maxDetailsChars),
            testName,
            promptSuiteId,
            waitForTc == null || waitForTc,
            processId);
    }
}
