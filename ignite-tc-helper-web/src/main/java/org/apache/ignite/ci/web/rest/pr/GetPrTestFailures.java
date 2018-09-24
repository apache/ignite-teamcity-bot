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

import com.google.common.base.Strings;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;

import org.apache.ignite.ci.*;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.github.PullRequest;
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

import javax.annotation.Nonnull;
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

@Path(GetPrTestFailures.PR)
@Produces(MediaType.APPLICATION_JSON)
public class GetPrTestFailures {
    public static final String PR = "pr";
    public static final String CURRENT_PR_FAILURES = "currentPrFailures";

    @Context
    private ServletContext ctx;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("updates")
    public UpdateInfo getPrFailuresUpdates(
        @Nullable @QueryParam("serverId") String srvId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String act,
        @Nullable @QueryParam("count") Integer cnt,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc) {

        return new UpdateInfo().copyFrom(getPrFailures(srvId, suiteId, branchForTc, act, cnt, baseBranchForTc));
    }

    @GET
    @Path("results")
    public TestFailuresSummary getPrFailures(
        @Nullable @QueryParam("serverId") String srvId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String act,
        @Nullable @QueryParam("count") Integer cnt,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc) {

        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(ctx);

        final FullQueryParams key = new FullQueryParams(srvId, suiteId, branchForTc, act, cnt, baseBranchForTc);

        final ICredentialsProv prov = ICredentialsProv.get(req);

        return updater.get(CURRENT_PR_FAILURES, prov, key,
                (k) -> getPrFailuresNoCache(k.getServerId(), k.getSuiteId(), k.getBranchForTc(), k.getAction(), k.getCount(), baseBranchForTc),
                true);
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
    @Path("resultsNoCache")
    @NotNull public TestFailuresSummary getPrFailuresNoCache(
        @Nullable @QueryParam("serverId") String srvId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String act,
        @Nullable @QueryParam("count") Integer cnt,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc) {

        final ITcHelper tcHelper = CtxListener.getTcHelper(ctx);
        final ICredentialsProv creds = ICredentialsProv.get(req);

        return getTestFailuresSummary(tcHelper, creds, srvId, suiteId, branchForTc, act, cnt, baseBranchForTc,
                CtxListener.getPool(ctx));
    }

    /**
     * @param helper Helper.
     * @param creds Credentials.
     * @param srvId Server id.
     * @param suiteId Suite id.
     * @param branchForTc Branch name in TC identification.
     * @param act Action.
     * @param cnt Count.
     * @param baseBranchForTc Base branch name in TC identification.
     * @param executorService Executor service to process TC communication requests there.
     * @return Test failures summary.
     */
    public static TestFailuresSummary getTestFailuresSummary(
            ITcHelper helper,
            ICredentialsProv creds,
            String srvId,
            String suiteId,
            String branchForTc,
            String act,
            Integer cnt,
            @Nullable String baseBranchForTc,
            @Nullable ExecutorService executorService) {
        final TestFailuresSummary res = new TestFailuresSummary();
        final AtomicInteger runningUpdates = new AtomicInteger();

        //using here non persistent TC allows to skip update statistic
          IAnalyticsEnabledTeamcity teamcity = helper.server(srvId, creds);
          {
            res.setJavaFlags(teamcity);

            LatestRebuildMode rebuild;
            if (FullQueryParams.HISTORY.equals(act))
                rebuild = LatestRebuildMode.ALL;
            else if (FullQueryParams.LATEST.equals(act))
                rebuild = LatestRebuildMode.LATEST;
            else if (FullQueryParams.CHAIN.equals(act))
                rebuild = LatestRebuildMode.NONE;
            else
                rebuild = LatestRebuildMode.LATEST;

            List<BuildRef> finishedBuilds = teamcity.getFinishedBuildsIncludeSnDepFailed(
                suiteId,
                branchForTc);

            long limit;
            if (rebuild == LatestRebuildMode.ALL)
                limit = cnt == null ? 10 : cnt;
            else
                limit = 1;

            final List<BuildRef> chains = finishedBuilds.stream()
                .filter(ref -> !ref.isFakeStub())
                .sorted(Comparator.comparing(BuildRef::getId).reversed())
                .limit(limit)
                .filter(b -> b.getId() != null).collect(Collectors.toList());

            boolean singleBuild = rebuild != LatestRebuildMode.ALL;
            ProcessLogsMode logs = singleBuild
                ? ProcessLogsMode.SUITE_NOT_COMPLETE
                : ProcessLogsMode.DISABLED;

            String baseBranch = Strings.isNullOrEmpty(baseBranchForTc) ? ITeamcity.DEFAULT : baseBranchForTc;

            Optional<FullChainRunCtx> pubCtx = BuildChainProcessor.processBuildChains(teamcity, rebuild, chains,
                logs,
                singleBuild,
                true, teamcity, baseBranch, executorService);

            final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus(teamcity.serverId(), branchForTc);

            chainStatus.baseBranchForTc = baseBranch;

            pubCtx.ifPresent(ctx -> {
                if (ctx.isFakeStub())
                    chainStatus.setBuildNotFound(true);
                else {
                    int cnt0 = (int)ctx.getRunningUpdates().count();

                    if (cnt0 > 0)
                        runningUpdates.addAndGet(cnt0);

                    //fail rate reference is always default (master)
                    chainStatus.initFromContext(teamcity, ctx, teamcity, baseBranch);
                }
            });

            res.addChainOnServer(chainStatus);
        }

        res.postProcess(runningUpdates.get());

        return res;
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

        IAnalyticsEnabledTeamcity teamcity = CtxListener.server(srvId, ctx, req);

        PullRequest pr = teamcity.getPullRequest(branchForTc);
        String statusesUrl = pr.getStatusesUrl();

        teamcity.notifyGit(statusesUrl, msg);


        return "Git was notified.";
    }

}
