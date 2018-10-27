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
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.tcbot.chain.BuildChainProcessor;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompareBuildsService {
    /** */
    private static final Logger logger = LoggerFactory.getLogger(CompareBuildsService.class);

    @Inject ITcServerProvider helper;
    @Inject ICredentialsProv prov;
    @Inject BuildChainProcessor bcp;
    @Inject ITeamcityIgnitedProvider tcIgnitedProv;
    @Inject IStringCompactor compactor;

    public List<String> tests0(String srvId, Integer buildId ) {
        IAnalyticsEnabledTeamcity teamcity = helper.server(srvId, prov);

        String hrefById = teamcity.getBuildHrefById(buildId);
        BuildRef buildRef = new BuildRef();

        buildRef.setId(buildId);
        buildRef.href = hrefById;

        ITeamcityIgnited srv = tcIgnitedProv.server(srvId, prov);

        return tests0(teamcity, srv, buildRef, bcp);
    }

    /** */
    private List<String> tests0(ITeamcity tc, ITeamcityIgnited server,
        BuildRef ref, BuildChainProcessor bcp) {
        List<String> tests = new ArrayList<>();

        Build build = tc.getBuild(ref.href);

        if (build.isComposite()) {
            List<BuildRef> deps = build.getSnapshotDependenciesNonNull();

            logger.info("Build {} is composite ({}).", build.getId(), deps.size());

            for (BuildRef ref0 : deps)
                tests.addAll(tests0(tc, server, ref0, bcp));
        }
        else {
            logger.info("Loading tests for build {}.", build.getId());

            MultBuildRunCtx buildCtx = new MultBuildRunCtx(build, compactor);

            buildCtx.addBuild(bcp.loadTestsAndProblems(tc, build, buildCtx, server));

            for (String testName : buildCtx.tests())
                tests.add(extractTestName(testName));
        }

        return tests;
    }


    /**
     * Get rid from suite name.
     * @param testFullName Test full name.
     * @return Test name.
     */
    private String extractTestName(String testFullName) {
        int pos = testFullName.indexOf(": ");

        return pos >= 0 ? testFullName.substring(pos + 2) : testFullName;
    }
}
