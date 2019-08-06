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

package org.apache.ignite.ci.web.rest.issues;

import com.google.inject.Injector;
import java.util.stream.Collectors;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.tcbot.engine.ui.IssueListUi;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.tcbot.engine.ui.UpdateInfo;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;

@Path(TcIssues.ISSUES)
@Produces(MediaType.APPLICATION_JSON)
public class TcIssues {
    public static final String ISSUES = "issues";

    @Context
    private ServletContext ctx;

    @Context
    private HttpServletRequest req;

    @GET
    @Path("updates")
    public UpdateInfo getAllTestFailsUpdates(@Nullable @QueryParam("branch") String branchOrNull,
        @Nullable @QueryParam("count") Integer count,
        @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {

        return new UpdateInfo(); //.copyFrom(listIssues(branchOrNull, count, checkAllLogs));
    }

    @GET
    @Path("list")
    public IssueListUi listIssues(@Nullable @QueryParam("branch") String branchOpt,
                                @Nullable @QueryParam("count") Integer count,
                                @Nullable @QueryParam("checkAllLogs") Boolean checkAllLogs) {
        Injector injector = CtxListener.getInjector(ctx);

        final String branch = isNullOrEmpty(branchOpt) ? "master" : branchOpt;

        IIssuesStorage issues = injector.getInstance(IIssuesStorage.class);

        IssueListUi issueList = new IssueListUi(issues.allIssues().collect(Collectors.toList()));

        issueList.branch = branch;

        return issueList;
    }

    @GET
    @Path("clear")
    public SimpleResult clear(@Nullable @QueryParam("branch") String branchOpt) {
        return new SimpleResult("Ok");
    }

}
