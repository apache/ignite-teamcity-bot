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
import com.google.gson.Gson;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import org.apache.ignite.ci.jira.Tickets;
import org.apache.ignite.ci.tcbot.issue.IssueDetector;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.jira.pure.IJiraIntegration;
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

import static org.apache.ignite.ci.util.XmlUtil.xmlEscapeText;

/**
 * TC Bot implementation. To be migrated to smaller injected classes
 */
@Deprecated
public class TcHelper implements ITcHelper {
    /** Stop guard. */
    private AtomicBoolean stop = new AtomicBoolean();

    /** Server authorizer credentials. */
    private ICredentialsProv serverAuthorizerCreds;

    @Inject private IssuesStorage issuesStorage;

    @Inject private ITcServerProvider serverProvider;

    @Inject private IssueDetector detector;

    @Inject private UserAndSessionsStorage userAndSessionsStorage;


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
    @Override public UserAndSessionsStorage users() {
        return userAndSessionsStorage;
    }

    @Override public String primaryServerId() {
        return "apache"; //todo remove
    }



    public void close() {
        if (stop.compareAndSet(false, true))
            detector.stop();
    }

}
