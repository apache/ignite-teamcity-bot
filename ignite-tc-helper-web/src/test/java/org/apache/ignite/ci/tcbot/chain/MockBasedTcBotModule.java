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
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.githubignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.jiraignited.IJiraIgnited;
import org.apache.ignite.jiraignited.IJiraIgnitedProvider;
import org.apache.ignite.jiraservice.IJiraIntegration;
import org.apache.ignite.jiraservice.IJiraIntegrationProvider;
import org.apache.ignite.tcbot.engine.conf.CleanerConfig;
import org.apache.ignite.tcbot.engine.conf.ICleanerConfig;
import org.apache.ignite.tcbot.engine.conf.TcBotJsonConfig;
import org.apache.ignite.githubservice.IGitHubConnection;
import org.apache.ignite.githubservice.IGitHubConnectionProvider;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranchesConfig;
import org.apache.ignite.tcbot.engine.conf.NotificationsConfig;
import org.apache.ignite.tcbot.engine.conf.TcServerConfig;
import org.apache.ignite.tcbot.engine.issue.IIssuesStorage;
import org.apache.ignite.tcbot.engine.newtests.NewTestsStorage;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
import org.apache.ignite.tcbot.notify.IEmailSender;
import org.apache.ignite.tcbot.notify.ISlackSender;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcbot.persistence.InMemoryStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedProviderMock;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;
import org.apache.ignite.tcignited.buildlog.IBuildLogProcessor;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Setup TC bot context with Ignited services mocks: - TC: {@link TeamcityIgnitedProviderMock}
 */
public class MockBasedTcBotModule extends AbstractModule {
    private TcBotJsonConfig tracked = new TcBotJsonConfig();

    public MockBasedTcBotModule(TcBotJsonConfig tracked) {
        this.tracked = tracked;
    }

    public MockBasedTcBotModule() {

    }

    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(IStringCompactor.class).to(InMemoryStringCompactor.class).in(new SingletonScope());

        bind(IBuildLogProcessor.class).toInstance(Mockito.mock(IBuildLogProcessor.class));

        final IGitHubConnectionProvider ghProv = Mockito.mock(IGitHubConnectionProvider.class);
        bind(IGitHubConnectionProvider.class).toInstance(ghProv);
        when(ghProv.server(anyString())).thenReturn(Mockito.mock(IGitHubConnection.class));

        final IGitHubConfig ghCfg = mock(IGitHubConfig.class);
        mockGitHub(ghCfg);

        IJiraServerConfig jiraCfg = mock(IJiraServerConfig.class);
        mockJira(jiraCfg);

        bind(ITeamcityIgnitedProvider.class).to(TeamcityIgnitedProviderMock.class).in(new SingletonScope());

        final ITcBotConfig cfg = new ITcBotConfig() {
            @Override public String primaryServerCode() {
                return ITcBotConfig.DEFAULT_SERVER_CODE;
            }

            @Override public Integer flakyRate() {
                return DEFAULT_FLAKY_RATE;
            }

            @Override public Double confidence() {
                return DEFAULT_CONFIDENCE;
            }

            /** */
            @Override public Boolean alwaysFailedTestDetection() {
                return false;
            }

            @Override  public ITrackedBranchesConfig getTrackedBranches() {
                return tracked;
            }

            /** {@inheritDoc} */
            @Override public ITcServerConfig getTeamcityConfig(String srvCode) {
                return new TcServerConfig().code(srvCode);
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

            @Override public ICleanerConfig getCleanerConfig() {
                return CleanerConfig.getDefaultCleanerConfig();
            }
        };
        bind(ITcBotConfig.class).toInstance(cfg);
        bind(IDataSourcesConfigSupplier.class).toInstance(cfg);

        bind(IIssuesStorage.class).toInstance(Mockito.mock(IIssuesStorage.class));
        bind(IUserStorage.class).toInstance(Mockito.mock(IUserStorage.class));

        bind(IEmailSender.class).toInstance(Mockito.mock(IEmailSender.class));
        bind(ISlackSender.class).toInstance(Mockito.mock(ISlackSender.class));

        bind(Ignite.class).toInstance(Mockito.mock(Ignite.class));
        bind(NewTestsStorage.class).toInstance(Mockito.mock(NewTestsStorage.class));

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
