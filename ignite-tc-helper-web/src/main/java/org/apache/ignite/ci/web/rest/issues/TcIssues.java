package org.apache.ignite.ci.web.rest.issues;

import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.issue.IssueList;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.TriggerBuild;
import org.apache.ignite.ci.web.rest.model.current.UpdateInfo;
import org.jetbrains.annotations.Nullable;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import static com.google.common.base.Strings.isNullOrEmpty;

@Path(TcIssues.ISSUES)
@Produces(MediaType.APPLICATION_JSON)
public class TcIssues {
    public static final String ISSUES = "issue";

    @Context
    private ServletContext context;

    @GET
    @Path("updates")
    public UpdateInfo getAllTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("count") Integer count,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        return new UpdateInfo(); //.copyFrom(listIssues(branchOrNull, count, checkAllLogs));
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

        IssueList issueList = new IssueList(issues.all());

        issueList.branch = branch;

        return issueList;
    }

    @GET
    @Path("clear")
    public TriggerBuild.TriggerResult clear(@Nullable @QueryParam("branch") String branchOpt) {
        return new TriggerBuild.TriggerResult("Ok");
    }

}
