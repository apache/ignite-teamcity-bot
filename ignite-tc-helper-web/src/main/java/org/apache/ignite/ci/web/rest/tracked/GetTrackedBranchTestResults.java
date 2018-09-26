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

import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.model.current.UpdateInfo;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;

@Path(GetTrackedBranchTestResults.TRACKED)
@Produces(MediaType.APPLICATION_JSON)
public class GetTrackedBranchTestResults {
    public static final String TRACKED = "tracked";
    public static final String TEST_FAILURES_SUMMARY_CACHE_NAME = "currentTestFailuresSummary";
    public static final String ALL_TEST_FAILURES_SUMMARY = "AllTestFailuresSummary";

    @Context
    private ServletContext ctx;

    @Context
    private HttpServletRequest request;

    @GET
    @Path("updates")
    public UpdateInfo getTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull,
                                          @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return new UpdateInfo().copyFrom(getTestFails(branchOrNull, checkAllLogs));
    }

    @GET
    @Path("results/txt")
    @Produces(MediaType.TEXT_PLAIN)
    public String getTestFailsText(@Nullable @QueryParam("branch") String branchOrNull,
                                   @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        return getTestFails(branchOrNull, checkAllLogs).toString();
    }

    @GET
    @Path("results")
    public TestFailuresSummary getTestFails(
            @Nullable @QueryParam("branch") String branchOrNull,
            @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(ctx);

        FullQueryParams param = new FullQueryParams();
        param.setBranch(branchOrNull);
        param.setCheckAllLogs(checkAllLogs);

        return updater.get(TEST_FAILURES_SUMMARY_CACHE_NAME, ICredentialsProv.get(request), param,
                (k) -> getTestFailsNoCache(k.getBranch(), k.getCheckAllLogs()), true
        );
    }

    @GET
    @Path("resultsNoCache")
    @NotNull
    public TestFailuresSummary getTestFailsNoCache(
            @Nullable @QueryParam("branch") String branch,
            @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        final ITcHelper helper = CtxListener.getTcHelper(ctx);
        final ICredentialsProv creds = ICredentialsProv.get(request);

        return getTrackedBranchTestFailures(branch, checkAllLogs, 1, helper, creds,
                CtxListener.getPool(ctx));
    }

    @GET
    @Path("mergedUpdates")
    public UpdateInfo getAllTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull,
                                             @Nullable @QueryParam("count") Integer count,
                                             @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        return new UpdateInfo().copyFrom(getAllTestFails(branchOrNull, count, checkAllLogs));
    }

    @GET
    @Path("mergedResults")
    public TestFailuresSummary getAllTestFails(@Nullable @QueryParam("branch") String branchOrNull,
                                               @Nullable @QueryParam("count") Integer count,
                                               @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(ctx);
        FullQueryParams fullKey = new FullQueryParams();
        fullKey.setBranch(branchOrNull);
        fullKey.setCount(count == null ? FullQueryParams.DEFAULT_COUNT : count);
        fullKey.setCheckAllLogs(checkAllLogs != null && checkAllLogs);

        final ICredentialsProv creds = ICredentialsProv.get(request);
        return updater.get(ALL_TEST_FAILURES_SUMMARY, creds,
                fullKey,
                k -> getAllTestFailsNoCache(
                        k.getBranch(),
                        k.getCount(),
                        k.getCheckAllLogs()),
                false);
    }

    @NotNull
    public static TestFailuresSummary getTrackedBranchTestFailures(
            @Nullable @QueryParam("branch") String branch,
            @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs,
            int buildResultMergeCnt,
            ITcHelper helper,
            ICredentialsProv creds,
            @Nullable ExecutorService pool) {
        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        final String branchNn = isNullOrEmpty(branch) ? FullQueryParams.DEFAULT_BRANCH_NAME : branch;
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branchNn);
        res.setTrackedBranch(branchNn);

        tracked.chains.stream().parallel()
            .filter(chainTracked -> creds.hasAccess(chainTracked.serverId))
            .map(chainTracked -> {
                final String srvId = chainTracked.serverId;

                final String branchForTc = chainTracked.getBranchForRestMandatory();

                //branch is tracked, so fail rate should be taken from this branch data (otherwise it is specified).
                final String baseBranchTc = chainTracked.getBaseBranchForTc().orElse(branchForTc);

                final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus(srvId, branchForTc);

                chainStatus.baseBranchForTc = baseBranchTc;

                IAnalyticsEnabledTeamcity teamcity = helper.server(srvId, creds);

                final List<BuildRef> builds = teamcity.getFinishedBuildsIncludeSnDepFailed(
                    chainTracked.getSuiteIdMandatory(),
                    branchForTc);

                List<BuildRef> chains = builds.stream()
                    .filter(ref -> !ref.isFakeStub())
                    .sorted(Comparator.comparing(BuildRef::getId).reversed())
                    .limit(buildResultMergeCnt)
                    .filter(b -> b.getId() != null).collect(Collectors.toList());

                ProcessLogsMode logs;
                if (buildResultMergeCnt > 1)
                    logs = checkAllLogs != null && checkAllLogs ? ProcessLogsMode.ALL : ProcessLogsMode.DISABLED;
                else
                    logs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE;

                LatestRebuildMode rebuild = buildResultMergeCnt > 1 ? LatestRebuildMode.ALL : LatestRebuildMode.LATEST;

                boolean includeScheduled = buildResultMergeCnt == 1;

                Optional<FullChainRunCtx> chainCtxOpt
                    = BuildChainProcessor.processBuildChains(teamcity,
                    rebuild, chains, logs,
                    includeScheduled, true, teamcity, baseBranchTc,
                    pool);

                chainCtxOpt.ifPresent(ctx -> {
                    int cnt = (int)ctx.getRunningUpdates().count();
                    if (cnt > 0)
                        runningUpdates.addAndGet(cnt);

                    chainStatus.initFromContext(teamcity, ctx, teamcity, baseBranchTc);
                });

                return chainStatus;
            })
            .forEach(res::addChainOnServer);

        res.servers.sort(Comparator.comparing(ChainAtServerCurrentStatus::serverName));

        res.postProcess(runningUpdates.get());

        return res;
    }


    @GET
    @Path("mergedResultsNoCache")
    @NotNull
    public TestFailuresSummary getAllTestFailsNoCache(@Nullable @QueryParam("branch") String branchOpt,
                                                      @QueryParam("count") Integer cnt,
                                                      @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        final ITcHelper helper = CtxListener.getTcHelper(ctx);
        final ICredentialsProv creds = ICredentialsProv.get(request);
        int cntLimit = cnt == null ? FullQueryParams.DEFAULT_COUNT : cnt;

        return getTrackedBranchTestFailures(branchOpt, checkAllLogs, cntLimit, helper, creds, CtxListener.getPool(ctx));
    }
}
