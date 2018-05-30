package org.apache.ignite.ci.web.rest.issues;

import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.detector.Issue;
import org.apache.ignite.ci.detector.IssueList;
import org.apache.ignite.ci.detector.IssuesStorage;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.rest.model.current.UpdateInfo;
import org.jetbrains.annotations.Nullable;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;

@Path(TcIssues.ISSUES)
@Produces(MediaType.APPLICATION_JSON)
public class TcIssues {
    public static final String ISSUES = "issues";

    @Context
    private ServletContext context;

    @GET
    @Path("updates")
    public UpdateInfo getAllTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("count") Integer count,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        return new UpdateInfo();//.copyFrom(listIssues(branchOrNull, count, checkAllLogs));
    }

    @GET
    @Path("list")
    public IssueList listIssues(@Nullable @QueryParam("branch") String branchOpt,
                                @Nullable @QueryParam("count") Integer count,
                                @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        final ITcHelper helper = CtxListener.getTcHelper(context);

        final String branch = isNullOrEmpty(branchOpt) ? "master" : branchOpt;

        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branch);

        IssuesStorage issues = helper.issues();

        return new IssueList(issues.all());
    }
}
