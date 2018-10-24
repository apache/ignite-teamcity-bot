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

import com.google.common.base.Strings;
import java.util.BitSet;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class TestCompacted {
    public static final int MUTED_F = 0;
    public static final int CUR_MUTED_F = 2;
    public static final int CUR_INV_F = 4;
    public static final int IGNORED_F = 6;
    /** Id in this build only. Does not idenfity test for its history */
    private int idInBuild = -1;

    private int name = -1;
    private int status = -1;
    private int duration = -1;

    private BitSet flags = new BitSet();

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
    public TestCompacted(IStringCompactor compactor, TestOccurrence testOccurrence) {
        String testOccurrenceId = testOccurrence.getId();
        if (!Strings.isNullOrEmpty(testOccurrenceId)) {
            try {
                idInBuild = RunStat.extractFullId(testOccurrenceId).getTestId();
            }
            catch (Exception e) {
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

    public TestOccurrence toTestOccurrence(IStringCompactor compactor, int buildId) {
        TestOccurrence occurrence = new TestOccurrence();

        String fullStrId = "id:" +
            idInBuild() + ",build:(id:" +
            buildId +
            ")";
        occurrence.id(fullStrId);
        occurrence.duration = duration < 0 ? null : duration;
        occurrence.name = compactor.getStringFromId(name);
        occurrence.status = compactor.getStringFromId(status);
        occurrence.href = "/app/rest/latest/testOccurrences/" + fullStrId;

        occurrence.muted = getFlag(MUTED_F);
        occurrence.currentlyMuted = getFlag(CUR_MUTED_F);
        occurrence.currentlyInvestigated = getFlag(CUR_INV_F);
        occurrence.ignored = getFlag(IGNORED_F);

        return occurrence;
    }

    private int idInBuild() {
        return idInBuild;
    }
}
