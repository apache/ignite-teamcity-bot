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
package org.apache.ignite.tcbot.engine.ui;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.engine.chain.MultBuildRunCtx;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.history.IRunHistory;

import static org.apache.ignite.tcbot.engine.ui.DsSuiteUi.buildWebLinkToBuild;

public class ShortSuiteUi extends DsHistoryStatUi {
    /** Suite Name */
    public String name;

    /** Suite Run Result (filled if failed): Summary of build problems, count of tests, etc. */
    public String result;

    /**
     * Possible blocker comment: filled for PR and builds checks, non null value contains problem explanation
     * displayable.
     */
    @Nullable public String blockerComment;

    public List<ShortTestFailureUi> testShortFailures = new ArrayList<>();


    /** Web Href. to suite particular run */
    public String webToBuild = "";

    public int totalBlockers() {
        int res = 0;
        if (!Strings.isNullOrEmpty(blockerComment))
            res++;

        res += (int)testFailures().stream().filter(ShortTestFailureUi::isPossibleBlocker).count();

        return res;
    }

    public Collection<? extends ShortTestFailureUi> testFailures() {
        return testShortFailures;
    }


    public ShortSuiteUi initFrom(@Nonnull MultBuildRunCtx suite,
        ITeamcityIgnited tcIgnited,
        IStringCompactor compactor,
        IRunHistory baseBranchHist) {
        name = suite.suiteName();
        result = suite.getResult();
        webToBuild = buildWebLinkToBuild(tcIgnited, suite);

        blockerComment = suite.getPossibleBlockerComment(compactor, baseBranchHist, tcIgnited.config());

        return this;
    }

    public ShortSuiteUi testShortFailures(List<ShortTestFailureUi> failures) {
        this.testShortFailures = failures;

        return this;
    }
}
