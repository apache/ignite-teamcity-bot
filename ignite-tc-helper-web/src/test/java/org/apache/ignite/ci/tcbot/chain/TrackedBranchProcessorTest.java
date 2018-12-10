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

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.TeamcityIgnitedProviderMock;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.current.TestFailuresSummary;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link TrackedBranchChainsProcessor}
 */
public class TrackedBranchProcessorTest {
    public static final String SRV_ID = "apache";
    /** Builds emulated storage. */
    private Map<Integer, FatBuildCompacted> apacheBuilds = new ConcurrentHashMap<>();

    /**
     * Injector.
     */
    private Injector injector = Guice.createInjector(new MockBasedTcBotModule());

    /** */
    @Before
    public void initBuilds() {
        final TeamcityIgnitedProviderMock instance = (TeamcityIgnitedProviderMock) injector.getInstance(ITeamcityIgnitedProvider.class);
        instance.addServer(SRV_ID, apacheBuilds);
    }

    @Test
    public void testTrackedBranchChainsProcessor() {
        TrackedBranchChainsProcessor tbProc = injector.getInstance(TrackedBranchChainsProcessor.class);

        String brachName = "master"; //todo https://issues.apache.org/jira/browse/IGNITE-10620 use separate branch e.g. masterForTests
        TestFailuresSummary failures = tbProc.getTrackedBranchTestFailures(brachName,
            false,
            1,
            mock(ICredentialsProv.class)
        );

        System.out.println(new Gson().toJson(failures));

    }

}
