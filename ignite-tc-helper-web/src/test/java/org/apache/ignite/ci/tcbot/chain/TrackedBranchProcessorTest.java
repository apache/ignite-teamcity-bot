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

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ignite.tcservice.ITeamcity;
import org.apache.ignite.ci.tcbot.conf.BranchTracked;
import org.apache.ignite.ci.tcbot.conf.ChainAtServerTracked;
import org.apache.ignite.ci.tcbot.conf.BranchesTracked;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.SyncMode;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedProviderMock;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.model.current.SuiteCurrentStatus;
import org.apache.ignite.ci.web.model.current.TestFailure;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import static org.apache.ignite.ci.tcbot.chain.PrChainsProcessorTest.CACHE_9;
import static org.apache.ignite.ci.tcbot.chain.PrChainsProcessorTest.TEST_RARE_FAILED_WITHOUT_CHANGES;
import static org.apache.ignite.ci.tcbot.chain.PrChainsProcessorTest.TEST_RARE_FAILED_WITH_CHANGES;
import static org.apache.ignite.ci.tcbot.chain.PrChainsProcessorTest.createFatBuild;
import static org.apache.ignite.ci.tcbot.chain.PrChainsProcessorTest.createTest;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link TrackedBranchChainsProcessor}
 */
public class TrackedBranchProcessorTest {
    public static final String SRV_ID = "apacheTest";
    public static final String BRACH_NAME = "trackedMaster";
    /** Builds emulated storage. */
    private Map<Integer, FatBuildCompacted> apacheBuilds = new ConcurrentHashMap<>();

    /** Branches tracked. */
    private BranchesTracked branchesTracked = new BranchesTracked();
    /**
     * Injector.
     */
    private Injector injector = Guice.createInjector(new MockBasedTcBotModule(branchesTracked));

    /** */
    @Before
    public void initBuilds() {
        final TeamcityIgnitedProviderMock instance = (TeamcityIgnitedProviderMock)injector.getInstance(ITeamcityIgnitedProvider.class);
        instance.addServer(SRV_ID, apacheBuilds);
    }

    @NotNull public ChainAtServerTracked trackedChain(String suiteId) {
        ChainAtServerTracked chain = new ChainAtServerTracked();

        chain.serverId = SRV_ID;
        chain.branchForRest = ITeamcity.DEFAULT;
        chain.suiteId = suiteId;
        return chain;
    }

    @Test
    public void testTrackedBranchChainsProcessor() {
        BranchTracked branch = new BranchTracked();
        branch.id = BRACH_NAME;
        branch.chains.add(trackedChain(CACHE_9));
        branchesTracked.addBranch(branch);

        IStringCompactor c = injector.getInstance(IStringCompactor.class);

        apacheBuilds.putAll(new PrChainsProcessorTest().initHistory(c));

        int buildCnt = 101;
        FatBuildCompacted fatBuild = createFatBuild(c, CACHE_9, ITeamcity.DEFAULT, buildCnt + 9999, 1340020, false)
            .addTests(c,
                Lists.newArrayList(
                    createTest(1L, TEST_RARE_FAILED_WITHOUT_CHANGES, false),
                    createTest(2L, TEST_RARE_FAILED_WITH_CHANGES, false)));

        fatBuild.changes(new int[] {1000000 + buildCnt, 1000020 + buildCnt});

        apacheBuilds.put(fatBuild.id(), fatBuild);

        TrackedBranchChainsProcessor tbProc = injector.getInstance(TrackedBranchChainsProcessor.class);

        ICredentialsProv mock = mock(ICredentialsProv.class);
        when(mock.hasAccess(anyString())).thenReturn(true);
        TestFailuresSummary failures = tbProc.getTrackedBranchTestFailures(BRACH_NAME,
            false,
            1,
            mock, SyncMode.RELOAD_QUEUED
        );

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println(gson.toJson(failures));

        assertFalse(failures.servers.isEmpty());

        ChainAtServerCurrentStatus apacheSrv = failures.servers.get(0);

        assertTrue(apacheSrv.failedTests > 0);

        assertFalse(apacheSrv.suites.isEmpty());

        Optional<SuiteCurrentStatus> cache9 = findSuite(apacheSrv, CACHE_9);
        assertTrue(cache9.isPresent());

        SuiteCurrentStatus suiteFails = cache9.get();
        assertFalse(suiteFails.testFailures.isEmpty());

        Optional<TestFailure> tfOpt = findTestFailure(suiteFails, TEST_RARE_FAILED_WITH_CHANGES);
        assertTrue(tfOpt.isPresent());
        assertNull(tfOpt.get().histBaseBranch.flakyComments);
        assertNull(tfOpt.get().problemRef);

        Optional<TestFailure> tfFlakyOpt = findTestFailure(suiteFails, TEST_RARE_FAILED_WITHOUT_CHANGES);
        assertTrue(tfFlakyOpt.isPresent());
        assertNotNull(tfFlakyOpt.get().histBaseBranch.flakyComments);

        assertNull(tfFlakyOpt.get().problemRef);
    }

    public Optional<SuiteCurrentStatus> findSuite(ChainAtServerCurrentStatus apacheSrv, String suiteName) {
        return apacheSrv.suites.stream().filter(s -> {
            return s.name.contains(suiteName);
        }).findAny();
    }

    public Optional<TestFailure> findTestFailure(SuiteCurrentStatus suiteFails, String name) {
        return suiteFails.testFailures.stream().filter(tf -> tf.name.equals(name)).findAny();
    }

}
