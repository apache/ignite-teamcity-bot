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

package org.apache.ignite.ci.web.model.current;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.IMultTestOccurrence;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnited;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.jira.pure.IJiraIntegration;
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.util.CollectionUtil;
import org.apache.ignite.internal.util.typedef.T2;

import static org.apache.ignite.ci.util.UrlUtil.escape;
import static org.apache.ignite.ci.web.model.current.SuiteCurrentStatus.branchForLink;
import static org.apache.ignite.ci.web.model.current.SuiteCurrentStatus.createOccurForLogConsumer;
import static org.apache.ignite.ci.web.model.current.SuiteCurrentStatus.createOrrucForLongRun;

/**
 * Represent Run All chain results/ or RunAll+latest re-runs.
 *
 * Persisted as part of cached result. Renaming require background updater migration.
 */
@SuppressWarnings({"WeakerAccess", "PublicField"})
public class ChainAtServerCurrentStatus {
    /** {@link BuildType#name} */
    public String chainName;

    /** Server ID. */
    public final String serverId;

    /** Branch name in teamcity identification. */
    public final String branchName;

    /** Web Href. to suite runs history. */
    public String webToHist = "";

    /** Web Href. to suite particular run */
    public String webToBuild = "";

    /** Filled only for compare page (e.g. pr.html). */
    public String ticketFullName;

    /** */
    public String webToTicket;

    /** */
    public Integer prNum;

    /** */
    public String webToPr;

    /** Suites involved in chain. */
    public List<SuiteCurrentStatus> suites = new ArrayList<>();

    public Integer failedTests;
    /** Count of suites with critical build problems found */
    public Integer failedToFinish;

    /** Duration printable. */
    public String durationPrintable;

    /** Tests duration printable. */
    public String testsDurationPrintable;

    /** Timed out builds average time. */
    public String lostInTimeouts;

    /** top long running suites */
    public List<TestFailure> topLongRunning = new ArrayList<>();

    /** top log data producing tests . */
    public List<TestFailure> logConsumers = new ArrayList<>();

    /** Special flag if chain entry point not found */
    public boolean buildNotFound;

    @Nullable public String baseBranchForTc;

    public ChainAtServerCurrentStatus(String srvId, String branchTc) {
        this.serverId = srvId;
        this.branchName = branchTc;
    }

    /** */
    public void setJiraTicketInfo(@Nullable String ticketFullName, @Nullable String webToTicket) {
        this.ticketFullName = ticketFullName;
        this.webToTicket = webToTicket;
    }

    /** */
    public void setPrInfo(@Nullable Integer prNum, @Nullable String webToPr) {
        this.prNum = prNum;
        this.webToPr = webToPr;
    }

    /** */
    public void initJiraAndGitInfo(ITeamcityIgnited tcIgnited,
        IJiraIntegration jiraIntegration, IGitHubConnIgnited gitHubConnIgnited) {
        Integer prNum = IGitHubConnection.convertBranchToId(branchName);

        String prUrl = null;

        String ticketFullName = null;

        String ticketUrl = null;

        String ticketPrefix = jiraIntegration.ticketPrefix();

        if (prNum != null) {
            PullRequest pullReq = gitHubConnIgnited.getPullRequest(prNum);

            if (pullReq != null && pullReq.getTitle() != null) {
                prUrl = pullReq.htmlUrl();

                ticketFullName = TcBotTriggerAndSignOffService.getTicketFullName(pullReq, ticketPrefix);
            }
        }
        else
            ticketFullName = TcBotTriggerAndSignOffService.prLessTicket(branchName, ticketPrefix, gitHubConnIgnited);

        if (!Strings.isNullOrEmpty(ticketFullName))
            ticketUrl = jiraIntegration.generateTicketUrl(ticketFullName);

        setPrInfo(prNum, prUrl);
        setJiraTicketInfo(ticketFullName, ticketUrl);
    }

