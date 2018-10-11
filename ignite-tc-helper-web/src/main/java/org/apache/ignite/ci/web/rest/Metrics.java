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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.tcbot.chain.BuildChainProcessor;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.runners.BuildHistory;
import org.apache.ignite.ci.runners.BuildMetricsHistory;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.exception.ServiceUnauthorizedException;
import org.apache.ignite.ci.web.model.chart.ChartData;
import org.apache.ignite.ci.web.model.chart.TestsMetrics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Collections.singletonList;

@Path("metrics")
@Produces("application/json")
@Deprecated
public class Metrics {
    public static final String FAILURES_PUBLIC = "failures.public";
    public static final String FAILURES_PRIVATE = "failures.private";
    @Context
    private ServletContext ctx;

    @Context
    private HttpServletRequest req;


    @Deprecated
    public void collectHistory(BuildMetricsHistory history,
        IAnalyticsEnabledTeamcity teamcity, String id, String branch)  {

        BuildChainProcessor bcp = CtxListener.getInjector(ctx).getInstance(BuildChainProcessor.class);

        final SuiteInBranch branchId = new SuiteInBranch(id, branch);
        final BuildHistory suiteHist = history.history(branchId);
        final List<BuildRef> all = teamcity.getFinishedBuildsIncludeSnDepFailed(id, branch);
        final List<Build> fullBuildInfoList = all.stream()
            .map(b -> teamcity.getBuild(b.href))
            .filter(Objects::nonNull)
            .filter(b -> b.getId() != null)
            .collect(Collectors.toList());

        for (Build next : fullBuildInfoList) {
            Date parse = next.getFinishDate();
            String dateForMap = new SimpleDateFormat("yyyyMMdd").format(parse);
            suiteHist.map.computeIfAbsent(dateForMap, k -> {
                FullChainRunCtx ctx = bcp.loadFullChainContext(teamcity,
                    singletonList(next),
                    LatestRebuildMode.NONE,
                    ProcessLogsMode.DISABLED, false,
                    ITeamcity.DEFAULT);

                if (ctx == null)
                    return null;

                for (MultBuildRunCtx suite : ctx.suites()) {
                    boolean suiteOk = suite.failedTests() == 0 && !suite.hasCriticalProblem();
                    history.addSuiteResult(teamcity.serverId() + "\t" + suite.suiteName(), suiteOk);
                }
                return ctx;
            });
        }
    }

    @GET
    @Path("failuresNoCache")
    public TestsMetrics getFailuresNoCache() {
        BuildMetricsHistory history = new BuildMetricsHistory();
        final String branch = "master";
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branch);
        List<ChainAtServerTracked> chains = tracked.chains;

        //todo take from branches.json
        final String serverId = "public";
        final ICredentialsProv prov = ICredentialsProv.get(req);

        if (!prov.hasAccess(serverId))
            throw ServiceUnauthorizedException.noCreds(serverId);

        IAnalyticsEnabledTeamcity teamcity = CtxListener.server(serverId, ctx, req);

        collectHistory(history, teamcity, "IgniteTests24Java8_RunAll", "refs/heads/master");

        return convertToChart(history);
    }

    @GET
    @Path("failures")
    public TestsMetrics getFailures() {
        final BackgroundUpdater updater = CtxListener.getInjector(ctx).getInstance(BackgroundUpdater.class);
        return updater.get(FAILURES_PUBLIC, null, "", k -> getFailuresNoCache(), false);

    }

    @GET
    @Path("failuresPrivate")
    public TestsMetrics getFailuresPrivate(@Nullable @QueryParam("param") String msg) {
        final BackgroundUpdater updater = CtxListener.getBackgroundUpdater(ctx);
        return updater.get(FAILURES_PRIVATE, null, "", k -> getFailuresPrivateNoCache(), false);
    }


    @GET
    @Path("failuresPrivateNoCache")
    @NotNull public TestsMetrics getFailuresPrivateNoCache() {
        //todo take from branches.json
        BuildMetricsHistory hist = new BuildMetricsHistory();
        final String srvId = "private";

        final ICredentialsProv prov = ICredentialsProv.get(req);

        if (!prov.hasAccess(srvId))
            throw ServiceUnauthorizedException.noCreds(srvId);

        IAnalyticsEnabledTeamcity teamcity = CtxListener.server(srvId, ctx, req);

        collectHistory(hist, teamcity, "id8xIgniteGridGainTestsJava8_RunAll", "refs/heads/master");

        return convertToChart(hist);
    }

    @NotNull
    private TestsMetrics convertToChart(BuildMetricsHistory hist) {
        TestsMetrics testsMetrics = new TestsMetrics();
        Set<SuiteInBranch> builds = hist.builds();
        testsMetrics.initBuilds(builds);//to initialize internal mapping build->idx

        for (String date : hist.dates()) {
            Date mddd;
            try {
                mddd = new SimpleDateFormat("yyyyMMdd").parse(date);
            }
            catch (ParseException e) {
                continue;
            }
            String dispDate = new SimpleDateFormat("dd.MM").format(mddd);
            int axisXIdx = testsMetrics.addAxisXLabel(dispDate);
            for (SuiteInBranch next : hist.builds()) {
                FullChainRunCtx suiteCtx = hist.build(next, date);
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
