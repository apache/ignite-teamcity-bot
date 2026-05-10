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

import org.apache.ignite.tcbot.common.application.TcBotApplicationContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.tcbot.engine.chain.SortOption;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.tracked.DisplayMode;
import org.apache.ignite.tcbot.engine.tracked.IDetailedStatusForTrackedBranch;
import org.apache.ignite.tcbot.engine.tracked.TrackedBranchChainsProcessor;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.engine.ui.GuardBranchStatusUi;
import org.apache.ignite.tcbot.engine.ui.UpdateInfo;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcservice.model.mute.MuteInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.tcignited.TeamcityIgnitedImpl.DEFAULT_PROJECT_ID;

@Path(GetTrackedBranchTestResults.TRACKED)
@Produces(MediaType.APPLICATION_JSON)
public class GetTrackedBranchTestResults {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(GetTrackedBranchTestResults.class);

    public static final String TRACKED = "tracked";

    /** Slow tracked branch request threshold. */
    private static final long SLOW_TRACKED_RESULTS_WARN_MS =
        Long.getLong("tcbot.tracked.slowResultsWarnMs", 1000L);

    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path("updates")
    public UpdateInfo getTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull) {
        UpdateInfo info = new UpdateInfo();

        Map<Integer, Integer> counters = CtxListener.getApplicationContext(ctx).getInstance(IDetailedStatusForTrackedBranch.class)
            .getTrackedBranchUpdateCounters(branchOrNull, ITcBotUserCreds.get(req));
        info.initCounters(counters);

        return info;
    }

    @GET
    @Path("results/aiPrompt")
    @Produces(MediaType.TEXT_PLAIN)
    public String getTestFailsAiPrompt(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("tagForHistSelected") String tagForHistSelected,
        @Nullable @QueryParam("sortOption") String sortOption,
        @Nullable @QueryParam("count") Integer mergeCnt,
        @Nullable @QueryParam("maxDetailsChars") Integer maxDetailsChars,
        @Nullable @QueryParam("testName") String testName,
        @Nullable @QueryParam("promptSuiteId") String promptSuiteId,
        @Nullable @QueryParam("waitForTc") Boolean waitForTc,
        @Nullable @QueryParam("processId") Long processId) {
        int actualMergeBuilds = (mergeCnt == null || mergeCnt < 1) ? 1 : mergeCnt;

        return CtxListener.getApplicationContext(ctx)
            .getInstance(TrackedBranchChainsProcessor.class)
            .getTrackedBranchFailuresAiPrompt(branchOrNull,
                actualMergeBuilds,
                ITcBotUserCreds.get(req),
                SyncMode.RELOAD_QUEUED,
                tagForHistSelected,
                SortOption.parseStringValue(sortOption),
                maxDetailsChars,
                testName,
                promptSuiteId,
                waitForTc == null || waitForTc,
                processId);
    }

    @GET
    @Path("resultsNoSync")
    public DsSummaryUi getTestFailsResultsNoSync(
        @Nullable @QueryParam("branch") String branch,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs,
        @Nullable @QueryParam("trustedTests") Boolean trustedTests,
        @Nullable @QueryParam("tagSelected") String tagSelected,
        @Nullable @QueryParam("tagForHistSelected") String tagForHistSelected,
        @Nullable @QueryParam("suiteId") String suiteId,
        @Nullable @QueryParam("displayMode") String displayMode,
        @Nullable @QueryParam("sortOption") String sortOption,
        @Nullable @QueryParam("count") Integer mergeCnt,
        @Nullable @QueryParam("showTestLongerThan") Integer showTestLongerThan,
        @Nullable @QueryParam("muted") Boolean showMuted,
        @Nullable @QueryParam("ignored") Boolean showIgnored) {
        return latestBuildResults(branch, checkAllLogs, trustedTests, tagSelected, tagForHistSelected,
            suiteId, SyncMode.NONE, displayMode, sortOption, mergeCnt, showTestLongerThan, showMuted, showIgnored);
    }

    @GET
    @Path("results")
    @NotNull
    public DsSummaryUi getTestFailsNoCache(
        @Nullable @QueryParam("branch") String branch,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs,
        @Nullable @QueryParam("trustedTests") Boolean trustedTests,
        @Nullable @QueryParam("tagSelected") String tagSelected,
        @Nullable @QueryParam("tagForHistSelected") String tagForHistSelected,
        @Nullable @QueryParam("suiteId") String suiteId,
        @Nullable @QueryParam("displayMode") String displayMode,
        @Nullable @QueryParam("sortOption") String sortOption,
        @Nullable @QueryParam("count") Integer mergeCnt,
        @Nullable @QueryParam("showTestLongerThan") Integer showTestLongerThan,
        @Nullable @QueryParam("muted") Boolean showMuted,
        @Nullable @QueryParam("ignored") Boolean showIgnored) {
        return latestBuildResults(branch, checkAllLogs, trustedTests, tagSelected, tagForHistSelected,
            suiteId, SyncMode.RELOAD_QUEUED, displayMode, sortOption, mergeCnt, showTestLongerThan, showMuted,
            showIgnored);
    }

    @NotNull private DsSummaryUi latestBuildResults(
        @Nullable String branch,
        @Nullable Boolean checkAllLogs,
        @Nullable Boolean trustedTests,
        @Nullable String tagSelected,
        @Nullable String tagForHistSelected,
        @Nullable String suiteId,
        @Nonnull SyncMode mode,
        @Nullable String displayMode,
        @Nullable String sortOption,
        @Nullable Integer mergeCnt,
        @Nullable Integer showTestLongerThan,
        @Nullable Boolean showMuted,
        @Nullable Boolean showIgnored) {
        long startNanos = System.nanoTime();

        ITcBotUserCreds creds = ITcBotUserCreds.get(req);

        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);

        int actualMergeBuilds = (mergeCnt == null || mergeCnt < 1) ? 1 : mergeCnt;

        int maxDurationSec = (showTestLongerThan == null || showTestLongerThan < 1) ? 0 : showTestLongerThan;

        DsSummaryUi res = appCtx.getInstance(IDetailedStatusForTrackedBranch.class)
            .getTrackedBranchTestFailures(branch,
                checkAllLogs,
                actualMergeBuilds,
                creds,
                mode,
                Boolean.TRUE.equals(trustedTests),
                tagSelected,
                tagForHistSelected,
                suiteId,
                DisplayMode.parseStringValue(displayMode),
                SortOption.parseStringValue(sortOption),
                maxDurationSec,
                Boolean.TRUE.equals(showMuted),
                Boolean.TRUE.equals(showIgnored));

        logSlowTrackedResult(startNanos, "latest", branch, actualMergeBuilds, mode, res);

        return res;
    }

    /**
     * Logs slow tracked branch request.
     */
    private void logSlowTrackedResult(long startNanos, String op, @Nullable String branch, int cnt, SyncMode mode,
        DsSummaryUi res) {
        long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        if (totalMs >= SLOW_TRACKED_RESULTS_WARN_MS) {
            logger.warn("Slow tracked branch result: op={}, branch={}, count={}, mode={}, totalMs={}, chains={}",
                op, branch, cnt, mode, totalMs, res == null ? 0 : res.servers.size());
        }
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

        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        ITcBotConfig cfg = appCtx.getInstance(ITcBotConfig.class);

        if (F.isEmpty(srvCode))
            srvCode = cfg.primaryServerCode();

        if (F.isEmpty(projectId))
            projectId = DEFAULT_PROJECT_ID;

        appCtx.getInstance(ITeamcityIgnitedProvider.class).checkAccess(srvCode, creds);

        return appCtx
            .getInstance(TcBotTriggerAndSignOffService.class)
            .getMutes(srvCode, projectId, creds);
    }

    @GET
    @Path("summary")
    public List<GuardBranchStatusUi> getIdsIfAccessible() {
        ITcBotUserCreds prov = ITcBotUserCreds.get(req);
        TcBotApplicationContext appCtx = CtxListener.getApplicationContext(ctx);
        ITcBotConfig cfg = appCtx.getInstance(ITcBotConfig.class);
        IDetailedStatusForTrackedBranch status = appCtx.getInstance(IDetailedStatusForTrackedBranch.class);

        return cfg.getTrackedBranches().branchesStream()
            .map(bt -> status.getBranchSummary(bt.name(), prov)).filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
