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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.SingletonScope;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.tcbot.engine.chain.*;
import org.apache.ignite.tcignited.build.TestCompactedV2;
import org.apache.ignite.tcignited.buildlog.IBuildLogProcessor;
import org.apache.ignite.tcservice.ITeamcity;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrence;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrenceFull;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcbot.persistence.InMemoryStringCompactor;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedMock;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

/**
 * Test for chain processor
 */
public class BuildChainProcessorTest {
    /** Unique failed test, prefix for test name. This name will be unique each time. */
    public static final String UNIQUE_FAILED_TEST = "uniqueFailedTest";

    /** Test failing every time. */
    public static final String TEST_FAILING_EVERY_TIME = "testFailingEveryTime";

    /** Pds 1 build type ID. */
    public static final String PDS_1_BT_ID = "Pds1";
    public static final String BRANCH = "master";

    /** Injector. */
    private Injector injector = Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
            bind(IStringCompactor.class).to(InMemoryStringCompactor.class).in(new SingletonScope());

            bind(IBuildLogProcessor.class).toInstance(Mockito.mock(IBuildLogProcessor.class));
        }
    });

    @Before
    public void resetCaches() {
        BuildRefCompacted.resetCached();
        TestCompactedV2.resetCached();
        TestCompactedMult.resetCached();
    }
    /**
     *
     */
    @Test
    public void testAllBuildsArePresentInMergedBuilds() {
        IStringCompactor c = injector.getInstance(IStringCompactor.class);
        BuildChainProcessor bcp = injector.getInstance(BuildChainProcessor.class);

        Map<Integer, FatBuildCompacted> builds = new HashMap<>();

        List<Integer> entry = Lists.newArrayList();
        for (int i = 0; i < 10; i++)
            addTestBuild(c, builds, entry, i);

        ITeamcityIgnited tcIgnited = tcIgnitedMock(builds);

        FullChainRunCtx ctx = bcp.loadFullChainContext(tcIgnited,
            entry,
            LatestRebuildMode.ALL, ProcessLogsMode.SUITE_NOT_COMPLETE, false, ITeamcity.DEFAULT, SyncMode.NONE, null, null);
        List<MultBuildRunCtx> suites = ctx.failedChildSuites().collect(Collectors.toList());

        assertTrue(!suites.isEmpty());

        for (MultBuildRunCtx suite : suites) {
            System.out.println(suite.getFailedTestsNames().collect(Collectors.toList()));

            if (suite.suiteName() != null && suite.suiteName().startsWith(UNIQUE_FAILED_TEST))
                assertTrue(suite.failedTests() >= 10);
            else
                assertTrue(suite.failedTests() >= 1);

            for (TestCompactedMult test : suite.getFailedTests()) {
                if (test.getName().startsWith(UNIQUE_FAILED_TEST))
                    assertEquals(1, test.failuresCount());
                else if (test.getName().equals(TEST_FAILING_EVERY_TIME))
                    assertEquals(10, test.failuresCount());
            }
        }

        //Adding successfull re-runs
        for (int j = 0; j < 10; j++) {
            FatBuildCompacted pds1 = testFatBuild(c, 130 + j, PDS_1_BT_ID);
            pds1.buildTypeName(UNIQUE_FAILED_TEST, c);

            TestOccurrenceFull t1 = new TestOccurrenceFull();
            t1.name = UNIQUE_FAILED_TEST + j;
            t1.status = TestOccurrence.STATUS_SUCCESS;
            pds1.addTests(c, Lists.newArrayList(t1), null);

            builds.put(pds1.id(), pds1);
        }

        FullChainRunCtx ctx2 = bcp.loadFullChainContext(tcIgnited,
            entry,
            LatestRebuildMode.ALL, ProcessLogsMode.SUITE_NOT_COMPLETE, false, ITeamcity.DEFAULT, SyncMode.NONE, null, null);
        List<MultBuildRunCtx> suites2 = ctx2.failedChildSuites().collect(Collectors.toList());

        assertTrue(!suites2.isEmpty());

        for (MultBuildRunCtx suite : suites2) {
            System.out.println(suite.getFailedTestsNames().collect(Collectors.toList()));

            if (suite.suiteName() != null && suite.suiteName().startsWith(UNIQUE_FAILED_TEST)) {
                for (TestCompactedMult test : suite.getFailedTests())
                    fail("Failure found but should be hidden by re-run " + test.getName());
            }
        }
    }

    /**
     *
     */
    @Test
    public void testAllBuildsArePresentInSingleuilds() {
        IStringCompactor c = injector.getInstance(IStringCompactor.class);
        BuildChainProcessor bcp = injector.getInstance(BuildChainProcessor.class);

        Map<Integer, FatBuildCompacted> builds = new HashMap<>();

        List<Integer> entry = Lists.newArrayList();
        addTestBuild(c, builds, entry, 0);

        FullChainRunCtx ctx = bcp.loadFullChainContext(tcIgnitedMock(builds),
            entry,
            LatestRebuildMode.LATEST, ProcessLogsMode.SUITE_NOT_COMPLETE, false, ITeamcity.DEFAULT, SyncMode.NONE, null, null);
        List<MultBuildRunCtx> suites = ctx.failedChildSuites().collect(Collectors.toList());

        assertTrue(!suites.isEmpty());

        MultBuildRunCtx suiteMultCtx = suites.get(0);
        assertTrue(suiteMultCtx.failedTests() >= 1);
    }

    public void addTestBuild(IStringCompactor c, Map<Integer, FatBuildCompacted> builds, List<Integer> entry, int i) {
        FatBuildCompacted root = testFatBuild(c, i, "RunAll");
        entry.add(root.id());

        root.snapshotDependencies(new int[] {100 + i, 200 + i});

        builds.put(root.id(), root);

        FatBuildCompacted pds1 = testFatBuild(c, 100 + i, PDS_1_BT_ID);
        pds1.buildTypeName(UNIQUE_FAILED_TEST, c);

        TestOccurrenceFull t1 = new TestOccurrenceFull();
        t1.name = UNIQUE_FAILED_TEST + i;
        t1.status = TestOccurrence.STATUS_FAILURE;
        pds1.addTests(c, Lists.newArrayList(t1), null);

        builds.put(pds1.id(), pds1);

        FatBuildCompacted pds2 = testFatBuild(c, 200 + i, "Pds2");

        TestOccurrenceFull t2 = new TestOccurrenceFull();
        t2.name = "testPds2" + i;
        t2.status = TestOccurrence.STATUS_SUCCESS;

        TestOccurrenceFull t3 = new TestOccurrenceFull();
        t3.name = TEST_FAILING_EVERY_TIME;
        t3.status = TestOccurrence.STATUS_FAILURE;
        pds2.addTests(c, Lists.newArrayList(t2, t3), null);

        builds.put(pds2.id(), pds2);
    }

    @NotNull public ITeamcityIgnited tcIgnitedMock(Map<Integer, FatBuildCompacted> builds) {
        return TeamcityIgnitedMock.getMutableMapTeamcityIgnited(builds,
            injector.getInstance(IStringCompactor.class));
    }

    @NotNull public static FatBuildCompacted testFatBuild(IStringCompactor c, int id, String bt) {
        FatBuildCompacted root = new FatBuildCompacted();
        BuildRef ref = new BuildRef();
        ref.setId(id);
        ref.buildTypeId = bt;
        ref.state = BuildRef.STATE_FINISHED;
        ref.status = BuildRef.STATUS_FAILURE;
        ref.branchName = BRANCH;
        root.fillFieldsFromBuildRef(c, ref);

        assertEquals(root.buildTypeId(c), bt);

        return root;
    }
}
