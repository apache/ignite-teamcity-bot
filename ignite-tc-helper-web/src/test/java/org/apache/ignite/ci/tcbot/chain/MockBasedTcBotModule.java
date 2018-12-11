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
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnited;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.github.pure.IGitHubConnectionProvider;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.jira.IJiraIntegrationProvider;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.InMemoryStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedProviderMock;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Setup TC bot context with Ignited services mocks:
 * - TC: {@link TeamcityIgnitedProviderMock}
 */
public class MockBasedTcBotModule extends AbstractModule {
    /** {@inheritDoc} */
    @Override protected void configure() {
        bind(IStringCompactor.class).to(InMemoryStringCompactor.class).in(new SingletonScope());

        final IGitHubConnectionProvider ghProv = Mockito.mock(IGitHubConnectionProvider.class);
        bind(IGitHubConnectionProvider.class).toInstance(ghProv);
        when(ghProv.server(anyString())).thenReturn(Mockito.mock(IGitHubConnection.class));

        final IGitHubConnIgnitedProvider gitHubConnIgnitedProvider = Mockito.mock(IGitHubConnIgnitedProvider.class);

        bind(IGitHubConnIgnitedProvider.class).toInstance(gitHubConnIgnitedProvider);

        IGitHubConnIgnited gitHubConnIgnited = Mockito.mock(IGitHubConnIgnited.class);

        PullRequest pullReq = Mockito.mock(PullRequest.class);

        when(pullReq.getTitle()).thenReturn("");

        when(gitHubConnIgnited.getPullRequest(anyString())).thenReturn(pullReq);

        when(gitHubConnIgnitedProvider.server(anyString())).thenReturn(gitHubConnIgnited);

        final IJiraIntegrationProvider jiraProv = Mockito.mock(IJiraIntegrationProvider.class);

        bind(IJiraIntegrationProvider.class).toInstance(jiraProv);

        when(jiraProv.server(anyString())).thenReturn(Mockito.mock(IJiraIntegration.class));

        bind(ITeamcityIgnitedProvider.class).to(TeamcityIgnitedProviderMock.class).in(new SingletonScope());

        final ITcServerProvider tcSrvOldProv = Mockito.mock(ITcServerProvider.class);

        final IAnalyticsEnabledTeamcity tcOld = BuildChainProcessorTest.tcOldMock();
        when(tcSrvOldProv.server(anyString(), any(ICredentialsProv.class))).thenReturn(tcOld);

        bind(ITcServerProvider.class).toInstance(tcSrvOldProv);

        super.configure();
    }
}
