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
package org.apache.ignite.ci.web.rest.long_running;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.tcbot.engine.tracked.TrackedBranchChainsProcessor;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.tcbot.engine.ui.LrTestsFullSummaryUi;
import org.jetbrains.annotations.Nullable;

@Path(BuildsLongRunningTestsReport.LONG_RUNNING_SUMMARY)
@Produces(MediaType.APPLICATION_JSON)
public class BuildsLongRunningTestsReport {
    public static final String LONG_RUNNING_SUMMARY = "long_running";

    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path("summary")
    @Produces(MediaType.APPLICATION_JSON)
    public LrTestsFullSummaryUi getBranch(@Nullable @QueryParam("branch") String branchOrNull) {
        final ITcBotUserCreds creds = ITcBotUserCreds.get(req);

        final TrackedBranchChainsProcessor tbProc = CtxListener.getInjector(ctx).getInstance(TrackedBranchChainsProcessor.class);

        return tbProc.getTrackedBranchLongRunningTestsSummary(branchOrNull, creds);
    }
}
