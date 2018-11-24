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
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.SingletonScope;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.github.pure.IGitHubConnectionProvider;
import org.apache.ignite.ci.teamcity.ignited.*;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PrChainsProcessorTest {
    public static final String SRV_ID = "apache";
    private Map<Integer, FatBuildCompacted> apacheBuilds = new ConcurrentHashMap<>();


    /**
     * Injector.
     */
    private Injector injector = Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
            bind(IStringCompactor.class).to(InMemoryStringCompactor.class).in(new SingletonScope());

            final IGitHubConnectionProvider ghProv = Mockito.mock(IGitHubConnectionProvider.class);
            bind(IGitHubConnectionProvider.class).toInstance(ghProv);
            when(ghProv.server(anyString())).thenReturn(Mockito.mock(IGitHubConnection.class));

            bind(ITeamcityIgnitedProvider.class).to(TeamcityIgnitedProviderMock.class).in(new SingletonScope());

            final ITcServerProvider tcSrvOldProv = Mockito.mock(ITcServerProvider.class);

            when(tcSrvOldProv.server(anyString(), any(ICredentialsProv.class)))
                    .thenReturn(Mockito.mock(IAnalyticsEnabledTeamcity.class));

            bind(ITcServerProvider.class).toInstance(tcSrvOldProv);

            super.configure();
        }
    });

    @Before
    public void initBuilds() {
        final TeamcityIgnitedProviderMock instance = (TeamcityIgnitedProviderMock) injector.getInstance(ITeamcityIgnitedProvider.class);
        instance.addServer(SRV_ID, apacheBuilds);
    }

    @Test
    public void testTestFailureWithoutStatReportedAsBlocker() {


        IStringCompactor c = injector.getInstance(IStringCompactor.class);
        PrChainsProcessor prcp = injector.getInstance(PrChainsProcessor.class);
        final List<SuiteCurrentStatus> suitesStatuses = prcp.getSuitesStatuses("RunAll",
                "ignite-9542", SRV_ID, mock(ICredentialsProv.class));

        assertNotNull(suitesStatuses);
        assertFalse(suitesStatuses.isEmpty());
    }
}
