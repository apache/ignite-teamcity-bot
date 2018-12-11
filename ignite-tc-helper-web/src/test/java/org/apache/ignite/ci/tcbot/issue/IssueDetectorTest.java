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

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.BranchesTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.tcbot.chain.MockBasedTcBotModule;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedImpl;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedProviderMock;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.ignite.ci.tcbot.chain.PrChainsProcessorTest.CACHE_9;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public class IssueDetectorTest {
    /** Server id. */
    public static final String SRV_ID = "apache";

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
        final TeamcityIgnitedProviderMock instance = (TeamcityIgnitedProviderMock) injector.getInstance(ITeamcityIgnitedProvider.class);
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
    public void testDetector() {
        BranchTracked branch = new BranchTracked();
        branch.id = FullQueryParams.DEFAULT_TRACKED_BRANCH_NAME;
        branch.chains.add(trackedChain(TeamcityIgnitedImpl.DEFAULT_PROJECT_ID));
        branchesTracked.addBranch(branch);

        IssueDetector issueDetector = injector.getInstance(IssueDetector.class);

        ICredentialsProv mock = mock(ICredentialsProv.class);
        when(mock.hasAccess(anyString())).thenReturn(true);
        issueDetector.startBackgroundCheck(mock);

        String masterStatus = issueDetector.checkFailuresEx("master");

        System.out.println(masterStatus);
        /* todo: https://issues.apache.org/jira/browse/IGNITE-10620

        - Add examples of failed tests into history, validate notifications originated.

         */
    }

}
