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
import javax.annotation.Nullable;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;

/**
 * Suite run history (in memory) summary with tests grouped by name.
 */
public class SuiteHistory implements ISuiteRunHistory {
    /** Tests history: Test name ID->RunHistory */
    @Deprecated
    private Map<Integer, RunHistCompacted> testsHistory = new HashMap<>();

    private Map<Integer, byte[]> testsCompactedHist = new HashMap<>();

    private RunHistCompacted suiteHist = new RunHistCompacted();

    public SuiteHistory(Map<Integer, SuiteInvocation> suiteRunHist) {
        //filling data
        suiteRunHist.forEach((buildId, suiteInv) -> addSuiteInvocation(suiteInv));

        suiteHist.sort();
        Map<Integer, Integer> buildIdToIdx = suiteHist.buildIdsMapping();
        int buildsCnt = buildIdToIdx.size();

        byte missingCode = (byte)RunStatus.RES_MISSING.getCode();
        testsHistory.forEach((k, testHist) -> {
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

            testsCompactedHist.put(k, testStatusesUltraComp);
        });

        finalizeInvocations();
    }

    private SuiteHistory() {}

    private void finalizeInvocations() {
        Set<Integer> presentBuilds = suiteHist.buildIds();

        testsHistory.forEach((k, t) -> t.registerMissing(k, presentBuilds));

        suiteHist.sort();
        testsHistory.values().forEach(RunHistCompacted::sort);
    }

    public IRunHistory getTestRunHist(int testName) {
        RunHistCompacted original = testsHistory.get(testName);

        //return original;

        return new TestUltraCompactRunHist(testsCompactedHist.get(testName), suiteHist);
    }

    @Override
    public ISuiteRunHistory filter(Map<Integer, Integer> requireParameters) {
        RunHistCompacted suitesFiltered = suiteHist.filterSuiteInvByParms(requireParameters);

        Map<Integer, Integer> buildIdToIdx = suiteHist.buildIdsMapping();

        Set<Integer> indexesToKeep = suitesFiltered.buildIds().stream().map(buildIdToIdx::get).collect(Collectors.toSet());

        SuiteHistory res = new SuiteHistory();

        res.suiteHist = suitesFiltered;
        this.testsCompactedHist.forEach((tName, invList) -> {
            byte[] buildsFiltered = new byte[indexesToKeep.size()];

            int j = 0;
            for (int i = 0; i < buildsFiltered.length; i++) {
                if (!indexesToKeep.contains(i))
                    continue;

                buildsFiltered[j] = buildsFiltered[i];
                j++;
            }

            res.testsCompactedHist.put(tName, buildsFiltered);
        });

        this.testsHistory.forEach((tName,invList)-> res.testsHistory.put(tName, invList.filterByBuilds(suitesFiltered.buildIds())));

        return res;
    }

    private RunHistCompacted getOrAddTestsHistory(Integer tName, Map<Integer, RunHistCompacted> map) {
        return map.computeIfAbsent(tName, k_ -> new RunHistCompacted());
    }

    private void addTestInvocation(Integer tName, Invocation invocation, Map<Integer, RunHistCompacted> map) {
        getOrAddTestsHistory(tName, map).addInvocation(invocation);
    }

    /**
     * @param suiteInv suite invocation (build) to be added to history (summary).
     */
    private void addSuiteInvocation(SuiteInvocation suiteInv) {
        suiteInv.tests().forEach((tName, invocation) -> addTestInvocation(tName, invocation, testsHistory));

        suiteHist.addInvocation(suiteInv.suiteInvocation());
    }

    @Override public IRunHistory self() {
        return suiteHist;
    }

    private static class TestUltraCompactRunHist extends AbstractRunHist {
        private final byte[] bytes;
        private final RunHistCompacted suiteHist;

        public TestUltraCompactRunHist(byte[] bytes, RunHistCompacted suiteHist) {
            this.bytes = bytes;
            this.suiteHist = suiteHist;

            Preconditions.checkState(bytes.length == suiteHist.getInvocations().count());

        }

        /** {@inheritDoc} */
        @Nullable @Override public List<Integer> getLatestRunResults() {
            byte[] bytes = this.bytes;
            if (bytes == null)
                return null;

            List<Integer> res = new ArrayList<>();
            for (int i = 0; i < bytes.length; i++)
                res.add((int)bytes[i]);

            return res;
        }

        @Override public int getRunsCount() {
            return -1;
        }

        @Override public int getFailuresCount() {
            return -1;
        }

        /** {@inheritDoc} */
        @Override public Iterable<Invocation> invocations() {
            return () -> new TestUltraCompactRunHistIterator(bytes, suiteHist);
        }

        private static class TestUltraCompactRunHistIterator implements Iterator<Invocation> {
            /** Cur index: index of element to be returned in case next is called now. */
            private int curIdx = 0;
            private final byte[] bytes;
            private final RunHistCompacted suiteHist;

            public TestUltraCompactRunHistIterator(byte[] bytes,
                RunHistCompacted suiteHist) {
                this.bytes = bytes;
                this.suiteHist = suiteHist;
            }

            /** {@inheritDoc} */
            @Override public boolean hasNext() {
                return curIdx < bytes.length;
            }

            /** {@inheritDoc} */
            @Override public Invocation next() {
                if (!hasNext())
                    throw new NoSuchElementException();

                Invocation suiteInv = suiteHist.getInvocationAt(curIdx);

                Invocation invocation = new Invocation(suiteInv.buildId())
                    .withChangeState(suiteInv.changesState())
                    .withStatus(bytes[curIdx]);

                curIdx++;

                return invocation;
            }
        }
    }
}
