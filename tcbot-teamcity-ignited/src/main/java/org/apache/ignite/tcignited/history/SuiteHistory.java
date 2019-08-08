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
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.internal.binary.BinaryObjectExImpl;

/**
 * Suite run history (in memory) summary with tests grouped by name.
 */
public class SuiteHistory implements ISuiteRunHistory {
    /** Tests history: Test name ID->RunHistory */
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

    public int size(Ignite ignite) {
        BinaryObjectExImpl binary = ignite.binary().toBinary(this);
        return binary.length();
    }

    public IRunHistory getTestRunHist(int testName) {
        RunHistCompacted original = testsHistory.get(testName);
        return new IRunHistory() {
            @Nullable @Override public List<Integer> getLatestRunResults() {
                byte[] bytes = testsCompactedHist.get(testName);
                if (bytes == null)
                    return null;

                List<Integer> res = new ArrayList<>();
                for (int i = 0; i < bytes.length; i++)
                    res.add((int)bytes[i]);

                return res;
            }

            @Nullable @Override public String getFlakyComments() {
                return null;
            }

            @Nullable @Override public Integer detectTemplate(IEventTemplate t) {
                return null;
            }

            @Override public int getCriticalFailuresCount() {
                return -1;
            }

            @Override public boolean isFlaky() {
                return false;
            }

            @Override public int getRunsCount() {
                return -1;
            }

            @Override public int getFailuresCount() {
                return -1;
            }
        };
    }

    @Override
    public ISuiteRunHistory filter(Map<Integer, Integer> requireParameters) {
        RunHistCompacted suitesFiltered = this.suiteHist.filterSuiteInvByParms(requireParameters);
        Set<Integer> builds = suitesFiltered.buildIds();

        SuiteHistory res = new SuiteHistory();

        res.suiteHist = suitesFiltered;
        this.testsHistory.forEach((tName,invList)-> res.testsHistory.put(tName, invList.filterByBuilds(builds)));

        return res;
    }

    private RunHistCompacted getOrAddTestsHistory(Integer tName) {
        return testsHistory.computeIfAbsent(tName, k_ -> new RunHistCompacted());
    }

    private void addTestInvocation(Integer tName, Invocation invocation) {
        getOrAddTestsHistory(tName).addInvocation(invocation);
    }

    /**
     * @param suiteInv suite invocation (build) to be added to history (summary).
     */
    private void addSuiteInvocation(SuiteInvocation suiteInv) {
        suiteInv.tests().forEach(this::addTestInvocation);

        suiteHist.addInvocation(suiteInv.suiteInvocation());
    }

    @Override public IRunHistory self() {
        return suiteHist;
    }
}
