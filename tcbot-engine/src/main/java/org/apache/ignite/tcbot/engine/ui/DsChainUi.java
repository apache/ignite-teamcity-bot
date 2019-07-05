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

package org.apache.ignite.tcbot.engine.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.ignite.internal.util.typedef.T2;
import org.apache.ignite.tcbot.common.util.CollectionUtil;
import org.apache.ignite.tcbot.common.util.UrlUtil;
import org.apache.ignite.tcbot.engine.chain.FullChainRunCtx;
import org.apache.ignite.tcbot.engine.chain.MultBuildRunCtx;
import org.apache.ignite.tcbot.engine.chain.TestCompactedMult;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcservice.model.conf.BuildType;

import static org.apache.ignite.tcbot.engine.ui.DsSuiteUi.branchForLink;
import static org.apache.ignite.tcbot.engine.ui.DsSuiteUi.createOccurForLogConsumer;
import static org.apache.ignite.tcbot.engine.ui.DsSuiteUi.createOrrucForLongRun;

/**
 * Detailed status of PR or tracked branch for chain.
 *
 * Represent Run All chain results/ or RunAll+latest re-runs.
 *
 * Persisted as part of cached result. Renaming require background updater migration.
 */
@SuppressWarnings({"WeakerAccess", "PublicField"})
public class DsChainUi {
    /** {@link BuildType#getName()} */
    public String chainName;

    /** Server ID. */
    @Deprecated
    public final String serverId;

    /** General server (service) code: JIRA, GH, TC. But if TC aliased {@link #tcServerCode} is used for TC. */
    public final String serverCode;

    /** Teamcity connection server (service) code. Same with {@link #serverCode} */
    public final String tcServerCode;

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
    public List<DsSuiteUi> suites = new ArrayList<>();

    /** Count of failed tests not muted tests. In case several runs are used, overall by all runs. */
    public Integer failedTests;

    /** Total (Not Muted/Not Ignored) tests in all suites. */
    public Integer totalTests;

    /** Tests which will be considered as a blocker. */
    public Integer trustedTests;

    /** Count of suites with critical build problems found */
    public Integer failedToFinish;

    /** Duration printable. */
    public String durationPrintable;

    /** Duration net time printable. */
    public String durationNetTimePrintable;

    public String sourceUpdateDurationPrintable;

    public String artifcactPublishingDurationPrintable;

    public String dependeciesResolvingDurationPrintable;

    /** Tests duration printable. */
    public String testsDurationPrintable;

    /** Timed out builds average time. */
    public String lostInTimeouts;

    /** top long running suites */
    public List<DsTestFailureUi> topLongRunning = new ArrayList<>();

    /** top log data producing tests . */
    public List<DsTestFailureUi> logConsumers = new ArrayList<>();

    /** Special flag if chain entry point not found */
    public boolean buildNotFound;

    @Nullable public String baseBranchForTc;

    /** Total blockers count. */
    public int totalBlockers;

