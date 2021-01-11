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
package org.apache.ignite.ci.web.rest.board;

import java.util.Collection;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.tcbot.engine.board.BoardService;
import org.apache.ignite.tcbot.engine.ui.BoardSummaryUi;
import org.apache.ignite.tcbot.engine.ui.MutedIssueUi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(BoardRestService.BOARD)
@Produces(MediaType.APPLICATION_JSON)
public class BoardRestService {
    static final String BOARD = "board";

    /** */
    private static final Logger logger = LoggerFactory.getLogger(BoardRestService.class);

    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path("summary")
    public BoardSummaryUi getSummary(@QueryParam("baseBranch") String baseBranch) {
        ITcBotUserCreds creds = ITcBotUserCreds.get(req);

        return CtxListener.getInjector(ctx).getInstance(BoardService.class).summary(creds, baseBranch);
    }

    @PUT
    @Path("muteIssue")
    public void muteIssue(
        @FormParam("tcSrvId") int tcSrvId,
        @FormParam("nameId") int nameId,
        @FormParam("branch") String branch,
        @FormParam("trackedBranch") String trackedBranch,
        @FormParam("issueType") String issueType,
        @FormParam("jiraTicket") String jiraTicket,
        @FormParam("comment") String comment,
        @FormParam("userName") String userName,
        @FormParam("webUrl") String webUrl) {
        CtxListener.getInjector(ctx).getInstance(BoardService.class)
            .muteIssue(tcSrvId, nameId, branch, trackedBranch, issueType, jiraTicket, comment, userName, webUrl);
    }

    @PATCH
    @Path("updateIssue")
    public void updateIssue(
        @FormParam("tcSrvId") int tcSrvId,
        @FormParam("nameId") int nameId,
        @FormParam("branch") String branch,
        @FormParam("issueType") String issueType,
        @FormParam("jiraTicket") String jiraTicket,
        @FormParam("comment") String comment) {
        CtxListener.getInjector(ctx).getInstance(BoardService.class)
            .updateIssue(tcSrvId, nameId, branch, issueType, jiraTicket, comment);
    }

    @DELETE
    @Path("unmuteIssue")
    public void unmuteIssue(
        @FormParam("tcSrvId") int tcSrvId,
        @FormParam("nameId") int nameId,
        @FormParam("branch") String branch,
        @FormParam("issueType") String issueType) {
        CtxListener.getInjector(ctx).getInstance(BoardService.class)
            .unmuteIssue(tcSrvId, nameId, branch, issueType);
    }

    @GET
    @Path("mutedIssues")
    public Collection<MutedIssueUi> getMutedIssues(@QueryParam("baseBranch") String baseBranch) {
        return CtxListener.getInjector(ctx).getInstance(BoardService.class).getAllMutedIssues(baseBranch);
    }
}
