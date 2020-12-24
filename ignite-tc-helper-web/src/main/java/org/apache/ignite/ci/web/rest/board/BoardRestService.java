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
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.tcbot.engine.board.BoardService;
import org.apache.ignite.tcbot.engine.boardmute.MutedBoardIssueInfo;
import org.apache.ignite.tcbot.engine.boardmute.MutedBoardIssueKey;
import org.apache.ignite.tcbot.engine.ui.BoardSummaryUi;
import org.apache.ignite.tcbot.engine.ui.MutedIssueUi;

@Path(BoardRestService.BOARD)
@Produces(MediaType.APPLICATION_JSON)
public class BoardRestService {
    static final String BOARD = "board";

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
    @Path("muteTest")
    public void muteTest(
        @FormParam("tcSrvId") int tcSrvId,
        @FormParam("name") String name,
        @FormParam("branch") String branch,
        @FormParam("trackedBranch") String trackedBranch,
        @FormParam("issueType") String issueType,
        @FormParam("jiraTicket") String jiraTicket,
        @FormParam("comment") String comment,
        @FormParam("userName") String userName,
        @FormParam("webUrl") String webUrl) {
        CtxListener.getInjector(ctx).getInstance(BoardService.class)
            .muteTest(tcSrvId, name, branch, trackedBranch, issueType, jiraTicket, comment, userName, webUrl);
    }

    @DELETE
    @Path("unmuteTest")
    public void unmuteTest(
        @FormParam("tcSrvId") int tcSrvId,
        @FormParam("name") String name,
        @FormParam("branch") String branch,
        @FormParam("issueType") String issueType) {
        CtxListener.getInjector(ctx).getInstance(BoardService.class)
            .unmuteTest(tcSrvId, name, branch, issueType);
    }

    @GET
    @Path("mutedissues")
    public Collection<MutedIssueUi> getMutedIssues(@QueryParam("baseBranch") String baseBranch) {
        return CtxListener.getInjector(ctx).getInstance(BoardService.class).getAllMutedIssues(baseBranch);
    }
}
