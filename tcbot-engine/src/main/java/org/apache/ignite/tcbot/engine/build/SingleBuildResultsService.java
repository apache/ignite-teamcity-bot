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

package org.apache.ignite.tcbot.engine.build;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.chain.FullChainRunCtx;
import org.apache.ignite.tcbot.engine.chain.LatestRebuildMode;
import org.apache.ignite.tcbot.engine.chain.ProcessLogsMode;
import org.apache.ignite.tcbot.engine.pool.TcUpdatePool;
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.build.UpdateCountersStorage;
import org.apache.ignite.tcignited.buildref.BranchEquivalence;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.apache.ignite.tcservice.ITeamcity;

/**
 * Displays single build at server by ID.
 */
public class SingleBuildResultsService {
    /** Max time to wait for fresh AI prompt build context. */
    private static final long AI_PROMPT_CONTEXT_WAIT_MS = TimeUnit.MINUTES.toMillis(1);

    /** Max time to wait for AI prompt build log processing. */
    private static final long AI_PROMPT_LOG_WAIT_MS = TimeUnit.MINUTES.toMillis(1);

    @Inject BuildChainProcessor buildChainProcessor;
    @Inject ITeamcityIgnitedProvider tcIgnitedProv;
    @Inject BranchEquivalence branchEquivalence;
    @Inject IStringCompactor compactor;
    @Inject UpdateCountersStorage updateCounters;
    @Inject AiPromptRequestMonitor aiPromptMonitor;
    @Inject TcUpdatePool tcUpdatePool;
    @Inject BotProcessMonitor processMonitor;

    @Nonnull public DsSummaryUi getSingleBuildResults(String srvCodeOrAlias, Integer buildId,
        @Nullable Boolean checkAllLogs, SyncMode syncMode, ICredentialsProv prov) {
        DsSummaryUi res = new DsSummaryUi();

        FullChainRunCtx ctx = loadSingleBuildContext(srvCodeOrAlias, buildId, checkAllLogs, syncMode, prov);

        ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, prov);

        String failRateBranch = ITeamcity.DEFAULT;

        DsChainUi chainStatus = new DsChainUi(srvCodeOrAlias, tcIgnited.serverCode(), ctx.branchName());

        chainStatus.initFromContext(tcIgnited, ctx, failRateBranch, compactor, false, null, null, -1, null, false, false);

        res.addChainOnServer(chainStatus);

