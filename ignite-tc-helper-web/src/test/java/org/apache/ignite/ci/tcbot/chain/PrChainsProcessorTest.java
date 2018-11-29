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
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.github.pure.IGitHubConnectionProvider;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestRef;
import org.apache.ignite.ci.teamcity.ignited.*;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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

            final IAnalyticsEnabledTeamcity tcOld = BuildChainProcessorTest.tcOldMock();
            when(tcSrvOldProv.server(anyString(), any(ICredentialsProv.class))).thenReturn(tcOld);


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

        final String btId = "RunAll";
        final String branch = "ignite-9542";

        initBuildChain(c, btId, branch);

        PrChainsProcessor prcp = injector.getInstance(PrChainsProcessor.class);
        final List<SuiteCurrentStatus> suitesStatuses = prcp.getSuitesStatuses(btId,
                branch, SRV_ID, mock(ICredentialsProv.class));

        assertNotNull(suitesStatuses);
        assertFalse(suitesStatuses.isEmpty());

        assertTrue(suitesStatuses.stream().anyMatch(
                s -> s.testFailures.stream().anyMatch(testFailure -> "testWithoutHistory".equals(testFailure.name))
        ));

        assertTrue(suitesStatuses.stream().anyMatch(s -> "Build".equals(s.suiteId)));
        assertTrue(suitesStatuses.stream().anyMatch(s -> "CancelledBuild".equals(s.suiteId)));
    }

    private void initBuildChain(IStringCompactor c, String btId, String branch) {
        final int id = 1000;

        final FatBuildCompacted chain = createFailedBuild(c, btId, branch, id, 100000);

        final FatBuildCompacted childBuild = createFailedBuild(c, "Cache1", branch, 1001, 100020);
        TestOccurrenceFull tf = new TestOccurrenceFull();
        tf.test = new TestRef();
        tf.test.id = 1L;
        tf.name = "testWithoutHistory";
        tf.status = TestOccurrence.STATUS_FAILURE;

        childBuild.addTests(c, Collections.singletonList(tf));


        final FatBuildCompacted buildBuild = createFailedBuild(c, "Build", branch, 1002, 100020);
        final ProblemOccurrence compile = new ProblemOccurrence();
        compile.setType(ProblemOccurrence.TC_COMPILATION_ERROR);
        buildBuild.addProblems(c, Collections.singletonList(compile));
        childBuild.snapshotDependencies(new int[]{buildBuild.id()});

        final Build build = createBuild("CancelledBuild", branch, 1003, 100020);
        build.status = BuildRef.STATUS_UNKNOWN;
        build.state = BuildRef.STATE_FINISHED;

        final FatBuildCompacted cancelledBuild = new FatBuildCompacted(c, build);


        chain.snapshotDependencies(new int[]{childBuild.id(), cancelledBuild.id()});

        apacheBuilds.put(chain.id(), chain);
        apacheBuilds.put(childBuild.id(), childBuild);
        apacheBuilds.put(buildBuild.id(), buildBuild);
        apacheBuilds.put(cancelledBuild.id(), cancelledBuild);
    }

    @NotNull
    public FatBuildCompacted createFailedBuild(IStringCompactor c, String btId, String branch, int id, int ageMs) {
        final Build build = createBuild(btId, branch, id, ageMs);
        build.status = BuildRef.STATUS_FAILURE;

        return new FatBuildCompacted(c, build);
    }

    @NotNull
    public Build createBuild(String btId, String branch, int id, int ageMs) {
        final Build build = new Build();
        build.buildTypeId = btId;
        final BuildType type = new BuildType();
        type.setId(btId);
        type.setName(btId);
        build.setBuildType(type);
        build.setId(id);
        build.setStartDateTs(System.currentTimeMillis() - ageMs);
        build.setBranchName(branch);
        return build;
    }
}
