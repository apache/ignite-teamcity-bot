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

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;

import com.google.inject.Injector;
import org.apache.ignite.ci.*;
import org.apache.ignite.ci.chain.PrChainsProcessor;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
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

@Path(GetPrTestFailures.PR)
@Produces(MediaType.APPLICATION_JSON)
public class GetPrTestFailures {
    public static final String PR = "pr";
    public static final String CURRENT_PR_FAILURES = "currentPrFailures";

    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
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
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        return new UpdateInfo().copyFrom(getPrFailures(srvId, suiteId, branchForTc, act, cnt, baseBranchForTc, checkAllLogs));
    }

    @GET
    @Path("results")
    public TestFailuresSummary getPrFailures(
        @Nullable @QueryParam("serverId") String srvId,
        @Nonnull @QueryParam("suiteId") String suiteId,
        @Nonnull @QueryParam("branchForTc") String branchForTc,
        @Nonnull @QueryParam("action") String act,
        @Nullable @QueryParam("count") Integer cnt,
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(ctx);

        final FullQueryParams key = new FullQueryParams(srvId, suiteId, branchForTc, act, cnt, baseBranchForTc);
        key.setCheckAllLogs(checkAllLogs);
        final ICredentialsProv prov = ICredentialsProv.get(req);

        return updater.get(CURRENT_PR_FAILURES, prov, key,
                (k) -> getPrFailuresNoCache(k.getServerId(), k.getSuiteId(), k.getBranchForTc(), k.getAction(), k.getCount(), baseBranchForTc, k.getCheckAllLogs()),
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
        @Nullable @QueryParam("baseBranchForTc") String baseBranchForTc,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        final ICredentialsProv creds = ICredentialsProv.get(req);
        final Injector injector = CtxListener.getInjector(ctx);
        final PrChainsProcessor prChainsProcessor = injector.getInstance(PrChainsProcessor.class);

        return prChainsProcessor.getTestFailuresSummary(creds, srvId, suiteId, branchForTc, act, cnt, baseBranchForTc,
            checkAllLogs);
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

        PullRequest pr;

        try {
            pr = teamcity.getPullRequest(branchForTc);
        }
        catch (RuntimeException e) {
            return "Exception happened - " + e.getMessage();
        }

        String statusesUrl = pr.getStatusesUrl();

        teamcity.notifyGit(statusesUrl, msg);


        return "Git was notified.";
    }
}