        res.initCounters(getBranchCntrs(srvCodeOrAlias, buildId, prov));
        return res;
    }

    /**
     * @param srvCodeOrAlias Server id or alias.
     * @param buildId Build id.
     * @param maxDetailsChars Max chars to include for every test details block. Non-positive means default cap.
     * @param syncMode Synchronization mode.
     * @param prov Credentials provider.
     */
    @Nonnull public String getSingleBuildFailuresAiPrompt(String srvCodeOrAlias, Integer buildId,
        @Nullable Integer maxDetailsChars, SyncMode syncMode, ICredentialsProv prov) {
        return getSingleBuildFailuresAiPrompt(srvCodeOrAlias, buildId, maxDetailsChars, syncMode, prov, null);
    }

    /**
     * Starts background refresh of TeamCity context required by AI prompt generation.
     *
     * @param processId User-visible process id.
     * @return User-visible acceptance message.
     */
    @Nonnull public String startSingleBuildFailuresAiPromptRefresh(String srvCodeOrAlias, Integer buildId,
        SyncMode syncMode, ICredentialsProv prov, @Nullable Long processId) {
        long reqId = aiPromptMonitor.start("singleBuild", null, srvCodeOrAlias, String.valueOf(buildId), null);

        processMonitor.start(processId, "aiPrompt", "Queued AI prompt TeamCity refresh.");

        tcUpdatePool.getService().submit(() -> {
            try {
                promptStatus(processId, reqId, "Refreshing TeamCity data and build logs for the prompt.");

                FullChainRunCtx ctx = loadSingleBuildContext(srvCodeOrAlias, buildId, null, syncMode, prov,
                    ProcessLogsMode.ALL);

                waitForAiPromptLogs(reqId, ctx, processId);

                aiPromptMonitor.finish(reqId, "background refresh finished");
                processMonitor.finish(processId, "Fresh TeamCity context is ready.");
            }
            catch (RuntimeException e) {
                aiPromptMonitor.fail(reqId, e);
                processMonitor.fail(processId, e);

                throw e;
            }
        });

        return "Fresh TeamCity context refresh started in background.";
    }

    /**
     * @param processId User-visible process id.
     */
    @Nonnull public String getSingleBuildFailuresAiPrompt(String srvCodeOrAlias, Integer buildId,
        @Nullable Integer maxDetailsChars, SyncMode syncMode, ICredentialsProv prov, @Nullable Long processId) {
        long reqId = aiPromptMonitor.start("singleBuild", null, srvCodeOrAlias, String.valueOf(buildId), null);
        processMonitor.start(processId, "aiPrompt", "Preparing AI prompt generation.");

        try {
            promptStatus(processId, reqId, "Collecting build and test details for the prompt.");

            FullChainRunCtx ctx = loadSingleBuildContextBestEffort(reqId, srvCodeOrAlias, buildId, syncMode, prov,
                processId);

            ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, prov);

            int maxDetails = TestFailuresAiPromptBuilder.restMaxDetailsChars(maxDetailsChars);

            promptStatus(processId, reqId, "Assembling the final prompt text.");

            String res = new TestFailuresAiPromptBuilder(compactor)
                .buildPrompt(tcIgnited, ctx, ITeamcity.DEFAULT, maxDetails);

            aiPromptMonitor.finish(reqId, "chars=" + res.length());
            processMonitor.finish(processId, "Prompt text is ready.");

            return res;
        }
        catch (RuntimeException e) {
            aiPromptMonitor.fail(reqId, e);
            processMonitor.fail(processId, e);

            throw e;
        }
    }

    /**
     * @param srvCodeOrAlias Server id or alias.
     * @param buildId Build id.
     * @param checkAllLogs Check all logs.
     * @param syncMode Synchronization mode.
     * @param prov Credentials provider.
     */
    private FullChainRunCtx loadSingleBuildContext(String srvCodeOrAlias, Integer buildId,
        @Nullable Boolean checkAllLogs, SyncMode syncMode, ICredentialsProv prov) {
        tcIgnitedProv.checkAccess(srvCodeOrAlias, prov);

        ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, prov);

        String failRateBranch = ITeamcity.DEFAULT;

        ProcessLogsMode procLogs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE;

        FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(
            tcIgnited,
            Collections.singletonList(buildId),
            LatestRebuildMode.NONE,
            procLogs,
            false,
            failRateBranch,
            syncMode,
            null,
            null);

        return ctx;
    }

    /**
     * @param reqId Monitor request id.
     * @param srvCodeOrAlias Server id or alias.
     * @param buildId Build id.
     * @param liveSyncMode Live sync mode.
     * @param prov Credentials provider.
     */
    private FullChainRunCtx loadSingleBuildContextBestEffort(long reqId, String srvCodeOrAlias, Integer buildId,
        SyncMode liveSyncMode, ICredentialsProv prov, @Nullable Long processId) {
        if (liveSyncMode == SyncMode.NONE) {
            promptStatus(processId, reqId, "Using cached build and test details for the prompt.");

            return loadSingleBuildContext(srvCodeOrAlias, buildId, null, SyncMode.NONE, prov,
                ProcessLogsMode.CACHED_ONLY);
        }

        Future<FullChainRunCtx> live = null;

        try {
            promptStatus(processId, reqId, "Requesting fresh TeamCity data for the prompt.");

            live = tcUpdatePool.getService().submit(() ->
                loadSingleBuildContext(srvCodeOrAlias, buildId, null, liveSyncMode, prov, ProcessLogsMode.ALL));

            return live.get(AI_PROMPT_CONTEXT_WAIT_MS, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e) {
            if (live != null)
                live.cancel(true);

            promptStatus(processId, reqId, "TeamCity refresh timed out; using cached build context.");
        }
        catch (InterruptedException e) {
            if (live != null)
                live.cancel(true);

            Thread.currentThread().interrupt();

            promptStatus(processId, reqId, "TeamCity refresh was interrupted.");

            throw new IllegalStateException("Interrupted while loading fresh TeamCity context", e);
        }
        catch (Exception e) {
            promptStatus(processId, reqId, "TeamCity refresh failed; using cached build context.");
        }

        return loadSingleBuildContext(srvCodeOrAlias, buildId, null, SyncMode.NONE, prov, ProcessLogsMode.CACHED_ONLY);
    }

    /**
     * @param processId User-visible process id.
     * @param reqId AI prompt monitor request id.
     * @param stage Shared process stage.
     * @param text Detailed AI prompt stage text.
     */
    private void promptStatus(@Nullable Long processId, long reqId, String text) {
        aiPromptMonitor.stage(reqId, text);
        processMonitor.status(processId, text);
    }

    /**
     * @param reqId AI prompt request id.
     * @param ctx Chain context.
     * @param processId User-visible process id.
     */
    private void waitForAiPromptLogs(long reqId, FullChainRunCtx ctx, @Nullable Long processId) {
        long started = ctx.logChecksStartedCount();

        if (started == 0)
            return;

        promptStatus(processId, reqId, "Analyzing build logs for the prompt.");

        ctx.awaitLogChecks(AI_PROMPT_LOG_WAIT_MS);

        long pending = ctx.pendingLogChecksCount();

        if (pending > 0)
            promptStatus(processId, reqId, "Build log analysis timed out; using available log context.");
        else
            promptStatus(processId, reqId, "Build log analysis finished.");
    }

    /**
     * @param srvCodeOrAlias Server id or alias.
     * @param buildId Build id.
     * @param checkAllLogs Check all logs.
     * @param syncMode Synchronization mode.
     * @param prov Credentials provider.
     * @param procLogs Process logs mode override.
     */
    private FullChainRunCtx loadSingleBuildContext(String srvCodeOrAlias, Integer buildId,
        @Nullable Boolean checkAllLogs, SyncMode syncMode, ICredentialsProv prov, ProcessLogsMode procLogs) {
        tcIgnitedProv.checkAccess(srvCodeOrAlias, prov);

        ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, prov);

        String failRateBranch = ITeamcity.DEFAULT;

        return buildChainProcessor.loadFullChainContext(
            tcIgnited,
            Collections.singletonList(buildId),
            LatestRebuildMode.NONE,
            procLogs,
            false,
            failRateBranch,
            syncMode,
            null,
            null);
    }


    public Map<Integer, Integer> getBranchCntrs(String srvCodeOrAlias,
        Integer buildId, ICredentialsProv creds) {
        tcIgnitedProv.checkAccess(srvCodeOrAlias, creds);

        String tcBranch = tcIgnitedProv.server(srvCodeOrAlias, creds).getFatBuild(buildId, SyncMode.LOAD_NEW).branchName(compactor);

        Set<Integer> allBranchIds = new HashSet<>();

        allBranchIds.addAll(branchEquivalence.branchIdsForQuery(tcBranch, compactor));
        allBranchIds.addAll(branchEquivalence.branchIdsForQuery(ITeamcity.DEFAULT, compactor));

        return updateCounters.getCounters(allBranchIds);
    }
}
