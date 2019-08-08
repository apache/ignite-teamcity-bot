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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;

/**
 * Suite run history (in memory) summary with tests grouped by name.
 */
public class SuiteHistory implements ISuiteRunHistory {
    /** Tests history: Test name ID->statuses for invocations */
    private Map<Integer, byte[]> testsInvStatues = new HashMap<>();

    /** Suite history. */
    private RunHistCompacted suiteHist = new RunHistCompacted();

    public SuiteHistory(Map<Integer, SuiteInvocation> suiteRunHist) {
        //filling data for tests invoked.
        Map<Integer, RunHistCompacted> testsHist = new HashMap<>();

        suiteRunHist.forEach((buildId, suiteInv) -> addSuiteInvocation(suiteInv, testsHist));

        suiteHist.sort();

        Map<Integer, Integer> buildIdToIdx = suiteHist.buildIdsMapping();
        int buildsCnt = buildIdToIdx.size();

        byte missingCode = (byte)RunStatus.RES_MISSING.getCode();
        testsHist.forEach((k, testHist) -> {
            byte[] testStatusesUltraComp = new byte[buildsCnt];

            for (int i = 0; i < testStatusesUltraComp.length; i++)
                testStatusesUltraComp[i] = missingCode;

            testHist.getInvocations().forEach(
                invocation -> {
                    int i = invocation.buildId();
                    Integer idx = buildIdToIdx.get(i);
                    if (idx == null)
                        return;

                    Preconditions.checkState(idx < testStatusesUltraComp.length);

                    testStatusesUltraComp[idx] = invocation.status();
                }
            );

            testsInvStatues.put(k, testStatusesUltraComp);
        });
    }

    private SuiteHistory() {}

    /**
     * @param suiteInv suite invocation (build) to be added to history (summary).
     * @param testsHist tests map.
     */
    private void addSuiteInvocation(SuiteInvocation suiteInv, Map<Integer, RunHistCompacted> testsHist) {
        suiteInv.tests().forEach(
            (tName, invocation) -> {
                testsHist.computeIfAbsent(tName, k_ -> new RunHistCompacted())
                    .addInvocation(invocation);
            });

        suiteHist.addInvocation(suiteInv.suiteInvocation());
    }

    /** {@inheritDoc} */
    @Nullable @Override public IRunHistory getTestRunHist(int testName) {
        byte[] testInvStatuses = testsInvStatues.get(testName);

        if(testInvStatuses == null)
            return null;

        return new TestUltraCompactRunHist(testInvStatuses, suiteHist);
    }

    /** {@inheritDoc} */
    @Override public ISuiteRunHistory filter(Map<Integer, Integer> requireParameters) {
        RunHistCompacted suitesFiltered = suiteHist.filterSuiteInvByParms(requireParameters);

        Map<Integer, Integer> buildIdToIdx = suiteHist.buildIdsMapping();

        Set<Integer> indexesToKeep = suitesFiltered.buildIds().stream().map(buildIdToIdx::get).collect(Collectors.toSet());

        SuiteHistory res = new SuiteHistory();

        res.suiteHist = suitesFiltered;

        testsInvStatues.forEach((tName, invList) -> {
            byte[] buildsFiltered = new byte[indexesToKeep.size()];

            int j = 0;
            for (int i = 0; i < invList.length; i++) {
                if (!indexesToKeep.contains(i))
                    continue;

                buildsFiltered[j] = invList[i];
                j++;
            }

            res.testsInvStatues.put(tName, buildsFiltered);
        });

        return res;
    }
    @Override public IRunHistory self() {
        return suiteHist;
    }

    private static class TestUltraCompactRunHist extends AbstractRunHist {
        @Nonnull private final byte[] testInvStatuses;
        @Nonnull private final RunHistCompacted suiteHist;

        public TestUltraCompactRunHist(@Nonnull byte[] testInvStatuses, @Nonnull RunHistCompacted suiteHist) {
            this.testInvStatuses = testInvStatuses;
            this.suiteHist = suiteHist;

            Preconditions.checkState(testInvStatuses.length == suiteHist.getInvocations().count());
        }

        /** {@inheritDoc} */
        @Nullable @Override public List<Integer> getLatestRunResults() {
            List<Integer> res = new ArrayList<>();
            for (int i = 0; i < testInvStatuses.length; i++)
                res.add((int)testInvStatuses[i]);

            return res;
        }

        /** {@inheritDoc} */
        @Override public int getCriticalFailuresCount() {
            int res = 0;
            for (int i = 0; i < testInvStatuses.length; i++) {
                if (testInvStatuses[i] == InvocationData.CRITICAL_FAILURE)
                    res++;
            }

            return res;
        }

        /** {@inheritDoc} */
        @Override public int getRunsCount() {
            int res = 0;
            for (int i = 0; i < testInvStatuses.length; i++) {
                byte status = testInvStatuses[i];
                if (status != InvocationData.MISSING && !Invocation.isMutedOrIgnored(status))
                    res++;
            }

            return res;
        }

        /** {@inheritDoc} */
        @Override public int getFailuresCount() {
            int res = 0;
            for (int i = 0; i < testInvStatuses.length; i++) {
                byte status = testInvStatuses[i];
                if (status == InvocationData.FAILURE || status == InvocationData.CRITICAL_FAILURE)
                    res++;
            }

            return res;
        }

        /** {@inheritDoc} */
        @Override public Iterable<Invocation> invocations() {
            return () -> new TestUltraCompactRunHistIterator(testInvStatuses, suiteHist);
        }
    }


    private static class TestUltraCompactRunHistIterator implements Iterator<Invocation> {
        /** Cur index: index of element to be returned in case next is called now. */
        private int curIdx = 0;
        @Nonnull private final byte[] testInvStatuses;
        @Nonnull private final RunHistCompacted suiteHist;

        public TestUltraCompactRunHistIterator(@Nonnull byte[] testInvStatuses,
            @Nonnull RunHistCompacted suiteHist) {
            this.testInvStatuses = testInvStatuses;
            this.suiteHist = suiteHist;
        }

        /** {@inheritDoc} */
        @Override public boolean hasNext() {
            return curIdx < testInvStatuses.length;
        }

        /** {@inheritDoc} */
        @Override public Invocation next() {
            if (!hasNext())
                throw new NoSuchElementException();

            Invocation suiteInv = suiteHist.getInvocationAt(curIdx);

            Invocation invocation = new Invocation(suiteInv.buildId())
                .withChangeState(suiteInv.changesState())
                .withStatus(testInvStatuses[curIdx]);

            curIdx++;

            return invocation;
        }
    }
}
