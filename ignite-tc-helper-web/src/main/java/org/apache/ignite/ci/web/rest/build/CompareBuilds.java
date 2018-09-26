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

package org.apache.ignite.ci.web.rest.build;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.util.Diff;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.exception.ServiceUnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** */
@Path("compare")
@Produces(MediaType.APPLICATION_JSON)
public class CompareBuilds {
    /** */
    private static final Logger logger = LoggerFactory.getLogger(CompareBuilds.class);

    /** */
    @Context
    private ServletContext ctx;

    /** */
    @Context
    private HttpServletRequest req;

    /** */
    @GET
    @Path("tests/txt")
    @Produces(MediaType.TEXT_PLAIN)
    public String getTestFailsText(
        @QueryParam("serverId") String srvId,
        @QueryParam("buildId") Integer buildId
    ) throws ServiceUnauthorizedException {
        StringBuilder buf = new StringBuilder();

        String prev = null;

        for (String test : sortedTests(srvId, buildId)) {
            buf.append(test);

            if (test.equals(prev))
                buf.append("  (dup name)");

            buf.append('\n');

            prev = test;
        }

        return buf.toString();
    }

    /** */
    private List<String> sortedTests(String srv, Integer build) {
        List<String> tests = tests(srv, build);

        Collections.sort(tests);

        return tests;
    }

    /** */
    @GET
    @Path("tests/cmp")
    @Produces(MediaType.TEXT_PLAIN)
    public String getTestFailsComparision(
        @QueryParam("serverId") String srv,
        @QueryParam("build1") Integer build1,
        @QueryParam("build2") Integer build2
    ) throws ServiceUnauthorizedException {
        Collection<String> tests1 = sortedTests(srv, build1);
        Collection<String> tests2 = sortedTests(srv, build2);

        Diff<String> diff = new Diff<>(tests1, tests2);

        StringBuilder buf = new StringBuilder();

        buf.append("\nSame (").append(diff.same().size()).append(")\n");

        buf.append("\nNew (").append(diff.added().size()).append(")\n");

        for (String add : diff.added())
            buf.append(add).append('\n');

        buf.append("\nNot found (").append(diff.removed().size()).append(")\n");

        for (String remove : diff.removed())
            buf.append(remove).append('\n');

        return buf.toString();
    }

    /** */
    private List<String> tests(String srv, Integer buildId) {
        final ITcHelper helper = CtxListener.getTcHelper(ctx);
        final ICredentialsProv prov = ICredentialsProv.get(req);

        if (!prov.hasAccess(srv))
            throw ServiceUnauthorizedException.noCreds(srv);

        IAnalyticsEnabledTeamcity teamcity = helper.server(srv, prov);

        String hrefById = teamcity.getBuildHrefById(buildId);
        BuildRef buildRef = new BuildRef();

        buildRef.setId(buildId);
        buildRef.href = hrefById;

        return tests0(teamcity, buildRef);

    }

    /** */
    private List<String> tests0(ITeamcity tc, BuildRef ref) {
        List<String> tests = new ArrayList<>();

        Build build = tc.getBuild(ref.href);

        if (build.isComposite()) {
            List<BuildRef> deps = build.getSnapshotDependenciesNonNull();

            logger.info("Build {} is composite ({}).", build.getId(), deps.size());

            for (BuildRef ref0 : deps)
                tests.addAll(tests0(tc, ref0));
        }
        else {
            logger.info("Loading tests for build {}.", build.getId());

            MultBuildRunCtx buildCtx = tc.loadTestsAndProblems(build);

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
