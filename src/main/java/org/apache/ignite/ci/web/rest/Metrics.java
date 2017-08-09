package org.apache.ignite.ci.web.rest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
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
import org.apache.ignite.ci.model.SuiteInBranch;
import org.apache.ignite.ci.runners.CheckBuildChainResults;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.TestsMetrics;
import org.jetbrains.annotations.Nullable;

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
            CheckBuildChainResults.collectHistory(history, teamcity,
                "Ignite20Tests_RunAll", "pull/2296/head");

            CheckBuildChainResults.collectHistory(history, teamcity,
                "Ignite20Tests_RunAll", "refs/heads/master");

            CheckBuildChainResults.collectHistory(history, teamcity,
                "Ignite20Tests_RunAll", "pull/2400/head");
        }

        TestsMetrics testsMetrics = new TestsMetrics();
        Set<SuiteInBranch> builds = history.builds();

        testsMetrics.initBuilds(builds);//to initialize internal mapping build->idx

        for (String date : history.dates()) {
            Date mddd = new SimpleDateFormat("yyyyMMdd").parse(date);
            String dispDate = new SimpleDateFormat("dd.MM.yyyy").format(mddd);
            int axisXIdx = testsMetrics.addAxisXLabel(dispDate);
            for (SuiteInBranch next : history.builds()) {
                CheckBuildChainResults.ChainContext suiteCtx = history.build(next, date);
                if (suiteCtx != null) {
                    testsMetrics.failed.addMeasurement(next, axisXIdx, (double)suiteCtx.failedTests());
                    testsMetrics.muted.addMeasurement(next, axisXIdx, (double)suiteCtx.mutedTests());
                    testsMetrics.notrun.addMeasurement(next, axisXIdx, (double)suiteCtx.buildProblems());
                    testsMetrics.total.addMeasurement(next, axisXIdx, (double)suiteCtx.totalTests());
                }

                System.out.print(
                    (suiteCtx == null ? " " : suiteCtx.buildProblems()) + "\t"
                        + (suiteCtx == null ? " " : suiteCtx.failedTests()) + "\t"
                        + (suiteCtx == null ? " " : suiteCtx.mutedTests()) + "\t"
                        + (suiteCtx == null ? " " : suiteCtx.totalTests()) + "\t"
                        + " \t");
            }

            System.out.print("\n");
        }
        System.out.println();
  
        return testsMetrics;

    }

}
