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
package org.apache.ignite.ci.runners;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;

public class BuildMetricsHistory {
    private Map<SuiteInBranch, BuildHistory> map = new TreeMap<>();
    private LinkedHashSet<SuiteInBranch> keys = new LinkedHashSet<>();
    public Map<String, FailuresHistory> failuresHistoryMap = new TreeMap<>();

    public BuildHistory history(SuiteInBranch id) {
        return map.computeIfAbsent(id, k -> {
            keys.add(k);
            return new BuildHistory();
        });
    }

    public Set<SuiteInBranch> builds() {
        return keys;
    }

    public TreeSet<String> dates() {
        Stream<String> stream = map.values().stream().flatMap(v -> v.map.keySet().stream());
        TreeSet<String> dates = new TreeSet<>();
        stream.forEach(dates::add);
        return dates;
    }

    public FullChainRunCtx build(SuiteInBranch next, String date) {
        BuildHistory hist = map.get(next);
        if (hist == null)
            return null;
        return hist.map.get(date);
    }

    public void addSuiteResult(String suiteName, boolean ok) {
        failuresHistoryMap.computeIfAbsent(suiteName, k -> new FailuresHistory())
            .addRun(ok);
    }
}
