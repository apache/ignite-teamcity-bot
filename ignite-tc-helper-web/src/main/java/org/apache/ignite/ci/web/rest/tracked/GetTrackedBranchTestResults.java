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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

import static org.apache.ignite.tcignited.TeamcityIgnitedImpl.DEFAULT_PROJECT_ID;

@Path(GetTrackedBranchTestResults.TRACKED)
@Produces(MediaType.APPLICATION_JSON)
public class GetTrackedBranchTestResults {
    public static final String TRACKED = "tracked";
    public static final int DEFAULT_COUNT = 10;

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

        Map<Integer, Integer> counters = CtxListener.getInjector(ctx).getInstance(IDetailedStatusForTrackedBranch.class)
            .getTrackedBranchUpdateCounters(branchOrNull, ITcBotUserCreds.get(req));
        info.initCounters(counters);

        return info;
    }

    @GET
    @Path("results/txt")
    @Produces(MediaType.TEXT_PLAIN)
    public String getTestFailsText(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs,
        @Nullable @QueryParam("trustedTests") Boolean trustedTests,
        @Nullable @QueryParam("tagSelected") String tagSelected,
        @Nullable @QueryParam("tagForHistSelected") String tagForHistSelected,
        @Nullable @QueryParam("displayMode") String displayMode,
        @Nullable @QueryParam("sortOption") String sortOption,
        @Nullable @QueryParam("count") Integer mergeCnt,
        @Nullable @QueryParam("showTestLongerThan") Integer showTestLongerThan,
        @Nullable @QueryParam("muted") Boolean showMuted,
        @Nullable @QueryParam("ignored") Boolean showIgnored) {
        return getTestFailsResultsNoSync(branchOrNull, checkAllLogs, trustedTests, tagSelected, tagForHistSelected,
            displayMode, sortOption, mergeCnt, showTestLongerThan, showMuted, showIgnored).toString();
    }

    @GET
    @Path("resultsNoSync")
    public DsSummaryUi getTestFailsResultsNoSync(
        @Nullable @QueryParam("branch") String branch,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs,
        @Nullable @QueryParam("trustedTests") Boolean trustedTests,
        @Nullable @QueryParam("tagSelected") String tagSelected,
        @Nullable @QueryParam("tagForHistSelected") String tagForHistSelected,
        @Nullable @QueryParam("displayMode") String displayMode,
        @Nullable @QueryParam("sortOption") String sortOption,
        @Nullable @QueryParam("count") Integer mergeCnt,
        @Nullable @QueryParam("showTestLongerThan") Integer showTestLongerThan,
        @Nullable @QueryParam("muted") Boolean showMuted,
        @Nullable @QueryParam("ignored") Boolean showIgnored) {
        return latestBuildResults(branch, checkAllLogs, trustedTests, tagSelected, tagForHistSelected,
            SyncMode.NONE, displayMode, sortOption, mergeCnt, showTestLongerThan, showMuted, showIgnored);
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
        @Nullable @QueryParam("displayMode") String displayMode,
        @Nullable @QueryParam("sortOption") String sortOption,
        @Nullable @QueryParam("count") Integer mergeCnt,
        @Nullable @QueryParam("showTestLongerThan") Integer showTestLongerThan,
        @Nullable @QueryParam("muted") Boolean showMuted,
        @Nullable @QueryParam("ignored") Boolean showIgnored) {
        return latestBuildResults(branch, checkAllLogs, trustedTests, tagSelected, tagForHistSelected,
            SyncMode.RELOAD_QUEUED, displayMode, sortOption, mergeCnt, showTestLongerThan, showMuted, showIgnored);
    }

    @NotNull private DsSummaryUi latestBuildResults(
        @Nullable String branch,
        @Nullable Boolean checkAllLogs,
        @Nullable Boolean trustedTests,
        @Nullable String tagSelected,
        @Nullable String tagForHistSelected,
        @Nonnull SyncMode mode,
        @Nullable String displayMode,
        @Nullable String sortOption,
        @Nullable Integer mergeCnt,
        @Nullable Integer showTestLongerThan,
        @Nullable Boolean showMuted,
        @Nullable Boolean showIgnored) {
        ITcBotUserCreds creds = ITcBotUserCreds.get(req);

        Injector injector = CtxListener.getInjector(ctx);

        int actualMergeBuilds = (mergeCnt == null || mergeCnt < 1) ? 1 : mergeCnt;

        int maxDurationSec = (showTestLongerThan == null || showTestLongerThan < 1) ? 0 : showTestLongerThan;

        return injector.getInstance(IDetailedStatusForTrackedBranch.class)
            .getTrackedBranchTestFailures(branch,
                checkAllLogs,
                actualMergeBuilds,
                creds,
                mode,
                Boolean.TRUE.equals(trustedTests),
                tagSelected,
                tagForHistSelected,
                DisplayMode.parseStringValue(displayMode),
                SortOption.parseStringValue(sortOption),
                maxDurationSec,
                Boolean.TRUE.equals(showMuted),
                Boolean.TRUE.equals(showIgnored));
    }

    @GET
    @Path("mergedUpdates")
    public UpdateInfo getAllTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull) {
        return new UpdateInfo().initCounters(
            CtxListener.getInjector(ctx)
                .getInstance(IDetailedStatusForTrackedBranch.class)
                .getTrackedBranchUpdateCounters(branchOrNull, ITcBotUserCreds.get(req)));
    }

    @GET
    @Path("mergedResultsNoSync")
    public DsSummaryUi getAllTestFailsNoSync(@Nullable @QueryParam("branch") String branch,
                                             @Nullable @QueryParam("count") Integer cnt,
                                             @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return mergedBuildsResults(branch, cnt, checkAllLogs, SyncMode.NONE);
    }

    @GET
    @Path("mergedResults")
    @NotNull
    public DsSummaryUi getAllTestFailsForMergedBuidls(@Nullable @QueryParam("branch") String branchOpt,
                                                      @QueryParam("count") Integer cnt,
                                                      @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return mergedBuildsResults(branchOpt, cnt, checkAllLogs, SyncMode.RELOAD_QUEUED);
    }

    @NotNull private DsSummaryUi mergedBuildsResults(
        @QueryParam("branch") @Nullable String branchOpt,
        @QueryParam("count") Integer cnt,
        @QueryParam("checkAllLogs") @Nullable Boolean checkAllLogs,
        SyncMode mode) {
        ITcBotUserCreds creds = ITcBotUserCreds.get(req);
        int cntLimit = cnt == null ? DEFAULT_COUNT : cnt;
        Injector injector = CtxListener.getInjector(ctx);

        return injector.getInstance(TrackedBranchChainsProcessor.class)
            .getTrackedBranchTestFailures(branchOpt, checkAllLogs, cntLimit, creds, mode,
                false, null, null, DisplayMode.OnlyFailures, null,
                -1, false, false);
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

    @GET
    @Path("summary")
    public List<GuardBranchStatusUi> getIdsIfAccessible() {
        ITcBotUserCreds prov = ITcBotUserCreds.get(req);
        Injector injector = CtxListener.getInjector(ctx);
        ITcBotConfig cfg = injector.getInstance(ITcBotConfig.class);
        IDetailedStatusForTrackedBranch status = injector.getInstance(IDetailedStatusForTrackedBranch.class);

        return cfg.getTrackedBranches().branchesStream()
            .map(bt -> status.getBranchSummary(bt.name(), prov)).filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
