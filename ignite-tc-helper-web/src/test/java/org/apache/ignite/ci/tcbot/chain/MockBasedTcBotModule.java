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

package org.apache.ignite.ci.tcbot.chain;

import com.google.inject.AbstractModule;
import com.google.inject.internal.SingletonScope;
import java.io.File;
import java.util.Properties;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnited;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.github.pure.IGitHubConnectionProvider;
import org.apache.ignite.ci.jira.ignited.IJiraIgnited;
import org.apache.ignite.ci.jira.ignited.IJiraIgnitedProvider;
import org.apache.ignite.ci.jira.pure.IJiraIntegration;
import org.apache.ignite.ci.jira.pure.IJiraIntegrationProvider;
import org.apache.ignite.ci.tcbot.conf.BranchesTracked;
import org.apache.ignite.ci.tcbot.conf.IGitHubConfig;
import org.apache.ignite.ci.tcbot.conf.IJiraServerConfig;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.tcbot.conf.ITcServerConfig;
import org.apache.ignite.ci.tcbot.conf.NotificationsConfig;
import org.apache.ignite.ci.tcbot.conf.TcServerConfig;
import org.apache.ignite.ci.tcbot.issue.IIssuesStorage;
import org.apache.ignite.ci.tcbot.user.IUserStorage;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.InMemoryStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedProviderMock;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Setup TC bot context with Ignited services mocks: - TC: {@link TeamcityIgnitedProviderMock}
 */
public class MockBasedTcBotModule extends AbstractModule {
    private BranchesTracked tracked = new BranchesTracked();

    public MockBasedTcBotModule(BranchesTracked tracked) {
        this.tracked = tracked;
    }

    public MockBasedTcBotModule() {

    }

    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(IStringCompactor.class).to(InMemoryStringCompactor.class).in(new SingletonScope());

        final IGitHubConnectionProvider ghProv = Mockito.mock(IGitHubConnectionProvider.class);
        bind(IGitHubConnectionProvider.class).toInstance(ghProv);
        when(ghProv.server(anyString())).thenReturn(Mockito.mock(IGitHubConnection.class));

        final IGitHubConfig ghCfg = mock(IGitHubConfig.class);
        mockGitHub(ghCfg);

        IJiraServerConfig jiraCfg = mock(IJiraServerConfig.class);
        mockJira(jiraCfg);

        bind(ITeamcityIgnitedProvider.class).to(TeamcityIgnitedProviderMock.class).in(new SingletonScope());

        final ITcServerProvider tcSrvOldProv = Mockito.mock(ITcServerProvider.class);

        final IAnalyticsEnabledTeamcity tcOld = BuildChainProcessorTest.tcOldMock();
        when(tcSrvOldProv.server(anyString(), any(ICredentialsProv.class))).thenReturn(tcOld);

        bind(ITcServerProvider.class).toInstance(tcSrvOldProv);

        bind(ITcBotConfig.class).toInstance(new ITcBotConfig() {
            @Override public String primaryServerCode() {
                return ITcBotConfig.DEFAULT_SERVER_CODE;
            }

            @Override public BranchesTracked getTrackedBranches() {
                return tracked;
            }

            /** {@inheritDoc} */
            @Override public ITcServerConfig getTeamcityConfig(String srvCode) {
                return new TcServerConfig().code(srvCode).properties(loadOldProps(srvCode));
            }

            @Override public IJiraServerConfig getJiraConfig(String srvCode) {
                return jiraCfg;
            }

            @Override public IGitHubConfig getGitConfig(String srvCode) {
                return ghCfg;
            }

            @Override public NotificationsConfig notifications() {
                return new NotificationsConfig();
            }

            private Properties loadOldProps(String srvCode) {
                File workDir = HelperConfig.resolveWorkDir();

                String cfgName = HelperConfig.prepareConfigName(srvCode);

                return HelperConfig.loadAuthProperties(workDir, cfgName);
            }
        });

        bind(IIssuesStorage.class).toInstance(Mockito.mock(IIssuesStorage.class));
        bind(IUserStorage.class).toInstance(Mockito.mock(IUserStorage.class));

        super.configure();
    }

    private void mockGitHub(IGitHubConfig ghCfg) {
        final IGitHubConnIgnitedProvider gitHubConnIgnitedProvider = Mockito.mock(IGitHubConnIgnitedProvider.class);

        bind(IGitHubConnIgnitedProvider.class).toInstance(gitHubConnIgnitedProvider);

        IGitHubConnIgnited gitHubConnIgnited = Mockito.mock(IGitHubConnIgnited.class);

        PullRequest pullReq = Mockito.mock(PullRequest.class);

        when(pullReq.getTitle()).thenReturn("");

        when(gitHubConnIgnited.getPullRequest(anyInt())).thenReturn(pullReq);

        when(gitHubConnIgnitedProvider.server(anyString())).thenReturn(gitHubConnIgnited);

        when(gitHubConnIgnited.config()).thenReturn(ghCfg);
    }

    /**
     * @param jiraCfg JIRA config.
     */
    private void mockJira(IJiraServerConfig jiraCfg) {
        final IJiraIntegrationProvider jiraProv = Mockito.mock(IJiraIntegrationProvider.class);

        bind(IJiraIntegrationProvider.class).toInstance(jiraProv);

        when(jiraProv.server(anyString())).thenReturn(Mockito.mock(IJiraIntegration.class));

        final IJiraIgnitedProvider jiraIgnProv = Mockito.mock(IJiraIgnitedProvider.class);

        bind(IJiraIgnitedProvider.class).toInstance(jiraIgnProv);

        IJiraIgnited jiraIgn = Mockito.mock(IJiraIgnited.class);

        when(jiraIgn.config()).thenReturn(jiraCfg);

        when(jiraIgnProv.server(anyString())).thenReturn(jiraIgn);
    }
}
