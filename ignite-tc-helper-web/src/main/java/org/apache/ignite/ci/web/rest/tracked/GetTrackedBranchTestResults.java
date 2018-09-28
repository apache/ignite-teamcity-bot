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

import org.apache.ignite.ci.chain.TrackedBranchChainsProcessor;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
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

        final ICredentialsProv creds = ICredentialsProv.get(request);

        final TrackedBranchChainsProcessor tbProc = CtxListener.getInjector(ctx).getInstance(TrackedBranchChainsProcessor.class);

        return tbProc.getTrackedBranchTestFailures(branch, checkAllLogs, 1, creds
        );
    }

    @GET
    @Path("mergedUpdates")
    public UpdateInfo getAllTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull,
                                             @Nullable @QueryParam("count") Integer cnt,
                                             @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        return new UpdateInfo().copyFrom(getAllTestFails(branchOrNull, cnt, checkAllLogs));
    }

    @GET
    @Path("mergedResults")
    public TestFailuresSummary getAllTestFails(@Nullable @QueryParam("branch") String branchOrNull,
                                               @Nullable @QueryParam("count") Integer cnt,
                                               @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(ctx);
        FullQueryParams fullKey = new FullQueryParams();
        fullKey.setBranch(branchOrNull);
        fullKey.setCount(cnt == null ? FullQueryParams.DEFAULT_COUNT : cnt);
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

    @GET
    @Path("mergedResultsNoCache")
    @NotNull
    public TestFailuresSummary getAllTestFailsNoCache(@Nullable @QueryParam("branch") String branchOpt,
                                                      @QueryParam("count") Integer cnt,
                                                      @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        final ICredentialsProv creds = ICredentialsProv.get(request);
        int cntLimit = cnt == null ? FullQueryParams.DEFAULT_COUNT : cnt;
        final TrackedBranchChainsProcessor tbProc = CtxListener.getInjector(ctx).getInstance(TrackedBranchChainsProcessor.class);

        return tbProc.getTrackedBranchTestFailures(branchOpt, checkAllLogs, cntLimit, creds);
    }
}
