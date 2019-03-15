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

package org.apache.ignite.ci.teamcity.ignited.runhist;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.issue.EventTemplate;
import org.apache.ignite.ci.teamcity.ignited.IRunHistory;

/**
 *
 */
@Persisted
public class RunHistCompacted implements IVersionedEntity, IRunHistory {
    /** Latest version. */
    private static final int LATEST_VERSION = 1;

    /** Entity fields version. */
    @SuppressWarnings("FieldCanBeLocal")
    private short _ver = LATEST_VERSION;

    /** Data. */
    private InvocationData data = new InvocationData();

    public RunHistCompacted() {
    }

    public RunHistCompacted(RunHistKey ignored) {

    }

    /** {@inheritDoc} */
    @Override public int version() {
        return _ver;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    /** {@inheritDoc} */
    @Override public int getRunsCount() {
        return data.notMutedRunsCount();
    }

    /** {@inheritDoc} */
    @Override public int getFailuresCount() {
        return data.failuresCount();
    }

    /** {@inheritDoc} */
    @Override public int getFailuresAllHist() {
        return data.allHistFailures();
    }

    /** {@inheritDoc} */
    @Override public int getRunsAllHist() {
        return data.allHistRuns();
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public List<Integer> getLatestRunResults() {
        return data.getLatestRuns();
    }

    /** {@inheritDoc} */
    @Override public String getFlakyComments() {
        int statusChange = 0;

        Invocation prev = null;

        List<Invocation> latestRuns = data.invocations().collect(Collectors.toList());

        for (Invocation cur : latestRuns) {
            if (prev != null && cur != null) {
                if (prev.status() != cur.status()
                    && cur.changesState() == RunStat.ChangesState.NONE
                    && prev.changesState() != RunStat.ChangesState.UNKNOWN)
                    statusChange++;
            }
            prev = cur;
        }

        if (statusChange < 1)
            return null;

        return "Test seems to be flaky: " +
            "changed its status [" + statusChange + "/" + latestRuns.size() + "] without code modifications";
    }

    /** {@inheritDoc} */
    @Override public int getCriticalFailuresCount() {
        return data.criticalFailuresCount();
    }

    /**
     * @param inv Invocation.
     * @return if test run is new and is not expired.
     */
    public boolean addInvocation(Invocation inv) {
        return data.addInvocation(inv);
    }

    private static int[] concatArr(int[] arr1, int[] arr2) {
        int[] arr1and2 = new int[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, arr1and2, 0, arr1.length);
        System.arraycopy(arr2, 0, arr1and2, arr1.length, arr2.length);

        return arr1and2;
    }

    @Nullable
    public Integer detectTemplate(EventTemplate t) {
        if (data == null)
            return null;

        int centralEvtBuild = t.beforeEvent().length;

        int[] template = concatArr(t.beforeEvent(), t.eventAndAfter());

        assert centralEvtBuild < template.length;
        assert centralEvtBuild >= 0;

        List<Invocation> histAsArr = data.invocations().collect(Collectors.toList());

        if (histAsArr.size() < template.length)
            return null;

        Integer detectedAt = null;
        if (t.shouldBeFirst()) {
            if (histAsArr.size() >= getRunsAllHist()) // skip if total runs can't fit to latest runs
                detectedAt = checkTemplateAtPos(template, centralEvtBuild, histAsArr, 0);
        }
        else {
            //startIgnite from the end to find most recent
            for (int idx = histAsArr.size() - template.length; idx >= 0; idx--) {
                detectedAt = checkTemplateAtPos(template, centralEvtBuild, histAsArr, idx);

                if (detectedAt != null)
                    break;
            }
        }

        return detectedAt;
    }

    @Nullable
    private static Integer checkTemplateAtPos(int[] template, int centralEvtBuild, List<Invocation> histAsArr,
        int idx) {
        for (int tIdx = 0; tIdx < template.length; tIdx++) {
            Invocation curStatus = histAsArr.get(idx + tIdx);

            if (curStatus == null)
                break;

            RunStat.RunStatus tmpl = RunStat.RunStatus.byCode(template[tIdx]);

            if ((tmpl == RunStat.RunStatus.RES_OK_OR_FAILURE && (curStatus.status() == InvocationData.OK || curStatus.status() == InvocationData.FAILURE))
                || curStatus.status() == tmpl.getCode()) {
                if (tIdx == template.length - 1)
                    return histAsArr.get(idx + centralEvtBuild).buildId();
            }
            else
                break;
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("failRate", getFailPercentPrintable())
            .add("data", data)
            .toString();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RunHistCompacted compacted = (RunHistCompacted)o;
        return _ver == compacted._ver &&
            Objects.equals(data, compacted.data);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(_ver, data);
    }
}
