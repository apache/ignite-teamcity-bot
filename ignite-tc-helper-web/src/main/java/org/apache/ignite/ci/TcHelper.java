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

package org.apache.ignite.ci;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.ignite.ci.conf.BranchesTracked;
import org.apache.ignite.ci.di.IServerFactory;
import org.apache.ignite.ci.issue.IssueDetector;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.util.ExceptionUtil;
import org.apache.ignite.ci.web.TcUpdatePool;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.apache.ignite.ci.web.model.hist.FailureSummary;
import org.apache.ignite.ci.web.rest.pr.GetPrTestFailures;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.ignite.ci.analysis.RunStat.MAX_LATEST_RUNS;
import static org.apache.ignite.ci.util.XmlUtil.xmlEscapeText;

/**
 * TC Bot implementation
 */
public class TcHelper implements ITcHelper {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TcHelper.class);

    /** Stop guard. */
    private AtomicBoolean stop = new AtomicBoolean();

    @Inject
    private TcUpdatePool tcUpdatePool;

    @Inject
    private IssuesStorage issuesStorage;

    @Inject
    private ITcServerProvider serverProvider;

    @Inject
    private IssueDetector detector;
    /** Build observer. */
    private BuildObserver buildObserver;

    @Inject
    private UserAndSessionsStorage userAndSessionsStorage;

    public TcHelper() {
        buildObserver = new BuildObserver(this);
    }

    /** {@inheritDoc} */
    @Override public IssuesStorage issues() {
        return issuesStorage;
    }

    /** {@inheritDoc} */
    @Override public IssueDetector issueDetector() {
        return detector;
    }

    /** {@inheritDoc} */
    @Override public BuildObserver buildObserver() {
        return buildObserver;
    }

    /** {@inheritDoc} */
    @Override public IAnalyticsEnabledTeamcity server(String srvId, @Nullable ICredentialsProv prov) {
        if (stop.get())
            throw new IllegalStateException("Shutdown");

        return serverProvider.server(srvId, prov);
    }

    /** {@inheritDoc} */
    @Override public ITcAnalytics tcAnalytics(String srvId) {
        return server(srvId, null);
    }

    /** {@inheritDoc} */
    @Override public UserAndSessionsStorage users() {
        return userAndSessionsStorage;
    }

    @Override public String primaryServerId() {
        return "apache"; //todo remove
    }

    //todo get from persistence
    @Override public Collection<String> getServerIds() {
        return getTrackedBranches().getServerIds();
    }

    private BranchesTracked getTrackedBranches() {
        return HelperConfig.getTrackedBranches();
    }

    @Override public List<String> getTrackedBranchesIds() {
        return getTrackedBranches().getIds();
    }

    /** {@inheritDoc} */
    @Override public boolean notifyJira(
        String srvId,
        ICredentialsProv prov,
        String buildTypeId,
        String branchForTc,
        String ticket
    ) {
        IAnalyticsEnabledTeamcity teamcity = server(srvId, prov);

        List<BuildRef> builds = teamcity.getFinishedBuildsIncludeSnDepFailed(buildTypeId, branchForTc);

        if (builds.isEmpty())
            return false;

        BuildRef build = builds.get(builds.size() - 1);
        String comment;

        try {
            comment = generateJiraComment(buildTypeId, build.branchName, srvId, prov, build.webUrl,
                getService());
        }
        catch (RuntimeException e) {
            logger.error("Exception happened during generating comment for JIRA " +
                "[build=" + build.getId() + ", errMsg=" + e.getMessage() + ']');

            return false;
        }

        if ("finished".equals(build.state))
            return teamcity.sendJiraComment(ticket, comment);

        return false;

    }

    /**
     * @param buildTypeId Suite name.
     * @param branchForTc Branch for TeamCity.
     * @param srvId Server id.
     * @param prov Credentials.
     * @param webUrl Build URL.
     * @param executorService Executor service to process TC communication requests there.
     * @return Comment, which should be sent to the JIRA ticket.
     */
    private String generateJiraComment(
            String buildTypeId,
            String branchForTc,
            String srvId,
            ICredentialsProv prov,
            String webUrl,
            @Nullable ExecutorService executorService
    ) {
        StringBuilder res = new StringBuilder();
        TestFailuresSummary summary = GetPrTestFailures.getTestFailuresSummary(
            this, prov, srvId, buildTypeId, branchForTc,
            "Latest", null, null, executorService);

        if (summary != null) {
            for (ChainAtServerCurrentStatus server : summary.servers) {
                if (!"apache".equals(server.serverName()))
                    continue;

                Map<String, List<SuiteCurrentStatus>> fails = findFailures(server);

                for (List<SuiteCurrentStatus> suites : fails.values()) {
                    for (SuiteCurrentStatus suite : suites) {
                        res.append("{color:#d04437}").append(suite.name).append("{color}");
                        res.append(" [[tests ").append(suite.failedTests);

                        if (suite.result != null && !suite.result.isEmpty())
                            res.append(' ').append(suite.result);

                        res.append('|').append(suite.webToBuild).append("]]\\n");

                        for (TestFailure failure : suite.testFailures) {
                            res.append("* ");

                            if (failure.suiteName != null && failure.testName != null)
                                res.append(failure.suiteName).append(": ").append(failure.testName);
                            else
                                res.append(failure.name);

                            FailureSummary recent = failure.histBaseBranch.recent;

                            if (recent != null) {
                                if (recent.failureRate != null) {
                                    res.append(" - ").append(recent.failureRate).append("% fails in last ")
                                        .append(MAX_LATEST_RUNS).append(" master runs.");
                                }
                                else if (recent.failures != null && recent.runs != null) {
                                    res.append(" - ").append(recent.failures).append(" fails / ")
                                        .append(recent.runs).append(" runs.");
                                }
                            }

                            res.append("\\n");
                        }

                        res.append("\\n");
                    }
                }

                if (res.length() > 0) {
                    res.insert(0, "{panel:title=Possible Blockers|" +
                        "borderStyle=dashed|borderColor=#ccc|titleBGColor=#F7D6C1}\\n")
                        .append("{panel}");
                }
                else {
                    res.append("{panel:title=No blockers found!|" +
                        "borderStyle=dashed|borderColor=#ccc|titleBGColor=#D6F7C1}{panel}");
                }
            }
        }

        res.append("\\n").append("[TeamCity Run All|").append(webUrl).append(']');

        return xmlEscapeText(res.toString());
    }

    /**
     * @param srv Server.
     * @return Failures for given server.
     */
    private Map<String, List<SuiteCurrentStatus>> findFailures(ChainAtServerCurrentStatus srv) {
        Map<String, List<SuiteCurrentStatus>> fails = new LinkedHashMap<>();

        fails.put("compilation", new ArrayList<>());
        fails.put("timeout", new ArrayList<>());
        fails.put("exit code", new ArrayList<>());
        fails.put("failed tests", new ArrayList<>());

        for (SuiteCurrentStatus suite : srv.suites) {
            String suiteRes = suite.result.toLowerCase();
            String failType = null;

            if (suiteRes.contains("compilation"))
                failType = "compilation";

            if (suiteRes.contains("timeout"))
                failType = "timeout";

            if (suiteRes.contains("exit code"))
                failType = "exit code";

            if (failType == null) {
                List<TestFailure> failures = new ArrayList<>();

                for (TestFailure testFailure : suite.testFailures) {
                    if (testFailure.isNewFailedTest())
                        failures.add(testFailure);
                }

                if (!failures.isEmpty()) {
                    suite.testFailures = failures;

                    failType = "failed tests";
                }
            }

            if (failType != null)
                fails.get(failType).add(suite);
        }

        return fails;
    }

    public void close() {
        if (stop.compareAndSet(false, true)) {
            tcUpdatePool.stop();

            detector.stop();

            buildObserver.stop();
        }
    }

    public ExecutorService getService() {
        return tcUpdatePool.getService();
    }
}
