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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.SingletonScope;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.InMemoryStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.SyncMode;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Test for chain processor
 */
public class BuildChainProcessorTest {
    /** Injector. */
    private Injector injector = Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
            bind(IStringCompactor.class).to(InMemoryStringCompactor.class).in(new SingletonScope());
            super.configure();
        }
    });

    /**
     *
     */
    @Test
    public void testAllBuildsArePresentInMergedBuilds() {
        IStringCompactor c = injector.getInstance(IStringCompactor.class);
        BuildChainProcessor bcp = injector.getInstance(BuildChainProcessor.class);
        IAnalyticsEnabledTeamcity teamcity = Mockito.mock(IAnalyticsEnabledTeamcity.class);
        when(teamcity.getBuildFailureRunStatProvider()).thenReturn(Mockito.mock(Function.class));
        when(teamcity.getTestRunStatProvider()).thenReturn(Mockito.mock(Function.class));

        Map<Integer, FatBuildCompacted> builds = new HashMap<>();

        List<Integer> entry = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            FatBuildCompacted root = testFatBuild(c, i, "RunAll");
            entry.add(root.id());

            root.snapshotDependencies(new int[] {100 + i});

            builds.put(root.id(), root);

            FatBuildCompacted pds1 = testFatBuild(c, 100 + i, "PDS 1");

            TestOccurrenceFull t1 = new TestOccurrenceFull();
            t1.name = "test" + i;
            t1.status = TestOccurrence.STATUS_FAILURE;
            pds1.addTests(c, Lists.newArrayList(t1));

            builds.put(pds1.id(), pds1);
        }

        ITeamcityIgnited tcIgnited = Mockito.mock(ITeamcityIgnited.class);
        when(tcIgnited.getFatBuild(anyInt(), any(SyncMode.class)))
            .thenAnswer(inv ->
            {
                Integer arg = inv.getArgument(0);

                return Preconditions.checkNotNull(builds.get(arg), "Can't find build in map [" + arg + "]");
            });

        FullChainRunCtx ctx = bcp.loadFullChainContext(teamcity, tcIgnited,
            entry,
            LatestRebuildMode.LATEST, ProcessLogsMode.SUITE_NOT_COMPLETE, false, ITeamcity.DEFAULT, SyncMode.NONE);
        List<MultBuildRunCtx> suites = ctx.failedChildSuites().collect(Collectors.toList());

        assertTrue(!suites.isEmpty());

        MultBuildRunCtx suiteMultCtx = suites.get(0);
        assertTrue(suiteMultCtx.failedTests() >= 1);
    }

    @NotNull public FatBuildCompacted testFatBuild(IStringCompactor c, int id, String bt) {
        FatBuildCompacted root = new FatBuildCompacted();
        BuildRef ref = new BuildRef();
        ref.setId(id);
        ref.buildTypeId = bt;
        ref.state = BuildRef.STATE_FINISHED;
        ref.status = BuildRef.STATUS_FAILURE;
        root.fillFieldsFromBuildRef(c, ref);
        return root;
    }
}