    public void initFromContext(ITeamcityIgnited tcIgnited,
        FullChainRunCtx ctx,
        @Nullable String baseBranchTc) {
        failedTests = 0;
        failedToFinish = 0;
        //todo mode with not failed
        Stream<MultBuildRunCtx> stream = ctx.failedChildSuites();

        stream.forEach(
            suite -> {
                final SuiteCurrentStatus suiteCurStatus = new SuiteCurrentStatus();

                suiteCurStatus.initFromContext(tcIgnited, suite, baseBranchTc);

                failedTests += suiteCurStatus.failedTests;
                if (suite.hasAnyBuildProblemExceptTestOrSnapshot() || suite.onlyCancelledBuilds())
                    failedToFinish++;

                this.suites.add(suiteCurStatus);
            }
        );
        durationPrintable = ctx.getDurationPrintable();
        testsDurationPrintable = ctx.getTestsDurationPrintable();
        lostInTimeouts = ctx.getLostInTimeoutsPrintable();
        webToHist = buildWebLink(tcIgnited, ctx);
        webToBuild = buildWebLinkToBuild(tcIgnited, ctx);

        Stream<T2<MultBuildRunCtx, IMultTestOccurrence>> allLongRunning = ctx.suites().flatMap(
            suite -> suite.getTopLongRunning().map(t -> new T2<>(suite, t))
        );
        Comparator<T2<MultBuildRunCtx, IMultTestOccurrence>> durationComp
            = Comparator.comparing((pair) -> pair.get2().getAvgDurationMs());

        CollectionUtil.top(allLongRunning, 3, durationComp).forEach(
            pairCtxAndOccur -> {
                MultBuildRunCtx suite = pairCtxAndOccur.get1();
                IMultTestOccurrence longRunningOccur = pairCtxAndOccur.get2();

                TestFailure failure = createOrrucForLongRun(tcIgnited, suite, longRunningOccur, baseBranchTc);

                failure.testName = "[" + suite.suiteName() + "] " + failure.testName; //may be separate field

                topLongRunning.add(failure);
            }
        );

        Stream<T2<MultBuildRunCtx, Map.Entry<String, Long>>> allLogConsumers = ctx.suites().flatMap(
            suite -> suite.getTopLogConsumers().map(t -> new T2<>(suite, t))
        );
        Comparator<T2<MultBuildRunCtx, Map.Entry<String, Long>>> longConsumingComp
            = Comparator.comparing((pair) -> pair.get2().getValue());

        CollectionUtil.top(allLogConsumers, 3, longConsumingComp).forEach(
            pairCtxAndOccur -> {
                MultBuildRunCtx suite = pairCtxAndOccur.get1();
                Map.Entry<String, Long> testLogConsuming = pairCtxAndOccur.get2();

                TestFailure failure = createOccurForLogConsumer(testLogConsuming);

                failure.name = "[" + suite.suiteName() + "] " + failure.name; //todo suite as be separate field

                logConsumers.add(failure);
            }
        );
    }

    private static String buildWebLinkToBuild(ITeamcityIgnited teamcity, FullChainRunCtx chain) {
        return teamcity.host() + "viewLog.html?buildId=" + chain.getSuiteBuildId();
    }

    private static String buildWebLink(ITeamcityIgnited teamcity, FullChainRunCtx suite) {
        final String branch = branchForLink(suite.branchName());
        return teamcity.host() + "viewType.html?buildTypeId=" + suite.suiteId()
            + "&branch=" + escape(branch)
            + "&tab=buildTypeStatusDiv";
    }

    /**
     * @return Server name.
     */
    public String serverName() {
        return serverId;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ChainAtServerCurrentStatus status = (ChainAtServerCurrentStatus)o;
        return Objects.equal(chainName, status.chainName) &&
            Objects.equal(serverId, status.serverId) &&
            Objects.equal(branchName, status.branchName) &&
            Objects.equal(webToHist, status.webToHist) &&
            Objects.equal(webToBuild, status.webToBuild) &&
            Objects.equal(suites, status.suites) &&
            Objects.equal(failedTests, status.failedTests) &&
            Objects.equal(failedToFinish, status.failedToFinish) &&
            Objects.equal(durationPrintable, status.durationPrintable) &&
            Objects.equal(testsDurationPrintable, status.testsDurationPrintable) &&
            Objects.equal(lostInTimeouts, status.lostInTimeouts) &&
            Objects.equal(logConsumers, status.logConsumers) &&
            Objects.equal(topLongRunning, status.topLongRunning) &&
            Objects.equal(buildNotFound, status.buildNotFound);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(chainName, serverId, branchName, webToHist, webToBuild, suites,
            failedTests, failedToFinish, durationPrintable, testsDurationPrintable,
            lostInTimeouts, logConsumers, topLongRunning, buildNotFound);
    }

    public void setBuildNotFound(boolean buildNotFound) {
        this.buildNotFound = buildNotFound;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("{").append(serverName()).append("}\n");
        suites.forEach(
            s -> builder.append(s.toString())
        );
        builder.append("\n");

        return builder.toString();
    }
}
