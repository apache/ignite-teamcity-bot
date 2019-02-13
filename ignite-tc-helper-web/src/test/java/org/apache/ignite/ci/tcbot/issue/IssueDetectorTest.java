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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.tcbot.chain.MockBasedTcBotModule;
import org.apache.ignite.ci.tcbot.conf.BranchesTracked;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedImpl;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedProviderMock;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ICredentialsProv;
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

    /** Builds emulated storage. */
    private Map<Integer, FatBuildCompacted> apacheBuilds = new ConcurrentHashMap<>();

    /** Config Branches tracked. */
    private BranchesTracked branchesTracked = new BranchesTracked();
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
    public void testDetector() throws IOException {
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
                put("testFailedShoudlBeConsideredAsFlaky", "0000011111");
                put("testFlakyStableFailure", "0000011111111111");
            }
        };

        emulateHistory(chainId, c, pds1Hist, buildWoChanges);

        IssueDetector issueDetector = injector.getInstance(IssueDetector.class);

        ICredentialsProv mock = mock(ICredentialsProv.class);
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
        OptionalInt longestHist = pds1Hist.values().stream().mapToInt(String::length).max();
        Preconditions.checkState(longestHist.isPresent());
        int histLen = longestHist.getAsInt();

        for (int i = 0; i < histLen; i++) {
            FatBuildCompacted pds1Build
                = createFatBuild(c, "PDS1", ITeamcity.DEFAULT, 1100 + i, 1000 * i, false)
                .addTests(c, testsMapToXmlModel(pds1Hist, histLen, i))
                .changes(new int[] {i});

            apacheBuilds.put(pds1Build.id(), pds1Build);

            FatBuildCompacted pds2Build
                = createFatBuild(c, "PDS2_noChanges", ITeamcity.DEFAULT, 1200 + i, 1000 * i, false)
                .addTests(c, testsMapToXmlModel(buildWoChanges, histLen, i));

            apacheBuilds.put(pds2Build.id(), pds2Build);

            FatBuildCompacted chainBuild = createFatBuild(c, chainId, ITeamcity.DEFAULT, 1000 + i, 1000 * i, false)
                .snapshotDependencies(new int[] {pds1Build.id(), pds2Build.id()});
            apacheBuilds.put(chainBuild.id(), chainBuild);
        }
    }

    @NotNull
    public List<TestOccurrenceFull> testsMapToXmlModel(
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
            boolean failed = '1' == chState;
            if (ok || failed)
                page.add(createTest(name.hashCode(), name, ok));
        });

        return page;
    }

}