    /**
     * @param srvCode Server code.
     * @param tcSvcCode Tc service code.
     * @param branchTc Branch tc.
     */
    public DsChainUi(String srvCode, String tcSvcCode, String branchTc) {
        this.serverCode = srvCode;
        this.tcServerCode = tcSvcCode;
        this.serverId = tcSvcCode;
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




    public void initFromContext(ITeamcityIgnited tcIgnited,
        FullChainRunCtx ctx,
        @Nullable String baseBranchTc,
        IStringCompactor compactor,
        boolean calcTrustedTests) {
        failedTests = 0;
        failedToFinish = 0;
        totalTests = 0;
        trustedTests = 0;
        //todo mode with not failed
        Stream<MultBuildRunCtx> stream = ctx.failedChildSuites();

        stream.forEach(
            suite -> {
                final DsSuiteUi suiteCurStatus = new DsSuiteUi();

                suiteCurStatus.initFromContext(tcIgnited, suite, baseBranchTc, compactor, true, calcTrustedTests);

                failedTests += suiteCurStatus.failedTests != null ? suiteCurStatus.failedTests : 0;
                totalTests += suiteCurStatus.totalTests != null ? suiteCurStatus.totalTests : 0;
                trustedTests += suiteCurStatus.trustedTests != null ? suiteCurStatus.trustedTests : 0;
                if (suite.hasAnyBuildProblemExceptTestOrSnapshot() || suite.onlyCancelledBuilds())
                    failedToFinish++;

                suites.add(suiteCurStatus);
            }
        );

        if(calcTrustedTests) {
            //todo odd convertion
            ctx.suites().filter(s -> !s.isFailed()).forEach(suite -> {

                final DsSuiteUi suiteCurStatus = new DsSuiteUi();

                suiteCurStatus.initFromContext(tcIgnited, suite, baseBranchTc, compactor, true, calcTrustedTests);

                totalTests += suiteCurStatus.totalTests != null ? suiteCurStatus.totalTests : 0;
                trustedTests += suiteCurStatus.trustedTests != null ? suiteCurStatus.trustedTests : 0;
            });
        }

        totalBlockers = suites.stream().mapToInt(DsSuiteUi::totalBlockers).sum();
        durationPrintable = ctx.getDurationPrintable();
        testsDurationPrintable = ctx.getTestsDurationPrintable();
        durationNetTimePrintable = ctx.durationNetTimePrintable();
        sourceUpdateDurationPrintable = ctx.sourceUpdateDurationPrintable();
        artifcactPublishingDurationPrintable = ctx.artifcactPublishingDurationPrintable();
        dependeciesResolvingDurationPrintable = ctx.dependeciesResolvingDurationPrintable();
        lostInTimeouts = ctx.getLostInTimeoutsPrintable();
        webToHist = buildWebLink(tcIgnited, ctx);
        webToBuild = buildWebLinkToBuild(tcIgnited, ctx);

        Stream<T2<MultBuildRunCtx, TestCompactedMult>> allLongRunning = ctx.suites().flatMap(
            suite -> suite.getTopLongRunning().map(t -> new T2<>(suite, t))
        );
        Comparator<T2<MultBuildRunCtx, TestCompactedMult>> durationComp
            = Comparator.comparing((pair) -> pair.get2().getAvgDurationMs());

        CollectionUtil.top(allLongRunning, 3, durationComp).forEach(
            pairCtxAndOccur -> {
                MultBuildRunCtx suite = pairCtxAndOccur.get1();
                TestCompactedMult longRunningOccur = pairCtxAndOccur.get2();

                DsTestFailureUi failure = createOrrucForLongRun(tcIgnited, compactor, suite, longRunningOccur, baseBranchTc);

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

                DsTestFailureUi failure = createOccurForLogConsumer(testLogConsuming);

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
            + "&branch=" + UrlUtil.escape(branch)
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
        DsChainUi status = (DsChainUi)o;
        return buildNotFound == status.buildNotFound &&
            Objects.equals(chainName, status.chainName) &&
            Objects.equals(serverId, status.serverId) &&
            Objects.equals(serverCode, status.serverCode) &&
            Objects.equals(tcServerCode, status.tcServerCode) &&
            Objects.equals(branchName, status.branchName) &&
            Objects.equals(webToHist, status.webToHist) &&
            Objects.equals(webToBuild, status.webToBuild) &&
            Objects.equals(ticketFullName, status.ticketFullName) &&
            Objects.equals(webToTicket, status.webToTicket) &&
            Objects.equals(prNum, status.prNum) &&
            Objects.equals(webToPr, status.webToPr) &&
            Objects.equals(suites, status.suites) &&
            Objects.equals(failedTests, status.failedTests) &&
            Objects.equals(failedToFinish, status.failedToFinish) &&
            Objects.equals(durationPrintable, status.durationPrintable) &&
            Objects.equals(durationNetTimePrintable, status.durationNetTimePrintable) &&
            Objects.equals(sourceUpdateDurationPrintable, status.sourceUpdateDurationPrintable) &&
            Objects.equals(artifcactPublishingDurationPrintable, status.artifcactPublishingDurationPrintable) &&
            Objects.equals(dependeciesResolvingDurationPrintable, status.dependeciesResolvingDurationPrintable) &&
            Objects.equals(testsDurationPrintable, status.testsDurationPrintable) &&
            Objects.equals(lostInTimeouts, status.lostInTimeouts) &&
            Objects.equals(topLongRunning, status.topLongRunning) &&
            Objects.equals(logConsumers, status.logConsumers) &&
            Objects.equals(baseBranchForTc, status.baseBranchForTc);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(chainName, serverId, serverCode, tcServerCode, branchName, webToHist, webToBuild,
            ticketFullName, webToTicket, prNum, webToPr, suites, failedTests, failedToFinish, durationPrintable,
            durationNetTimePrintable,  sourceUpdateDurationPrintable, artifcactPublishingDurationPrintable,
            dependeciesResolvingDurationPrintable,  testsDurationPrintable, lostInTimeouts, topLongRunning,
            logConsumers, buildNotFound, baseBranchForTc);
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
