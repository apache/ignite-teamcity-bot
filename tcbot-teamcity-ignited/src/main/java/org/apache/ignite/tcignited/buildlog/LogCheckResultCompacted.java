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

package org.apache.ignite.tcignited.buildlog;

import com.google.common.base.MoreObjects;
import org.apache.ignite.ci.tcbot.common.StringFieldCompacted;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.IVersionedEntity;
import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.buildref.BuildRefDao;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Persistable Log from suite run check task result.
 */
@Persisted
class LogCheckResultCompacted implements ILogCheckResult, IVersionedEntity {
    /** Latest version. */
    private static final int LATEST_VERSION = 7;

    /** Entity version. */
    @SuppressWarnings("FieldCanBeLocal") private Integer _version = LATEST_VERSION;

    /** Last started test. Optionally filled from log post processor */
    private int lastStartedTest = -1;

    /** Last thread dump. */
    private StringFieldCompacted lastThreadDump = new StringFieldCompacted();

    /**
     * Test name -> its log check results
     */
    private Map<String, TestLogCheckResultCompacted> testLogCheckResult = new TreeMap<>();

    @Nullable
    private Set<Integer> buildCustomProblems = null;

    void setLastStartedTest(String lastStartedTest, IStringCompactor compactor) {
        this.lastStartedTest = compactor.getStringId(lastStartedTest);
    }

    void setLastThreadDump(String lastThreadDump) {
        this.lastThreadDump.setValue(lastThreadDump);
    }

    public String getLastStartedTest(IStringCompactor compactor) {
        if (lastStartedTest > 0)
            return compactor.getStringFromId(lastStartedTest);

        return null;
    }

    /** {@inheritDoc} */
    @Override public int version() {
        return _version == null ? -1 : _version;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    public String getLastThreadDump() {
        return lastThreadDump.getValue();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("lastStartedTest", lastStartedTest)
            .add("lastThreadDump", lastThreadDump)
            .add("testWarns", getWarns())
            .add("buildCustomProblems", buildCustomProblems)
            .toString();
    }

    private String getWarns() {
        StringBuilder sb = new StringBuilder();

        testLogCheckResult.forEach(
            (t, logCheckResult) -> {
                List<String> warns = logCheckResult.getWarns();
                if (warns.isEmpty())
                    return;

                sb.append(t).append("   :\n");

                warns.forEach(w -> {
                    sb.append(w).append("\n");
                });
            });

        return sb.toString();
    }

    public Map<String, ITestLogCheckResult> getTestLogCheckResult() {
        return Collections.unmodifiableMap(testLogCheckResult);
    }

    @Override
    public boolean hasThreadDump() {
        return lastThreadDump.isFilled();
    }

    TestLogCheckResultCompacted getOrCreateTestResult(String name) {
        return testLogCheckResult.computeIfAbsent(name, k -> new TestLogCheckResultCompacted());
    }

    public void addProblem(String code, IStringCompactor compactor) {
        if (buildCustomProblems == null)
            buildCustomProblems = new TreeSet<>();

        int problemId = compactor.getStringId(code);
        buildCustomProblems.add(problemId);
    }

    boolean hasProblem(String code, IStringCompactor compactor) {
        if (buildCustomProblems == null)
            return false;

        Integer stringIdIfPresent = compactor.getStringIdIfPresent(code);
        if (stringIdIfPresent == null)
            return false;

        return buildCustomProblems.contains(stringIdIfPresent);
    }

    /**
     *
     */
    @Nonnull public Set<String> getCustomProblems(IStringCompactor compactor) {
        return buildCustomProblems == null
                ? Collections.emptySet()
                : buildCustomProblems
                .stream()
                .map(compactor::getStringFromId)
                .collect(Collectors.toSet());
    }
}
