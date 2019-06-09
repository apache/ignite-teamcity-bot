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

package org.apache.ignite.ci.tcbot.builds;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.apache.ignite.tcbot.engine.chain.MultBuildRunCtx;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompareBuildsService {
    /** */
    private static final Logger logger = LoggerFactory.getLogger(CompareBuildsService.class);

    @Inject BuildChainProcessor bcp;
    @Inject ITeamcityIgnitedProvider tcIgnitedProv;
    @Inject IStringCompactor compactor;

    /**
     * @param srvId Server id.
     * @param buildId Build id.
     * @param prov Credentials provider.
     * @return List of build tests.
     */
    public List<String> tests0(String srvId, Integer buildId, ITcBotUserCreds prov) {
        ITeamcityIgnited srv = tcIgnitedProv.server(srvId, prov);

        return tests0(srv, buildId, bcp);
    }

    /** */
    private List<String> tests0(ITeamcityIgnited tcIgnited,
        Integer buildId, BuildChainProcessor bcp) {
        List<String> tests = new ArrayList<>();

        FatBuildCompacted fatBuild = tcIgnited.getFatBuild(buildId);

        if (fatBuild.isComposite()) {
            int[] deps = fatBuild.snapshotDependencies();

            logger.info("Build {} is composite ({}).", fatBuild.getId(), deps.length);

            for (int ref0 : deps)
                tests.addAll(tests0(tcIgnited, ref0, bcp));
        }
        else {
            logger.info("Loading tests for build {}.", fatBuild.getId());

            MultBuildRunCtx buildCtx = new MultBuildRunCtx(fatBuild.toBuildRef(compactor), compactor);

            buildCtx.addBuild(bcp.loadChanges(fatBuild, tcIgnited));

            for (String testName : buildCtx.tests())
                tests.add(extractTestName(testName));
        }

        return tests;
    }

    /**
     * Get rid from suite name.
     *
     * @param testFullName Test full name.
     * @return Test name.
     */
    private String extractTestName(String testFullName) {
        int pos = testFullName.indexOf(": ");

        return pos >= 0 ? testFullName.substring(pos + 2) : testFullName;
    }
}
