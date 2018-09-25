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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.runners.CheckBuildChainResults;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.exception.ServiceUnauthorizedException;
import org.apache.ignite.ci.web.model.chart.ChartData;
import org.apache.ignite.ci.web.model.chart.TestsMetrics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.ci.runners.CheckBuildChainResults.collectHistory;

@Path("metrics")
@Produces("application/json")
public class Metrics {
    public static final String FAILURES_PUBLIC = "failures.public";
    public static final String FAILURES_PRIVATE = "failures.private";
    @Context
    private ServletContext context;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("failuresNoCache")
    public TestsMetrics getFailuresNoCache(){
        CheckBuildChainResults.BuildMetricsHistory history = new CheckBuildChainResults.BuildMetricsHistory();
        final String branch = "master" ;
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branch);
        List<ChainAtServerTracked> chains = tracked.chains;

        //todo take from branches.json
        final String serverId = "public";
        final ICredentialsProv prov = ICredentialsProv.get(req);

        if(!prov.hasAccess(serverId)) {
            throw ServiceUnauthorizedException.noCreds(serverId);
        }

        try (ITeamcity teamcity = CtxListener.getTcHelper(context).server(serverId, prov)) {
            collectHistory(history, teamcity, "IgniteTests24Java8_RunAll", "refs/heads/master");
        }
        return convertToChart(history);
    }

    @GET
    @Path("failures")
    public TestsMetrics getFailures() {
        final BackgroundUpdater updater = (BackgroundUpdater)context.getAttribute(CtxListener.UPDATER);
        return updater.get(FAILURES_PUBLIC, "", k -> getFailuresNoCache());

    }

    @GET
    @Path("failuresPrivate")
    public TestsMetrics getFailuresPrivate(@Nullable @QueryParam("param") String msg)  {
        final BackgroundUpdater updater = (BackgroundUpdater)context.getAttribute(CtxListener.UPDATER);
        return updater.get(FAILURES_PRIVATE, "", k -> getFailuresPrivateNoCache());
    }


    @GET
    @Path("failuresPrivateNoCache")
    @NotNull public TestsMetrics getFailuresPrivateNoCache() {
        //todo take from branches.json
        CheckBuildChainResults.BuildMetricsHistory history = new CheckBuildChainResults.BuildMetricsHistory();
        final String serverId = "private";

        final ICredentialsProv prov = ICredentialsProv.get(req);

        if (!prov.hasAccess(serverId)) {
            throw ServiceUnauthorizedException.noCreds(serverId);
        }

        try (ITeamcity teamcity = CtxListener.getTcHelper(context).server(serverId, prov)) {
            teamcity.setExecutor(CtxListener.getPool(context));

            collectHistory(history, teamcity, "id8xIgniteGridGainTestsJava8_RunAll", "refs/heads/master");
        }
        return convertToChart(history);
    }

    @NotNull
    private TestsMetrics convertToChart(CheckBuildChainResults.BuildMetricsHistory history) {
        TestsMetrics testsMetrics = new TestsMetrics();
        Set<SuiteInBranch> builds = history.builds();
        testsMetrics.initBuilds(builds);//to initialize internal mapping build->idx

        for (String date : history.dates()) {
            Date mddd;
            try {
                mddd = new SimpleDateFormat("yyyyMMdd").parse(date);
            }
            catch (ParseException e) {
                continue;
            }
            String dispDate = new SimpleDateFormat("dd.MM").format(mddd);
            int axisXIdx = testsMetrics.addAxisXLabel(dispDate);
            for (SuiteInBranch next : history.builds()) {
                FullChainRunCtx suiteCtx = history.build(next, date);
                if (suiteCtx != null) {
                    testsMetrics.failed.addMeasurement(next, axisXIdx, (double)suiteCtx.failedTests());
                    testsMetrics.muted.addMeasurement(next, axisXIdx, (double)suiteCtx.mutedTests());
                    testsMetrics.notrun.addMeasurement(next, axisXIdx, (double)suiteCtx.buildProblems());
                    testsMetrics.total.addMeasurement(next, axisXIdx, (double)suiteCtx.totalTests());
                }
            }
        }

        removeOddLabels(testsMetrics.failed);
        removeOddLabels(testsMetrics.muted);
        removeOddLabels(testsMetrics.notrun);
        removeOddLabels(testsMetrics.total);
        return testsMetrics;
    }

    private void removeOddLabels(ChartData<SuiteInBranch> failed) {
        final List<String> x = failed.axisX;
        if (x.size() > 15) {
            double labelWeigh = 15.0 / x.size() ;
            double curWeight=1;
            int idx;
            for (int i = 0; i < x.size(); i++) {
                if (curWeight >= 1) {
                    curWeight = 0;
                    //keep label here
                    continue;
                }
                curWeight += labelWeigh;
                x.set(i, "");
            }
        }
    }

}
