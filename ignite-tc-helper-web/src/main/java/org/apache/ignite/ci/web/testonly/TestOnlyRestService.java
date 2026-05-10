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

package org.apache.ignite.ci.web.testonly;

import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.tcbot.ITcBotBgAuth;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.githubignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.jiraignited.IJiraIgnitedProvider;
import org.apache.ignite.jiraignited.JiraTicketSync;
import org.apache.ignite.tcbot.common.application.TcBotApplicationContext;
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;

/**
 * Test-only hooks used by the production-like integration harness.
 */
@Path("__test__/bot")
@Produces(MediaType.APPLICATION_JSON)
public class TestOnlyRestService {
    /** JVM profile property enabling this resource. */
    public static final String PROFILE_PROPERTY = "tcbot.profile";

    /** Required profile value. */
    public static final String INTEGRATION_TEST_PROFILE = "integration-test";

    /** Context. */
    @Context
    private ServletContext ctx;

    /**
     * @param srvCode Server id.
     * @param processId Optional process id.
     */
    @POST
    @Path("refresh-github")
    public TestOnlyRunResult refreshGithub(@QueryParam("serverId") String srvCode,
        @QueryParam("processId") Long processId) {
        return run("TestOnly.refreshGithub." + srvCode, processId, () -> {
            BotProcessMonitor process = process();
            IGitHubConnIgnited github = appCtx().getInstance(IGitHubConnIgnitedProvider.class).server(srvCode);

            process.status(processId, "Refreshing GitHub pull requests from emulator/test endpoint.");
            String prs = github.refreshPullRequests();

            process.status(processId, "Refreshing GitHub branches from emulator/test endpoint.");
            String branches = github.refreshBranches();

            return "serverId=" + srvCode + ": " + prs + "; " + branches;
        });
    }

    /**
     * @param srvCode Server id.
     * @param processId Optional process id.
     */
    @POST
    @Path("refresh-jira")
    public TestOnlyRunResult refreshJira(@QueryParam("serverId") String srvCode,
        @QueryParam("processId") Long processId) {
        return run("TestOnly.refreshJira." + srvCode, processId, () -> {
            BotProcessMonitor process = process();

            process.status(processId, "Refreshing JIRA tickets from emulator/test endpoint.");
            appCtx().getInstance(IJiraIgnitedProvider.class).server(srvCode);

            return "serverId=" + srvCode + ": " + appCtx().getInstance(JiraTicketSync.class).incrementalUpdate(srvCode);
        });
    }

    /**
     * @param srvCode Server id.
     * @param processId Optional process id.
     */
    @POST
    @Path("refresh-teamcity-builds")
    public TestOnlyRunResult refreshTeamcityBuilds(@QueryParam("serverId") String srvCode,
        @QueryParam("processId") Long processId) {
        return run("TestOnly.refreshTeamcityBuilds." + srvCode, processId, () -> {
            BotProcessMonitor process = process();

            process.status(processId, "Refreshing TeamCity build references from emulator/test endpoint.");
            appCtx().getInstance(ITeamcityIgnitedProvider.class)
                .server(srvCode, appCtx().getInstance(ITcBotBgAuth.class).getServerAuthorizerCreds())
                .actualizeRecentBuildRefs();

            return "serverId=" + srvCode + ": TeamCity build references refreshed.";
        });
    }

    /**
     * @param processId Optional process id.
     */
    @POST
    @Path("run-build-observer")
    public TestOnlyRunResult runBuildObserver(@QueryParam("processId") Long processId) {
        return run("TestOnly.runBuildObserver", processId, () -> {
            process().status(processId, "Running build observer from emulator/test endpoint.");

            return appCtx().getInstance(BuildObserver.class).runNow();
        });
    }

    /**
     * @param taskName Scheduler task name.
     * @param processId Optional process id.
     * @param action Test hook action.
     */
    private TestOnlyRunResult run(String taskName, @Nullable Long processId, TestOnlyAction action) {
        ensureIntegrationTestProfile();

        BotProcessMonitor process = process();

        process.start(processId, "testOnly", "Test-only action request accepted: " + taskName);

        boolean accepted = appCtx().getInstance(IScheduler.class).runNamedNow(taskName, () -> {
            try {
                process.status(processId, "Running test-only action: " + taskName);

                String result = action.run();

                process.finish(processId, result);
            }
            catch (Exception e) {
                process.fail(processId, e);
                throw new RuntimeException(e);
            }
        }, processId);

        if (!accepted) {
            process.fail(processId, "Test-only action is already queued or running: " + taskName);

            throw new ClientErrorException("Test-only action is already queued or running: " + taskName,
                Response.Status.CONFLICT);
        }

        return new TestOnlyRunResult(taskName, processId, true);
    }

    /** */
    private void ensureIntegrationTestProfile() {
        if (!INTEGRATION_TEST_PROFILE.equals(System.getProperty(PROFILE_PROPERTY)))
            throw new NotFoundException("Test-only API is available only in integration-test profile.");
    }

    /** */
    private TcBotApplicationContext appCtx() {
        return CtxListener.getApplicationContext(ctx);
    }

    /** */
    private BotProcessMonitor process() {
        return appCtx().getInstance(BotProcessMonitor.class);
    }

    /** Test-only action body. */
    private interface TestOnlyAction {
        /** */
        public String run() throws Exception;
    }

    /** Test-only run result. */
    public static class TestOnlyRunResult {
        /** Task name. */
        public String task;

        /** Process id. */
        @Nullable public Long processId;

        /** Whether task was accepted. */
        public boolean queued;

        /** */
        public TestOnlyRunResult() {
            // No-op.
        }

        /**
         * @param task Task name.
         * @param processId Process id.
         * @param queued Queued flag.
         */
        public TestOnlyRunResult(String task, @Nullable Long processId, boolean queued) {
            this.task = task;
            this.processId = processId;
            this.queued = queued;
        }
    }
}
