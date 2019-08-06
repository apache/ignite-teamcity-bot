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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistCompacted;
import org.apache.ignite.internal.binary.BinaryObjectExImpl;
import org.apache.ignite.tcignited.history.IRunHistory;
import org.apache.ignite.tcignited.history.ISuiteRunHistory;
import org.apache.ignite.tcignited.history.SuiteInvocation;

/**
 * Suite run history (in memory) summary with tests grouped by name.
 */
public class SuiteHistory implements ISuiteRunHistory {
    /** Tests history: Test name ID->RunHistory */
    private Map<Integer, RunHistCompacted> testsHistory = new HashMap<>();

    private RunHistCompacted suiteHist = new RunHistCompacted();

    public SuiteHistory(Map<Integer, SuiteInvocation> suiteRunHist) {
        suiteRunHist.forEach((buildId, suiteInv) -> addSuiteInvocation(suiteInv));
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
        return testsHistory.get(testName);
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
