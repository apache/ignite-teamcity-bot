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

package org.apache.ignite.ci.teamcity.ignited.fatbuild;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import org.apache.ignite.ci.tcbot.common.StringFieldCompacted;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrence;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrenceFull;
import org.apache.ignite.tcservice.model.result.tests.TestRef;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.buildtype.ParametersCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.ci.teamcity.ignited.runhist.InvocationData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.Snappy;

/**
 *
 */
public class TestCompacted {
    public static final int MUTED_F = 0;
    public static final int CUR_MUTED_F = 2;
    public static final int CUR_INV_F = 4;
    public static final int IGNORED_F = 6;
    /** true when kept uncompressed */
    public static final int COMPRESS_TYPE_FLAG1 = 8;
    /** true when kept gzip */
    public static final int COMPRESS_TYPE_FLAG2 = 9;
    public static final int COMPRESS_TYPE_RFU3 = 10;
    public static final int COMPRESS_TYPE_RFU4 = 11;

    /** Id in this build only. Does not identify test for its history */
    private int idInBuild = -1;

    private int name = -1;
    private int status = -1;
    private int duration = -1;

    private BitSet flags = new BitSet();

    /** Test global, can be used for references. */
    private long testId = 0;

    /** Actual build id. */
    private int actualBuildId = -1;

    /** Uncompressesd/ZIP/Snappy compressed test log Details. */
    @Nullable private byte[] details;

    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TestCompacted.class);

    /**
     * Default constructor.
     */
    public TestCompacted() {
    }

    /**
     * @param compactor Compactor.
     * @param testOccurrence TestOccurrence.
     */
    public TestCompacted(IStringCompactor compactor, TestOccurrenceFull testOccurrence) {
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
        flags.clear(off, off + 2);

        boolean valPresent = val != null;
        flags.set(off, valPresent);

        if (valPresent)
            flags.set(off + 1, val);
    }


    /**
     * @param off Offset.
     */
    private Boolean getFlag(int off) {
        if (!flags.get(off))
            return null;

        return flags.get(off + 1);
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
            return "";

        final boolean flag1 = flags.get(COMPRESS_TYPE_FLAG1);
        final boolean flag2 = flags.get(COMPRESS_TYPE_FLAG2);
        if(!flag1 && !flag2) {
            try {
                byte[] uncompressed = Snappy.uncompress(details);
                return new String(uncompressed, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.error("Snappy.uncompress failed: " + e.getMessage(), e);
                return null;
            }
        } else if(flag1 && !flag2)
            return new String(details, StandardCharsets.UTF_8);
        else if (!flag1 && flag2) {
            try {
                return StringFieldCompacted.unzipToString(details);
            }
            catch (Exception e) {
                logger.error("GZip.uncompress failed: " + e.getMessage(), e);
                return null;
            }
        } else
            return null;
    }

    public void setDetails(String dtlsStr) {
        if (Strings.isNullOrEmpty(dtlsStr)) {
            this.details = null;
            return;
        }


        byte[] uncompressed;
        byte[] snappy = null;
        byte[] gzip = null;
        try {
            uncompressed = dtlsStr.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Set details failed: " + e.getMessage(), e);
            return;
        }
        try {
            snappy = Snappy.compress(uncompressed);
        }
        catch (Exception e) {
            logger.error("Snappy.compress failed: " + e.getMessage(), e);
        }

        try {
            gzip = StringFieldCompacted.zipBytes(uncompressed);
        }
        catch (Exception e) {
            logger.error("Snappy.compress failed: " + e.getMessage(), e);
        }

        final int snappyLen = snappy != null ? snappy.length : -1;
        final int gzipLen = gzip != null ? gzip.length : -1;

        flags.set(COMPRESS_TYPE_FLAG1, true);
        flags.set(COMPRESS_TYPE_FLAG2, false);
        //uncompressed
        details = uncompressed;

        if (snappyLen > 0 && snappyLen < details.length) {
            flags.set(COMPRESS_TYPE_FLAG1, false);
            flags.set(COMPRESS_TYPE_FLAG2, false);
            details = snappy;
        }

        if (gzipLen > 0 && gzipLen < details.length) {
            flags.set(COMPRESS_TYPE_FLAG1, false);
            flags.set(COMPRESS_TYPE_FLAG2, true);
            details = gzip;
        }


        logger.debug("U " + uncompressed.length + " S " + snappyLen + " Z " + gzipLen + ": F (" +
                flags.get(COMPRESS_TYPE_FLAG1) + ", " +
                flags.get(COMPRESS_TYPE_FLAG2) +")");
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
        TestCompacted compacted = (TestCompacted)o;
        return idInBuild == compacted.idInBuild &&
            name == compacted.name &&
            status == compacted.status &&
            duration == compacted.duration &&
            testId == compacted.testId &&
            actualBuildId == compacted.actualBuildId &&
            Objects.equals(flags, compacted.flags) &&
            Arrays.equals(details, compacted.details);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = Objects.hash(idInBuild, name, status, duration, flags, testId, actualBuildId);
        res = 31 * res + Arrays.hashCode(details);
        return res;
    }

    public boolean isFailedButNotMuted(IStringCompactor compactor) {
        return isFailedTest(compactor) && !(isMutedTest() || isIgnoredTest());
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
        return compactor.getStringId(TestOccurrence.STATUS_SUCCESS) != status;
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
     * @param compactor
     * @param build
     * @param paramsFilter parameters filter to find out parameters to be saved in RunHistory (for future filtering).
     * @return
     */
    public Invocation toInvocation(IStringCompactor compactor,
        FatBuildCompacted build,
        BiPredicate<Integer, Integer> paramsFilter) {
        final boolean failedTest = isFailedTest(compactor);

        final int failCode = failedTest
            ? (isIgnoredTest() || isMutedTest())
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
