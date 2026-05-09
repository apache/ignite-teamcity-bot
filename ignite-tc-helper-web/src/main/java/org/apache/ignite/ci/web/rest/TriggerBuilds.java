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

import org.apache.ignite.tcbot.common.application.TcBotApplicationContext;
import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.ignite.ci.tcbot.trigger.TriggerResult;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.tcbot.engine.pool.TcUpdatePool;
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
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
     * @param commentTargets Comment targets.
     * @param ticketId JIRA ticket id.
     * @param prNum Pull request number in appropriate project (@code srvCodeOrAlias).
     * @param baseBranchForTc Base branch for possible blockers comparison (e.g. master, 8.8-master)
     * @param commentOnlyIfNoBlockers Comment only if analysis has no blockers.
     * @return Result of triggering suites re-run.
     */
    @GET
    @Path("trigger")
    @Deprecated
    public TriggerResult triggerBuilds(
        @Nullable @QueryParam("srvCode") String srvCodeOrAlias,
        @Nullable @QueryParam("branchName") String branchForTc,
        @Nonnull @QueryParam("parentSuiteId") String parentSuiteId,
        @Nonnull @QueryParam("suiteIdList") String suiteIdList,
        @Nullable @QueryParam("top") Boolean top,
        @Nullable @QueryParam("observe") Boolean observe,
        @Nullable @QueryParam("comment") String commentTargets,
        @Nullable @QueryParam("ticketId") String ticketId,
        @Nullable @QueryParam("prNum") String prNum,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nullable @QueryParam("commentOnlyIfNoBlockers") Boolean commentOnlyIfNoBlockers,
        @Nonnull @QueryParam("cleanRebuild") Boolean cleanRebuild
    ) {
        TcBotApplicationContext appCtx = appCtx();
        ITcBotUserCreds prov = creds();

        if (isNullOrEmpty(suiteIdList))
            return new TriggerResult("Error: nothing to run.");

        checkAccess(appCtx, srvCodeOrAlias, prov);

        String jiraRes = triggerBuilds(appCtx, prov, srvCodeOrAlias, branchForTc, parentSuiteId, suiteIdList, top,
            observe, commentTargets, ticketId, prNum, baseBranchForTc, commentOnlyIfNoBlockers, cleanRebuild, null);

        return new TriggerResult("Tests started." + (!jiraRes.isEmpty() ? "<br>" + jiraRes : ""));
    }

    /**
     * Starts re-running possible blocker suites in background.
     *
     * @param srvCodeOrAlias Server code or alias (e.g. Apache Ignite, GridGain, GGPrivate).
     * @param branchForTc Branch name for TeamCity triggering.
     * @param parentSuiteId Parent suite id for suite need to be re-run.
     * @param suiteIdList Suite ids need to be re-run (possible blockers).
     * @param top If {@code true} re-running suites will be placed at the top of TC queue.
     * @param observe If {@code true} JIRA will be commented with current state of possible blockers.
     * @param commentTargets Comment targets.
     * @param ticketId JIRA ticket id.
     * @param prNum Pull request number in appropriate project (@code srvCodeOrAlias).
     * @param baseBranchForTc Base branch for possible blockers comparison (e.g. master, 8.8-master)
     * @param commentOnlyIfNoBlockers Comment only if analysis has no blockers.
     */
    @GET
    @Path("triggerBuildsAsync")
    public SimpleResult triggerBuildsAsync(
        @Nullable @QueryParam("srvCode") String srvCodeOrAlias,
        @Nullable @QueryParam("branchName") String branchForTc,
        @Nonnull @QueryParam("parentSuiteId") String parentSuiteId,
        @Nonnull @QueryParam("suiteIdList") String suiteIdList,
        @Nullable @QueryParam("top") Boolean top,
        @Nullable @QueryParam("observe") Boolean observe,
        @Nullable @QueryParam("comment") String commentTargets,
        @Nullable @QueryParam("ticketId") String ticketId,
        @Nullable @QueryParam("prNum") String prNum,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nullable @QueryParam("commentOnlyIfNoBlockers") Boolean commentOnlyIfNoBlockers,
        @Nonnull @QueryParam("cleanRebuild") Boolean cleanRebuild,
        @Nullable @QueryParam("processId") Long processId
    ) {
        ITcBotUserCreds prov = creds();
        TcBotApplicationContext appCtx = appCtx();
        BotProcessMonitor process = appCtx.getInstance(BotProcessMonitor.class);

        process.start(processId, "triggerBuilds", "Trigger request accepted by the bot REST API.");

        try {
            if (isNullOrEmpty(suiteIdList)) {
                process.fail(processId, "nothing to run");

                return new SimpleResult("Error: nothing to run.");
            }

            checkAccess(appCtx, srvCodeOrAlias, prov);
        }
        catch (RuntimeException e) {
            process.fail(processId, e);

            throw e;
        }

        appCtx.getInstance(TcUpdatePool.class).getService().submit(() -> {
            try {
                String jiraRes = triggerBuilds(appCtx, prov, srvCodeOrAlias, branchForTc, parentSuiteId, suiteIdList,
                    top, observe, commentTargets, ticketId, prNum, baseBranchForTc, commentOnlyIfNoBlockers,
                    cleanRebuild, processId);

                process.finish(processId, "Tests started." + (!jiraRes.isEmpty() ? " " + jiraRes : ""));
            }
            catch (RuntimeException e) {
                process.fail(processId, e);
            }
        });

        return new SimpleResult("Trigger process started.");
    }

    /**
     * @param srvCode Server id.
     * @param branchForTc Branch for tc.
     * @param suiteId Suite id.
     * @param ticketId Ticket full name with IGNITE- prefix.
     * @param commentTargets Comment targets.
     */
    @GET
    @Path("commentJira")
    @Deprecated
    public SimpleResult commentJira(
        @Nullable @QueryParam("serverId") String srvCode,
        @Nullable @QueryParam("branchName") String branchForTc,
        @Nullable @QueryParam("suiteId") String suiteId,
        @Nullable @QueryParam("ticketId") String ticketId,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nullable @QueryParam("comment") String commentTargets,
        @Nullable @QueryParam("prNum") String prNum,
        @Nullable @QueryParam("commentOnlyIfNoBlockers") Boolean commentOnlyIfNoBlockers
    ) {
        ITcBotUserCreds prov = creds();
        TcBotApplicationContext appCtx = appCtx();

        checkAccess(appCtx, srvCode, prov);

        return commentBuildAnalysis(appCtx, prov, srvCode, branchForTc, suiteId, ticketId, baseBranchForTc,
            commentTargets, prNum, commentOnlyIfNoBlockers, null);
    }

    /**
     * Starts build analysis commenting in background.
     *
     * @param srvCode Server id.
     * @param branchForTc Branch for tc.
     * @param suiteId Suite id.
     * @param ticketId Ticket full name with IGNITE- prefix.
     * @param commentTargets Comment targets.
     */
    @GET
    @Path("commentBuildAnalysis")
    public SimpleResult commentBuildAnalysis(
        @Nullable @QueryParam("serverId") String srvCode,
        @Nullable @QueryParam("branchName") String branchForTc,
        @Nullable @QueryParam("suiteId") String suiteId,
        @Nullable @QueryParam("ticketId") String ticketId,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nullable @QueryParam("comment") String commentTargets,
        @Nullable @QueryParam("prNum") String prNum,
        @Nullable @QueryParam("commentOnlyIfNoBlockers") Boolean commentOnlyIfNoBlockers,
        @Nullable @QueryParam("processId") Long processId
    ) {
        ITcBotUserCreds prov = creds();
        TcBotApplicationContext appCtx = appCtx();

        BotProcessMonitor process = appCtx.getInstance(BotProcessMonitor.class);

        process.start(processId, "commentBuildAnalysis", "Comment request accepted by the bot REST API.");

        try {
            checkAccess(appCtx, srvCode, prov);
        }
        catch (RuntimeException e) {
            process.fail(processId, e);

            throw e;
        }

        appCtx.getInstance(TcUpdatePool.class).getService().submit(() -> {
            try {
                SimpleResult res = commentBuildAnalysis(appCtx, prov, srvCode, branchForTc, suiteId, ticketId,
                    baseBranchForTc, commentTargets, prNum, commentOnlyIfNoBlockers, processId);

                process.finish(processId, res.result);
            }
            catch (RuntimeException e) {
                process.fail(processId, e);
            }
        });

        return new SimpleResult("Comment process started.");
    }

    /** */
    private TcBotApplicationContext appCtx() {
        return CtxListener.getApplicationContext(ctx);
    }

    /** */
    private ITcBotUserCreds creds() {
        return ITcBotUserCreds.get(req);
    }

    /** */
    private void checkAccess(TcBotApplicationContext appCtx, @Nullable String srvCodeOrAlias, ITcBotUserCreds prov) {
        appCtx.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCodeOrAlias, prov);
    }

    /** */
    private String triggerBuilds(
        TcBotApplicationContext appCtx,
        ITcBotUserCreds prov,
        @Nullable String srvCodeOrAlias,
        @Nullable String branchForTc,
        @Nonnull String parentSuiteId,
        @Nonnull String suiteIdList,
        @Nullable Boolean top,
        @Nullable Boolean observe,
        @Nullable String commentTargets,
        @Nullable String ticketId,
        @Nullable String prNum,
        @Nullable String baseBranchForTc,
        @Nullable Boolean commentOnlyIfNoBlockers,
        @Nonnull Boolean cleanRebuild,
        @Nullable Long processId
    ) {
        TcBotTriggerAndSignOffService service = appCtx.getInstance(TcBotTriggerAndSignOffService.class);

        if (processId == null)
            return service.triggerBuildsAndObserve(srvCodeOrAlias, branchForTc, parentSuiteId, suiteIdList, top,
                observe, ticketId, prNum, baseBranchForTc, cleanRebuild, commentTargets, commentOnlyIfNoBlockers, prov);

        return service.triggerBuildsAndObserve(srvCodeOrAlias, branchForTc, parentSuiteId, suiteIdList, top, observe,
            ticketId, prNum, baseBranchForTc, cleanRebuild, commentTargets, commentOnlyIfNoBlockers, prov, processId);
    }

    /** */
    private SimpleResult commentBuildAnalysis(
        TcBotApplicationContext appCtx,
        ITcBotUserCreds prov,
        @Nullable String srvCode,
        @Nullable String branchForTc,
        @Nullable String suiteId,
        @Nullable String ticketId,
        @Nullable String baseBranchForTc,
        @Nullable String commentTargets,
        @Nullable String prNum,
        @Nullable Boolean commentOnlyIfNoBlockers,
        @Nullable Long processId
    ) {
        TcBotTriggerAndSignOffService service = appCtx.getInstance(TcBotTriggerAndSignOffService.class);
        boolean onlyIfNoBlockers = commentOnlyIfNoBlockers != null && commentOnlyIfNoBlockers;

        if (processId == null)
            return service.commentJiraEx(srvCode, branchForTc, suiteId, ticketId, baseBranchForTc, prov, commentTargets,
                prNum, onlyIfNoBlockers);

        return service.commentJiraEx(srvCode, branchForTc, suiteId, ticketId, baseBranchForTc, prov, commentTargets,
            prNum, onlyIfNoBlockers, processId);
    }
}
