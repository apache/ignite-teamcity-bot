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
package org.apache.ignite.ci.teamcity.ignited;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.teamcity.pure.ITeamcityHttpConnection;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class IgnitedTcInMemoryIntegrationTest {

    public static final String APACHE = "apache";

    @Test
    public void saveAndLoadBuildReference() throws IOException {
        Ignite ignite = Ignition.start();

        ITeamcityHttpConnection http = Mockito.mock(ITeamcityHttpConnection.class);

        when(http.sendGet(anyString(), anyString())).thenAnswer(
            (invocationOnMock)->{
                return this.getClass().getResourceAsStream("/buildHistoryMaster.xml");
            }
        );

        try {
            TeamcityIgnitedModule module = new TeamcityIgnitedModule();

            module.overrideHttp(http);

            Injector injector = Guice.createInjector(module, new AbstractModule() {
                @Override protected void configure() {
                    bind(Ignite.class).toInstance(ignite);
                    bind(IScheduler.class).toInstance(Mockito.mock(IScheduler.class));
                }
            });

            ICredentialsProv mock = Mockito.mock(ICredentialsProv.class);
            ITeamcityIgnited srv = injector.getInstance(ITeamcityIgnitedProvider.class).server(APACHE, mock);

            assertTrue(srv.getBuildHistory("IgniteTests24Java8_RunAll", "<default>").isEmpty());

            TeamcityIgnitedImpl server1 = (TeamcityIgnitedImpl)srv;
            server1.runAtualizeBuilds(APACHE, true);

            List<BuildRef> history = srv.getBuildHistory("IgniteTests24Java8_RunAll", "<default>");

            assertTrue(history.isEmpty());
        }
        finally {
            ignite.close();
        }
    }
}
