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

package org.apache.ignite.ci.analysis;

import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.db.Persisted;
import org.jetbrains.annotations.Nullable;

/**
 * Persistable Log from suite run check task result.
 */
@Persisted
public class LogCheckResult implements IVersionedEntity {
    /** Latest version. */
    private static final int LATEST_VERSION = 6;

    /** Entity version. */
    @SuppressWarnings("FieldCanBeLocal") private Integer _version = LATEST_VERSION;

    /** Last started test. Optionally filled from log post processor */
    private String lastStartedTest;

    /** Last thread dump. */
    private String lastThreadDump;

    /**
     * Test name -> its log check results
     */
    private Map<String, TestLogCheckResult> testLogCheckResult = new TreeMap<>();

    @Nullable
    private Set<String> buildCustomProblems = null;

    public void setLastStartedTest(String lastStartedTest) {
        this.lastStartedTest = lastStartedTest;
    }

    public void setLastThreadDump(String lastThreadDump) {
        this.lastThreadDump = lastThreadDump;
    }

    public String getLastStartedTest() {
        return lastStartedTest;
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
        return lastThreadDump;
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

    public Map<String, TestLogCheckResult> getTestLogCheckResult() {
        return Collections.unmodifiableMap(testLogCheckResult);
    }

    public TestLogCheckResult getOrCreateTestResult(String name) {
        return testLogCheckResult.computeIfAbsent(name, k -> new TestLogCheckResult());
    }

    public void addProblem(String code) {
        if (buildCustomProblems == null)
            buildCustomProblems = new TreeSet<>();

        buildCustomProblems.add(code);
    }

    public boolean hasProblem(String deadlock) {
        return buildCustomProblems != null && buildCustomProblems.contains(deadlock);
    }

    /**
     *
     */
    @Nonnull public Set<String> getCustomProblems() {
        return buildCustomProblems == null ? Collections.emptySet() : Collections.unmodifiableSet(buildCustomProblems);
    }
}
