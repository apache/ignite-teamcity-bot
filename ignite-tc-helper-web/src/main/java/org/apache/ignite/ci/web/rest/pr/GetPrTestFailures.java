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
import javax.ws.rs.BadRequestException;
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
     * Starts explicit TeamCity build ref recheck for PR report pages.
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
        if (srvId == null || srvId.trim().isEmpty())
            throw new BadRequestException("serverId query parameter is required.");

        if (suiteId == null || suiteId.trim().isEmpty())
            throw new BadRequestException("suiteId query parameter is required.");

        if (branchForTc == null || branchForTc.trim().isEmpty())
            throw new BadRequestException("branchForTc query parameter is required.");

        final String serverId = srvId.trim();
        final String buildTypeId = suiteId.trim();
        final String branch = branchForTc.trim();

        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        ITcBotUserCreds creds = ITcBotUserCreds.get(req);
        ITeamcityIgnitedProvider tcProv = appCtx.getInstance(ITeamcityIgnitedProvider.class);
        BotProcessMonitor process = appCtx.getInstance(BotProcessMonitor.class);
        String taskName = "Pr.recheckBuildRef." + serverId + "." + buildTypeId + "." + branch;
        String pageCtx = pageContext(serverId, suiteId, branchForTc, action);

        process.start(processId, "teamcityBuildRefRecheck",
            "Admin TeamCity build ref recheck request accepted. " + pageCtx);

        try {
            process.status(processId, "Checking TeamCity credentials for server " + serverId + ".");
            tcProv.checkAccess(serverId, creds);
        }
        catch (RuntimeException e) {
            process.fail(processId, e);

            throw e;
        }

        boolean accepted = appCtx.getInstance(IScheduler.class).runNamedNow(taskName, () -> {
            try {
                process.status(processId, "Rechecking TeamCity build reference for server " + serverId +
                    ". Requested from: " + pageCtx);
                process.status(processId, "Calling direct TeamCity build ref lookup for suite " + buildTypeId +
                    " and branch " + branch + ".");
                String res = tcProv.server(serverId, creds).recheckBuildRef(buildTypeId, branch);
                process.status(processId, "TeamCity build ref recheck result for server " + serverId +
                    ": " + res);
                process.finish(processId, "TeamCity build reference rechecked for server " + serverId +
                    ". " + res + ". Refresh context was: " + pageCtx);
            }
            catch (RuntimeException e) {
                process.fail(processId, e);

                throw e;
            }
        }, processId);

        if (!accepted) {
            process.fail(processId, "TeamCity build ref recheck is already queued or running for " + pageCtx);

            throw new ClientErrorException("TeamCity build ref recheck is already queued or running for " + pageCtx,
                Response.Status.CONFLICT);
        }

        return new SimpleResult("TeamCity build ref recheck queued for server " + serverId + ".");
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
