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
package org.apache.ignite.tcignited.history;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.tcbot.common.TcBotConst;

/**
 * Abstract in memory summary of suite or test execution history
 */
public abstract class AbstractRunHist implements IRunHistory {
    /**
     *
     */
    public abstract Iterable<Invocation> invocations();

    /**
     *
     */
    public Stream<Invocation> getInvocations() {
        return StreamSupport.stream(invocations().spliterator(), false);
    }

    /** {@inheritDoc} */
    @Override public int getCriticalFailuresCount() {
        return (int)getInvocations().filter(inv -> inv.status() == InvocationData.CRITICAL_FAILURE).count();
    }

    /** {@inheritDoc} */
    @Override public int getFailuresCount() {
        return (int)getInvocations().filter(inv ->
            inv.status() == InvocationData.FAILURE
                || inv.status() == InvocationData.CRITICAL_FAILURE).count();
    }

    /** {@inheritDoc} */
    @Override public int getRunsCount() {
        return (int)getInvocations().filter(inv ->
            inv.status() != InvocationData.MISSING && !Invocation.isMutedOrIgnored(inv.status())).count();
    }

    /** {@inheritDoc} */
    @Override public boolean isFlaky() {
        return getStatusChangesWithoutCodeModification() >= TcBotConst.FLAKYNESS_STATUS_CHANGE_BORDER;
    }

    /** {@inheritDoc} */
    @Override public final String getFlakyComments() {
        int statusChange = getStatusChangesWithoutCodeModification();

        if (statusChange < TcBotConst.FLAKYNESS_STATUS_CHANGE_BORDER)
            return null;

        return "Test seems to be flaky: " +
            "changed its status [" + statusChange + "/" + getInvocations().count() + "] without code modifications";
    }

    /**
     * @return number of status change without code modifications for test to be considered as flaky
     */
    public int getStatusChangesWithoutCodeModification() {
        int statusChange = 0;

        Invocation prev = null;

        for (Invocation cur : invocations()) {
            if (cur == null)
                continue;

            if (cur.status() == InvocationData.MISSING)
                continue;

            //todo here all previous MISSING invocations status could be checked
            if (prev != null) {
                if (prev.status() != cur.status()
                    && cur.changesState() == ChangesState.NONE
                    && prev.changesState() != ChangesState.UNKNOWN)
                    statusChange++;
            }

            prev = cur;
        }
        return statusChange;
    }

    @Nullable
    public Integer detectTemplate(IEventTemplate t) {
        int centralEvtBuild = t.beforeEvent().length;

        int[] template = concatArr(t.beforeEvent(), t.eventAndAfter());

        assert centralEvtBuild < template.length;
        assert centralEvtBuild >= 0;

        boolean includeMissing = t.includeMissing();

        List<Invocation> histAsArr = new ArrayList<>();
        for (Invocation invocation : invocations()) {
            if (includeMissing || invocation.status() != InvocationData.MISSING)
                histAsArr.add(invocation);
        }

        if (histAsArr.size() < template.length)
            return null;

        Integer detectedAt = null;

        //startIgnite from the end to find most recent
        for (int idx = histAsArr.size() - template.length; idx >= 0; idx--) {
            detectedAt = checkTemplateAtPos(template, centralEvtBuild, histAsArr, idx);

            if (detectedAt != null)
                break;
        }

        if (detectedAt != null && t.shouldBeFirstNonMissing()) {
            for (Invocation invocation : invocations()) {
                if (invocation.status() != InvocationData.MISSING)
                    return invocation.buildId() != detectedAt ? null : detectedAt;
            }

            return null;
        }

        return detectedAt;
    }

    private static int[] concatArr(int[] arr1, int[] arr2) {
        int[] arr1and2 = new int[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, arr1and2, 0, arr1.length);
        System.arraycopy(arr2, 0, arr1and2, arr1.length, arr2.length);

        return arr1and2;
    }

    @Nullable
    private static Integer checkTemplateAtPos(int[] template, int centralEvtBuild, List<Invocation> histAsArr,
        int idx) {
        for (int tIdx = 0; tIdx < template.length; tIdx++) {
            Invocation curStatus = histAsArr.get(idx + tIdx);

            if (curStatus == null)
                break;

            RunStatus tmpl = RunStatus.byCode(template[tIdx]);

            if ((tmpl == RunStatus.RES_OK_OR_FAILURE && (curStatus.status() == InvocationData.OK || curStatus.status() == InvocationData.FAILURE))
                || curStatus.status() == tmpl.getCode()) {
                if (tIdx == template.length - 1)
                    return histAsArr.get(idx + centralEvtBuild).buildId();
            }
            else
                break;
        }

        return null;
    }
}
