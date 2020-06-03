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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.engine.chain.TestCompactedMult;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.history.IRunHistory;

public class ShortTestFailureUi {
    private static final Pattern TEST_CLASS_AND_METHOD_PATTERN = Pattern.compile("([^.]+[.][^.]+(\\[.+\\])?$)");

    /** Test full Name */
    public String name;

    /** suite (in code) short name */
    @Nullable public String suiteName;

    /** test short name with class and method */
    @Nullable public String testName;


    /** Blocker comment: indicates test seems to be introduced failure. */
    @Nullable public String blockerComment;

    /**
     *
     */
    public boolean isPossibleBlocker() {
        return !Strings.isNullOrEmpty(blockerComment);
    }

    public ShortTestFailureUi initFrom(@Nonnull TestCompactedMult failure,
        ITeamcityIgnited tcIgn, Integer baseBranchId) {
        name = failure.getName();

        String[] split = Strings.nullToEmpty(name).split("\\:");
        if (split.length >= 2) {
            this.suiteName = extractSuite(split[0]);
            this.testName = extractTest(split[1]);
        }

        final IRunHistory stat = failure.history(tcIgn, baseBranchId);
        blockerComment = failure.getPossibleBlockerComment(stat);

        return this;
    }

    public static String extractTest(String s) {
        String testShort = s.trim();
        Matcher matcher = TEST_CLASS_AND_METHOD_PATTERN.matcher(testShort);
        return matcher.find() ? matcher.group(0) : null;
    }

    public static String extractSuite(String s) {
        String suiteShort = s.trim();
        String[] suiteComps = suiteShort.split("\\.");
        if (suiteComps.length > 1)
            return suiteComps[suiteComps.length - 1];
        return null;
    }
}
