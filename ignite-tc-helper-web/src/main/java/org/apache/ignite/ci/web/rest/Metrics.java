package org.apache.ignite.ci.web.rest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.runners.CheckBuildChainResults;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.ChartData;
import org.apache.ignite.ci.web.rest.model.TestsMetrics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.ci.runners.CheckBuildChainResults.collectHistory;

@Path("metrics")
@Produces("application/json")
public class Metrics {
    @Context
    private ServletContext context;

    @GET
    @Path("failures")
    public TestsMetrics getFailures(@Nullable @QueryParam("param") String msg) throws ParseException {
        Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);
        CheckBuildChainResults.BuildMetricsHistory history = new CheckBuildChainResults.BuildMetricsHistory();
        try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
            collectHistory(history, teamcity, "Ignite20Tests_RunAll", "refs/heads/master");
            // collectHistory(history, teamcity, "Ignite20Tests_RunAll", "ignite-2.2");
            //collectHistory(history, teamcity, "Ignite20Tests_RunAll", "pull/2380/head");
            //collectHistory(history, teamcity, "Ignite20Tests_RunAll", "pull/2508/head");
        }
        return convertToChart(history);
    }

    @GET
    @Path("failuresPrivate")
    public TestsMetrics getFailuresPrivate(@Nullable @QueryParam("param") String msg) throws ParseException {
        Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);
        CheckBuildChainResults.BuildMetricsHistory history = new CheckBuildChainResults.BuildMetricsHistory();
        try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
            collectHistory(history, teamcity, "id8xIgniteGridGainTests_RunAll", "refs/heads/master");
            // collectHistory(history, teamcity, "id8xIgniteGridGainTests_RunAll", "ignite-2.1.4");
            //collectHistory(history, teamcity, "id8xIgniteGridGainTests_RunAll", "ignite-2.1.5");
        }
        return convertToChart(history);
    }

    @NotNull
    private TestsMetrics convertToChart(CheckBuildChainResults.BuildMetricsHistory history) throws ParseException {
        TestsMetrics testsMetrics = new TestsMetrics();
        Set<SuiteInBranch> builds = history.builds();
        testsMetrics.initBuilds(builds);//to initialize internal mapping build->idx

        for (String date : history.dates()) {
            Date mddd = new SimpleDateFormat("yyyyMMdd").parse(date);
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
