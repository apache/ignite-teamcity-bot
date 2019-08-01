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

package org.apache.ignite.tcignited.build;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import org.apache.ignite.ci.tcbot.common.StringFieldCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.ParametersCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.ci.teamcity.ignited.runhist.InvocationData;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcignited.buildlog.ILogProductSpecific;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrence;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrenceFull;
import org.apache.ignite.tcservice.model.result.tests.TestRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
@Persisted
public class TestCompactedV2 implements ITest {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TestCompactedV2.class);

    public static final int MUTED_F = 0;
    public static final int CUR_MUTED_F = 2;
    public static final int CUR_INV_F = 4;
    public static final int IGNORED_F = 6;

    /** Status success. */
    private static volatile int STATUS_SUCCESS = -1;

    /** Id in this build only. Does not identify test for its history */
    private int idInBuild = -1;

    private int name = -1;
    private int status = -1;
    private int duration = -1;

    private int flags = 0;

    /** Test global, can be used for references. */
    private long testId = 0;

    /** Actual build id. */
    private int actualBuildId = -1;

    /** endl-separated log */
    @Nullable StringFieldCompacted details = null;


    /**
     * Default constructor.
     */
    public TestCompactedV2() {
    }

    /**
     * @param compactor Compactor.
     * @param testOccurrence TestOccurrence.
     */
    public TestCompactedV2(IStringCompactor compactor, TestOccurrenceFull testOccurrence) {
        String testOccurrenceId = testOccurrence.getId();
        if (!Strings.isNullOrEmpty(testOccurrenceId)) {
            try {
                final TestId testId = extractFullId(testOccurrenceId);
                if (testId != null)
                    idInBuild = testId.getTestId();
            } catch (Exception e) {
                logger.error("Failed to handle TC response: " + testOccurrenceId, e);
            }
        }

        name = compactor.getStringId(testOccurrence.name);
        status = compactor.getStringId(testOccurrence.status);
        duration = testOccurrence.duration == null ? -1 : testOccurrence.duration;

        setFlag(MUTED_F, testOccurrence.muted);
        setFlag(CUR_MUTED_F, testOccurrence.currentlyMuted);
        setFlag(CUR_INV_F, testOccurrence.currentlyInvestigated);
        setFlag(IGNORED_F, testOccurrence.ignored);

        if (testOccurrence.build != null && testOccurrence.build.getId() != null)
            actualBuildId = testOccurrence.build.getId();

        if (testOccurrence.test != null && testOccurrence.test.id != null)
            testId = Long.valueOf(testOccurrence.test.id);

        setDetails(testOccurrence.details);
    }

    public static TestId extractFullId(String id) {
        Integer buildId = extractIdPrefixed(id, "build:(id:", ")");

        if (buildId == null)
            return null;

        Integer testId = extractIdPrefixed(id, "id:", ",");

        if (testId == null)
            return null;

        return new TestId(buildId, testId);
    }

    public static Integer extractIdPrefixed(String id, String prefix, String postfix) {
        try {
            int buildIdIdx = id.indexOf(prefix);
            if (buildIdIdx < 0)
                return null;

            int buildIdPrefixLen = prefix.length();
            int absBuildIdx = buildIdIdx + buildIdPrefixLen;
            int buildIdEndIdx = id.substring(absBuildIdx).indexOf(postfix);
            if (buildIdEndIdx < 0)
                return null;

            String substring = id.substring(absBuildIdx, absBuildIdx + buildIdEndIdx);

            return Integer.valueOf(substring);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private void setFlag(int off, Boolean val) {
        boolean valPresent = val != null;

        setBitAt(off, valPresent);

        if (valPresent)
            setBitAt(off + 1, valPresent);
        else
            setBitAt(off, false);
    }

    private void setBitAt(int off, boolean val) {
        if (val)
            flags |= (1 << off);// flags.set(off, true);
        else
            flags &= ~(1 << off); //flags.clear(off)
    }

    /**
     * @param off Offset.
     */
    private Boolean getFlag(int off) {
        if (!getBitAt(off))
            return null;

        return getBitAt(off + 1);
    }

    private boolean getBitAt(int off) {
        return (flags & (1 << off)) != 0;
    }

    public TestOccurrenceFull toTestOccurrence(IStringCompactor compactor, int buildId) {
        TestOccurrenceFull occurrence = new TestOccurrenceFull();

        String fullStrId = "id:" +
            idInBuild() + ",build:(id:" +
            buildId +
            ")";
        occurrence.id(fullStrId);
        occurrence.duration = getDuration();
        occurrence.name = compactor.getStringFromId(name);
        occurrence.status = compactor.getStringFromId(status);
        occurrence.href = "/app/rest/latest/testOccurrences/" + fullStrId;

        occurrence.muted = getMutedFlag();
        occurrence.currentlyMuted = getFlag(CUR_MUTED_F);
        occurrence.currentlyInvestigated = getCurrInvestigatedFlag();
        occurrence.ignored = getIgnoredFlag();

        if (actualBuildId > 0) {
            BuildRef buildRef = new BuildRef();

            buildRef.setId(actualBuildId);

            occurrence.build = buildRef;
        }

        if (testId != 0) {
            TestRef test = new TestRef();

            test.id = String.valueOf(testId);

            occurrence.test = test;
        }

        occurrence.details = getDetailsText();

        return occurrence;
    }

    public Boolean getCurrInvestigatedFlag() {
        return getFlag(CUR_INV_F);
    }

    /**
     *
     */
    @Nullable public String getDetailsText() {
        if (details == null)
            return null;

        return details.getValue();
    }

    public void setDetails(String dtlsStr) {
        if (Strings.isNullOrEmpty(dtlsStr)) {
            this.details = null;
            return;
        }

        ILogProductSpecific logSpecific;

       // new StringReader(dtlsStr)
        //logSpecific.needWarn()
        //todo filter
        this.details = new StringFieldCompacted();
        this.details.setValue(dtlsStr);
    }

    public Boolean getIgnoredFlag() {
        return getFlag(IGNORED_F);
    }

    public Boolean getMutedFlag() {
        return getFlag(MUTED_F);
    }

    private int idInBuild() {
        return idInBuild;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TestCompactedV2 compacted = (TestCompactedV2)o;
        return idInBuild == compacted.idInBuild &&
            name == compacted.name &&
            status == compacted.status &&
            duration == compacted.duration &&
            testId == compacted.testId &&
            actualBuildId == compacted.actualBuildId &&
            Objects.equals(flags, compacted.flags) &&
            Objects.equals(details, compacted.details);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = Objects.hash(idInBuild, name, status, duration, flags, testId, actualBuildId);
        res = 31 * res + Objects.hashCode(details);
        return res;
    }

    /**
     * @param successStatus Success status code.
     */
    public boolean isFailedButNotMuted(int successStatus) {
        return successStatus != status() && !isMutedOrIgnored();
    }

    public boolean isFailedButNotMuted(IStringCompactor compactor) {
        return isFailedButNotMuted(statusSuccess(compactor));
    }

    public int statusSuccess(IStringCompactor compactor) {
        //Each time compactor should give same result
        if (STATUS_SUCCESS == -1)
            STATUS_SUCCESS = compactor.getStringId(TestOccurrence.STATUS_SUCCESS);

        return STATUS_SUCCESS;
    }

    public boolean isMutedOrIgnored() {
        return isMutedTest() || isIgnoredTest();
    }

    public boolean isIgnoredTest() {
        Boolean flag = getIgnoredFlag();

        return flag != null && flag;
    }

    public boolean isMutedTest() {
        Boolean flag = getMutedFlag();

        return flag != null && flag;
    }

    public boolean isFailedTest(IStringCompactor compactor) {
        return statusSuccess(compactor) != status();
    }

    public String testName(IStringCompactor compactor) {
        return compactor.getStringFromId(name);
    }

    public int testName() {
        return name;
    }

    public boolean isInvestigated() {
        final Boolean investigatedFlag = getCurrInvestigatedFlag();

        return investigatedFlag != null && investigatedFlag;
    }

    public int status() {
        return status;
    }

    @Nullable public Integer getDuration() {
        return duration < 0 ? null : duration;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("idInBuild", idInBuild)
            .add("name", name)
            .add("status", status)
            .add("duration", duration)
            .add("flags", flags)
            .add("testId", testId)
            .add("actualBuildId", actualBuildId)
            .add("details", details)
            .toString() + "\n";
    }

    public Long getTestId() {
        if (testId != 0)
            return testId;

        return null;
    }

    /**
     * @param build
     * @param paramsFilter parameters filter to find out parameters to be saved in RunHistory (for future filtering).
     * @param successStatusStrId
     * @return
     */
    public static Invocation toInvocation(
        ITest test,
        FatBuildCompacted build,
        BiPredicate<Integer, Integer> paramsFilter, int successStatusStrId) {
        final boolean failedTest = successStatusStrId != test.status();

        final int failCode = failedTest
            ? (test.isIgnoredTest() || test.isMutedTest())
            ? InvocationData.MUTED
            : InvocationData.FAILURE
            : InvocationData.OK;

        Invocation invocation = new Invocation(build.getId())
            .withStatus(failCode)
            .withStartDate(build.getStartDateTs())
            .withChanges(build.changes());

        java.util.Map<Integer, Integer> importantParms = new TreeMap<>();

        ParametersCompacted parameters = build.parameters();
        if (parameters == null)
            return invocation;

        parameters.forEach((k, v) -> {
            if (paramsFilter.test(k, v))
                importantParms.put(k, v);
        });

        return invocation.withParameters(importantParms);
    }

    /**
     * Pair of build and test Ids.
     */
    private static class TestId {
        int buildId;
        int testId;

        public TestId(Integer buildId, Integer testId) {
            this.buildId = buildId;
            this.testId = testId;
        }

        public int getBuildId() {
            return buildId;
        }

        public int getTestId() {
            return testId;
        }

        /**
         * {@inheritDoc}
         */
        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("buildId", buildId)
                .add("testId", testId)
                .toString();
        }
    }
}
