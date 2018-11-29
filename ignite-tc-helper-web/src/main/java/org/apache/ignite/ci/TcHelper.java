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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import org.apache.ignite.ci.conf.BranchesTracked;
import org.apache.ignite.ci.issue.IssueDetector;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.tcbot.chain.PrChainsProcessor;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.web.model.JiraCommentResponse;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.hist.FailureSummary;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.ci.analysis.RunStat.MAX_LATEST_RUNS;
import static org.apache.ignite.ci.util.XmlUtil.xmlEscapeText;

/**
 * TC Bot implementation. To be migrated to smaller injected classes
 */
@Deprecated
public class TcHelper implements ITcHelper {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TcHelper.class);

    /** Stop guard. */
    private AtomicBoolean stop = new AtomicBoolean();

    /** Server authorizer credentials. */
    private ICredentialsProv serverAuthorizerCreds;

    @Inject private IssuesStorage issuesStorage;

    @Inject private ITcServerProvider serverProvider;

    @Inject private IssueDetector detector;

    @Inject private UserAndSessionsStorage userAndSessionsStorage;

    @Inject private PrChainsProcessor prChainsProcessor;

    @Inject private ITeamcityIgnitedProvider tcProv;

    @Inject private IStringCompactor compactor;

    /** */
    private final ObjectMapper objectMapper;

    public TcHelper() {
        objectMapper = new ObjectMapper();
    }

    /** {@inheritDoc} */
    @Override public void setServerAuthorizerCreds(ICredentialsProv creds) {
        this.serverAuthorizerCreds = creds;
    }

    /** {@inheritDoc} */
    @Override public ICredentialsProv getServerAuthorizerCreds() {
        return serverAuthorizerCreds;
    }

    /** {@inheritDoc} */
    @Override public boolean isServerAuthorized() {
        return !Objects.isNull(serverAuthorizerCreds);
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
    @Override public Visa notifyJira(
        String srvId,
        ICredentialsProv prov,
        String buildTypeId,
        String branchForTc,
        String ticket
    ) {
        IAnalyticsEnabledTeamcity teamcity = server(srvId, prov);

        ITeamcityIgnited tcIgnited = tcProv.server(srvId, prov);

        List<Integer> builds = tcIgnited.getLastNBuildsFromHistory(buildTypeId, branchForTc, 1);

        if (builds.isEmpty())
            return new Visa("JIRA wasn't commented - no finished builds to analyze.");

        Integer buildId = builds.get(0);

        FatBuildCompacted fatBuild = tcIgnited.getFatBuild(buildId);
        Build build = fatBuild.toBuild(compactor);

        build.webUrl = tcIgnited.host() + "viewLog.html?buildId=" + build.getId() + "&buildTypeId=" + build.buildTypeId;

        int blockers;

        JiraCommentResponse res;

        try {
            List<SuiteCurrentStatus> suitesStatuses = prChainsProcessor.getSuitesStatuses(buildTypeId, build.branchName, srvId, prov);
            if (suitesStatuses == null)
                return new Visa("JIRA wasn't commented - no finished builds to analyze.");

            String comment = generateJiraComment(suitesStatuses, build.webUrl, buildTypeId, tcIgnited);

            blockers = suitesStatuses.stream()
                .mapToInt(suite -> {
                    if (suite.testFailures.isEmpty())
                        return 1;

                    return suite.testFailures.size();
                })
                .sum();

            res = objectMapper.readValue(teamcity.sendJiraComment(ticket, comment), JiraCommentResponse.class);
        }
        catch (Exception e) {
            String errMsg = "Exception happened during commenting JIRA ticket " +
                "[build=" + build.getId() + ", errMsg=" + e.getMessage() + ']';

            logger.error(errMsg);

            return new Visa("JIRA wasn't commented - " + errMsg);
        }

        return new Visa(IJiraIntegration.JIRA_COMMENTED, res, blockers);
    }


    /**
     * @param suites Suite Current Status.
     * @param webUrl Build URL.
     * @return Comment, which should be sent to the JIRA ticket.
     */
    private String generateJiraComment(List<SuiteCurrentStatus> suites, String webUrl, String buildTypeId, ITeamcityIgnited tcIgnited) {
        BuildTypeRefCompacted bt = tcIgnited.getBuildTypeRef(buildTypeId);

        String suiteName = (bt != null ? bt.name(compactor) : buildTypeId);

        StringBuilder res = new StringBuilder();

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
                            .append(recent.runs).append(" master runs.");
                    }
                    else if (recent.failures != null && recent.runs != null) {
                        res.append(" - ").append(recent.failures).append(" fails / ")
                            .append(recent.runs).append(" master runs.");
                    }
                }

                res.append("\\n");
            }

            res.append("\\n");
        }

        if (res.length() > 0) {
            res.insert(0, "{panel:title=" + suiteName + ": Possible Blockers|" +
                "borderStyle=dashed|borderColor=#ccc|titleBGColor=#F7D6C1}\\n")
                .append("{panel}");
        }
        else {
            res.append("{panel:title=").append(suiteName).append(": No blockers found!|")
                .append("borderStyle=dashed|borderColor=#ccc|titleBGColor=#D6F7C1}{panel}");
        }

        res.append("\\n").append("[TeamCity *").append(suiteName).append("* Results|").append(webUrl).append(']');

        return xmlEscapeText(res.toString());
    }

    public void close() {
        if (stop.compareAndSet(false, true))
            detector.stop();
    }

}
