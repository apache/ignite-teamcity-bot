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

package org.apache.ignite.ci.tcbot.issue;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.ignite.ci.tcbot.chain.MockBasedTcBotModule;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedProviderMock;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.tcbot.engine.conf.BranchTracked;
import org.apache.ignite.tcbot.engine.conf.ChainAtServerTracked;
import org.apache.ignite.tcbot.engine.conf.TcBotJsonConfig;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.TeamcityIgnitedImpl;
import org.apache.ignite.tcservice.ITeamcity;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrenceFull;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.apache.ignite.ci.tcbot.chain.PrChainsProcessorTest.createFatBuild;
import static org.apache.ignite.ci.tcbot.chain.PrChainsProcessorTest.createTest;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public class IssueDetectorTest {
    /** Server id. */
    public static final String SRV_ID = "apacheTest";
    public static final String PDS_1 = "PDS1";
    public static final String PDS_2 = "PDS2_noChanges";

    /** Builds emulated storage. */
    private Map<Integer, FatBuildCompacted> apacheBuilds = new ConcurrentHashMap<>();

    /** Config Branches tracked. */
    private TcBotJsonConfig branchesTracked = new TcBotJsonConfig();
    /**
     * Injector.
     */
    private Injector injector = Guice.createInjector(new MockBasedTcBotModule(branchesTracked));

    /** */
    @Before
    public void initBuilds() {
        ((TeamcityIgnitedProviderMock)injector.getInstance(ITeamcityIgnitedProvider.class))
            .addServer(SRV_ID, apacheBuilds);
    }

    @NotNull public ChainAtServerTracked trackedChain(String suiteId) {
        ChainAtServerTracked chain = new ChainAtServerTracked();

        chain.serverId = SRV_ID;
        chain.branchForRest = ITeamcity.DEFAULT;
        chain.suiteId = suiteId;

        return chain;
    }

    @Test
    public void testDetector() {
        String brachName = "masterTest";
        String chainId = TeamcityIgnitedImpl.DEFAULT_PROJECT_ID;
        BranchTracked branch = new BranchTracked();
        branch.id = brachName;
        branch.chains.add(trackedChain(chainId));
        branchesTracked.addBranch(branch);

        IStringCompactor c = injector.getInstance(IStringCompactor.class);

        Map<String, String> pds1Hist = new TreeMap<String, String>() {
            {
                put("testFailed", "0000011111");
                put("testOk", "      0000");
            }
        };

        Map<String, String> buildWoChanges = new TreeMap<String, String>() {
            {
                put("testFailedShouldBeConsideredAsFlaky", "0000011111");
                put("testFlakyStableFailure", "0000010101100101");
            }
        };

        emulateHistory(chainId, c, pds1Hist, buildWoChanges);

        IssueDetector issueDetector = injector.getInstance(IssueDetector.class);

        ITcBotUserCreds mock = mock(ITcBotUserCreds.class);
        when(mock.hasAccess(anyString())).thenReturn(true);
        issueDetector.startBackgroundCheck(mock);

        String masterStatus = issueDetector.checkFailuresEx(brachName);

        int expIssuesCnt = 2;

        System.out.println(masterStatus);
        assertTrue(masterStatus, masterStatus.contains("New issues found " + expIssuesCnt));

        /* todo: https://issues.apache.org/jira/browse/IGNITE-10620

        - Add examples of failed tests into history, validate notifications originated.

         */
        issueDetector.sendNewNotificationsEx();

        issueDetector.stop();
    }

    /**
     * @param chainId Chain id.
     * @param c Compactor.
     * @param pds1Hist PDS 1 history - build with changes all the time
     */
    public void emulateHistory(String chainId,
        IStringCompactor c,
        Map<String, String> pds1Hist,
        Map<String, String> buildWoChanges) {
        Map<Integer, FatBuildCompacted> builds = apacheBuilds;

        emulateHistory(builds, chainId, c, PDS_1, pds1Hist, PDS_2, buildWoChanges);
    }

    public static void emulateHistory(Map<Integer, FatBuildCompacted> builds,
        String chainId, IStringCompactor c,
        String pds1, Map<String, String> pds1Hist,
        String pds2, Map<String, String> buildWoChanges) {
        OptionalInt longestHist = Stream.concat(pds1Hist.values().stream(),
            buildWoChanges.values().stream()).mapToInt(String::length).max();
        Preconditions.checkState(longestHist.isPresent());
        int histLen = longestHist.getAsInt();

        for (int i = 0; i < histLen; i++) {
            FatBuildCompacted pds1Build
                = createFatBuild(c, pds1, ITeamcity.DEFAULT, 1100 + i, 100 * i, false)
                .addTests(c, testsMapToXmlModel(pds1Hist, histLen, i), null)
                .changes(new int[] {i});

            builds.put(pds1Build.id(), pds1Build);

            FatBuildCompacted pds2Build
                = createFatBuild(c, pds2, ITeamcity.DEFAULT, 1200 + i, 100 * i, false)
                .addTests(c, testsMapToXmlModel(buildWoChanges, histLen, i), null);

            builds.put(pds2Build.id(), pds2Build);

            FatBuildCompacted chainBuild = createFatBuild(c, chainId, ITeamcity.DEFAULT, 1000 + i, 100 * i, false)
                .snapshotDependencies(new int[] {pds1Build.id(), pds2Build.id()});
            builds.put(chainBuild.id(), chainBuild);
        }
    }

    @NotNull
    public static List<TestOccurrenceFull> testsMapToXmlModel(
        Map<String, String> pds1Hist,
        int histLen,
        int idx) {
        List<TestOccurrenceFull> page = Lists.newArrayList();

        pds1Hist.forEach((name, stat) -> {
            int cnt = stat.length();
            int locIdx = cnt - histLen + idx;
            if (locIdx < 0)
                return;

            char chState = stat.charAt(locIdx);
            boolean ok = '0' == chState;
            boolean failed = '1' == chState || '6' == chState;
            boolean muted = '6' == chState;
            if (ok || failed) {
                TestOccurrenceFull test = createTest(name.hashCode(), name, ok);
                if (muted)
                    test.muted = true;

                page.add(test);
            }
        });

        return page;
    }

}
