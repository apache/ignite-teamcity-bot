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

import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.junit.Test;

import static org.apache.ignite.ci.analysis.RunStat.ChangesState.UNKNOWN;

public class RunStatTest {
    @Test
    public void testHistoricalIsExcluded() {

        RunStat stat = new RunStat("");
        TestOccurrence occurrence = new TestOccurrence();
        occurrence.status = "SUCCESS";
        occurrence.setId("id:10231,build:(id:1103529)");
        stat.addTestRunToLatest(occurrence, UNKNOWN);

        assert stat.getLatestRunResults().contains(0);

        occurrence.status = "FAILED";
        occurrence.setId("id:10231,build:(id:1133529)");
        stat.addTestRunToLatest(occurrence, UNKNOWN);
        assert stat.getLatestRunResults().contains(0);
        assert stat.getLatestRunResults().contains(1);

        for (int i = 0; i < RunStat.MAX_LATEST_RUNS; i++) {
            occurrence.setId("id:10231,build:(id:" + 1133529 + i + ")");

            stat.addTestRunToLatest(occurrence, UNKNOWN);
        }

        assert !stat.getLatestRunResults().contains(0) : stat.getLatestRunResults();

        System.out.println(stat.getLatestRunResults());

        //success, but too old
        for (int i = 0; i < RunStat.MAX_LATEST_RUNS; i++) {
            occurrence.setId("id:10231,build:(id:" + 1000 + i + ")");
            occurrence.status = "SUCCESS";
            stat.addTestRunToLatest(occurrence, UNKNOWN);
        }

        assert !stat.getLatestRunResults().contains(0) : stat.getLatestRunResults();

        System.out.println(stat.getLatestRunResults());
    }
}
