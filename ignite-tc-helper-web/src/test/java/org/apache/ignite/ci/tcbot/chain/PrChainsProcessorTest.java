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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.SingletonScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnited;
import org.apache.ignite.ci.github.ignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.github.pure.IGitHubConnectionProvider;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.jira.IJiraIntegrationProvider;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestRef;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.InMemoryStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedProviderMock;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.InvocationData;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PrChainsProcessorTest {
    public static final String SRV_ID = "apache";
    public static final String TEST_WITH_HISTORY_FAILING_IN_MASTER = "testWithHistoryFailingInMaster";
    public static final String TEST_WITH_HISTORY_PASSING_IN_MASTER = "testWithHistoryPassingInMaster";
    public static final String TEST_FLAKY_IN_MASTER = "testFlaky50";
    public static final String TEST_WITHOUT_HISTORY = "testWithoutHistory";
    public static final String TEST_WAS_FIXED_IN_MASTER = "testFailingButFixedInMaster";
    public static final int NUM_OF_TESTS_IN_MASTER = 10;
    public static final String CACHE_1 = "Cache1";

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
        final List<SuiteCurrentStatus> blockers = prcp.getBlockersSuitesStatuses(btId,
                branch, SRV_ID, mock(ICredentialsProv.class));

        System.out.println(blockers);
        assertNotNull(blockers);
        assertFalse(blockers.isEmpty());

        assertTrue(blockers.stream().anyMatch(containsTestFail("testWithoutHistory")));

        assertTrue(blockers.stream().anyMatch(s -> "Build".equals(s.suiteId)));
        assertTrue(blockers.stream().anyMatch(s -> "CancelledBuild".equals(s.suiteId)));

        assertTrue(blockers.stream().anyMatch(containsTestFail(TEST_WITH_HISTORY_PASSING_IN_MASTER)));

        if (SuiteCurrentStatus.NEW_RUN_STAT) {
            assertFalse(blockers.stream().anyMatch(containsTestFail(TEST_WITH_HISTORY_FAILING_IN_MASTER)));
            assertFalse(blockers.stream().anyMatch(containsTestFail(TEST_FLAKY_IN_MASTER)));
        }

        Optional<SuiteCurrentStatus> suiteOpt = blockers.stream().filter(containsTestFail(TEST_WITH_HISTORY_PASSING_IN_MASTER)).findAny();
        assertTrue(suiteOpt.isPresent());
        Optional<TestFailure> testOpt = suiteOpt.get().testFailures.stream().filter(tf -> TEST_WITH_HISTORY_PASSING_IN_MASTER.equals(tf.name)).findAny();
        assertTrue(testOpt.isPresent());

        if (SuiteCurrentStatus.NEW_RUN_STAT) {
            List<Integer> etalon = new ArrayList<>();
            for (int i = 0; i < NUM_OF_TESTS_IN_MASTER; i++)
                etalon.add(RunStat.RunStatus.RES_OK.getCode());

            assertEquals(etalon, testOpt.get().histBaseBranch.latestRuns);
        }

        assertTrue(blockers.stream().anyMatch(containsTestFail(TEST_WAS_FIXED_IN_MASTER)));
        if(SuiteCurrentStatus.NEW_RUN_STAT)
            assertFalse(blockers.stream().anyMatch(containsTestFail(TEST_WITH_HISTORY_FAILING_IN_MASTER)));
        // otherwise this non-blocker will not be filtered out

        assertTrue(blockers.stream().anyMatch(containsTestFail(TEST_WITH_HISTORY_PASSING_IN_MASTER)));
    }

    @NotNull
    private Predicate<SuiteCurrentStatus> containsTestFail(String name) {
        return s -> s.testFailures.stream().anyMatch(testFailure -> {
            return name.equals(testFailure.name);
        });
    }

    public void initBuildChain(IStringCompactor c, String btId, String branch) {
        final FatBuildCompacted buildBuild = createFailedBuild(c, "Build", branch, 1002, 100020);
        final ProblemOccurrence compile = new ProblemOccurrence();
        compile.setType(ProblemOccurrence.TC_COMPILATION_ERROR);
        buildBuild.addProblems(c, Collections.singletonList(compile));

        final FatBuildCompacted childBuild =
            createFailedBuild(c, CACHE_1, branch, 1001, 100020)
                .addTests(c,
                    Lists.newArrayList(
                        createFailedTest(1L, TEST_WITHOUT_HISTORY),
                        createFailedTest(2L, TEST_WITH_HISTORY_FAILING_IN_MASTER),
                        createFailedTest(3L, TEST_WITH_HISTORY_PASSING_IN_MASTER),
                        createFailedTest(50L, TEST_FLAKY_IN_MASTER),
                        createFailedTest(400L, TEST_WAS_FIXED_IN_MASTER)));

        childBuild.snapshotDependencies(new int[] {buildBuild.id()});

        final Build build = createJaxbBuild("CancelledBuild", branch, 1003, 100020, true);

        build.status = BuildRef.STATUS_UNKNOWN;
        build.state = BuildRef.STATE_FINISHED;

        final FatBuildCompacted cancelledBuild = new FatBuildCompacted(c, build);

        cancelledBuild.snapshotDependencies(new int[]{buildBuild.id()});

        final int id = 1000;

        final FatBuildCompacted chain = createFailedBuild(c, btId, branch, id, 100000);

        chain.snapshotDependencies(new int[]{childBuild.id(), cancelledBuild.id()});

        addBuilds(chain);
        addBuilds(childBuild);
        addBuilds(buildBuild);
        addBuilds(cancelledBuild);

        for (int i = 0; i < NUM_OF_TESTS_IN_MASTER; i++) {
            FatBuildCompacted cache1InMaster = createFailedBuild(c, CACHE_1,
                ITeamcity.DEFAULT, 500 + i, 100000 + (i * 10000))
                .addTests(c, Lists.newArrayList(
                    createFailedTest(2L, TEST_WITH_HISTORY_FAILING_IN_MASTER),
                    createPassingTest(3L, TEST_WITH_HISTORY_PASSING_IN_MASTER),
                    createTest(50L, TEST_FLAKY_IN_MASTER, i % 2 == 0),
                    createPassingTest(400L, TEST_WAS_FIXED_IN_MASTER)));

            if (i % 7 == 1) {
                ProblemOccurrence timeout = new ProblemOccurrence();
                timeout.setType(ProblemOccurrence.TC_EXECUTION_TIMEOUT);
                cache1InMaster.addProblems(c, Collections.singletonList(timeout));
            }

            addBuilds(cache1InMaster);
        }

        long ageMs = TimeUnit.DAYS.toMillis(InvocationData.MAX_DAYS);

        for (int i = 0; i < 134; i++) {
            addBuilds(createFailedBuild(c, CACHE_1,
                ITeamcity.DEFAULT, i, ageMs + (i * 10000))
                .addTests(c, Lists.newArrayList(
                    createFailedTest(400L, TEST_WAS_FIXED_IN_MASTER))));
        }

        for (int i = 0; i < 10; i++) {
            final FatBuildCompacted successfull =
                createFatBuild(c, CACHE_1, "some-exotic-branch", i+7777, 100020, true)
                    .addTests(c,
                        Lists.newArrayList(
                            createPassingTest(1L, TEST_WITHOUT_HISTORY),
                            createPassingTest(2L, TEST_WITH_HISTORY_FAILING_IN_MASTER),
                            createPassingTest(3L, TEST_WITH_HISTORY_PASSING_IN_MASTER),
                            createPassingTest(50L, TEST_FLAKY_IN_MASTER),
                            createPassingTest(400L, TEST_WAS_FIXED_IN_MASTER)));

            addBuilds(successfull);
        }
    }

    private void addBuilds(FatBuildCompacted... builds) {
        for (FatBuildCompacted build : builds) {
            final FatBuildCompacted oldB = apacheBuilds.put(build.id(), build);

            Preconditions.checkState(oldB==null);
        }
    }

    @NotNull
    private TestOccurrenceFull createFailedTest(long id, String name) {
        return createTest(id, name, false);
    }

    @NotNull
    private TestOccurrenceFull createPassingTest(long id, String name) {
        return createTest(id, name, true);
    }

    @NotNull private TestOccurrenceFull createTest(long id, String name, boolean passed) {
        TestOccurrenceFull tf = new TestOccurrenceFull();

        tf.test = new TestRef();

        tf.test.id = String.valueOf(id);
        tf.name = name;
        tf.status = passed ? TestOccurrence.STATUS_SUCCESS : TestOccurrence.STATUS_FAILURE;
        return tf;
    }

    @NotNull
    public FatBuildCompacted createFailedBuild(IStringCompactor c, String btId, String branch, int id, long ageMs) {
        return createFatBuild(c, btId, branch, id, ageMs, false);
    }

    @NotNull public FatBuildCompacted createFatBuild(IStringCompactor c, String btId, String branch, int id, long ageMs,
        boolean passed) {
        final Build build = createJaxbBuild(btId, branch, id, ageMs, passed);

        return new FatBuildCompacted(c, build);
    }

    @NotNull
    private Build createJaxbBuild(String btId, String branch, int id, long ageMs, boolean passed) {
        final Build build = new Build();
        build.buildTypeId = btId;
        final BuildType type = new BuildType();
        type.setId(btId);
        type.setName(btId);
        build.setBuildType(type);
        build.setId(id);
        build.setStartDateTs(System.currentTimeMillis() - ageMs);
        build.setBranchName(branch);
        build.state = Build.STATE_FINISHED;
        build.status = passed ? Build.STATUS_SUCCESS : BuildRef.STATUS_FAILURE;

        return build;
    }

    public Map<Integer, FatBuildCompacted> apacheBuilds() {
        return apacheBuilds;
    }
}
