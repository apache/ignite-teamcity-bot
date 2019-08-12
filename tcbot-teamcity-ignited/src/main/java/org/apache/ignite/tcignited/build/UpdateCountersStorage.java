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

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ignite.internal.util.typedef.internal.U;

public class UpdateCountersStorage {
    /** Counters: Map from Branch name compactor ID to its correspondent update counter value. */
    private ConcurrentMap<Integer, AtomicInteger> counters = new ConcurrentHashMap<>();

    public Map<Integer, Integer> getCounters(Collection<Integer> branchNames) {
        Map<Integer, Integer> res = new TreeMap<>();

        for (Integer name : branchNames) {
            if (name == null)
                continue;

            res.put(name, getIntegerForEntry(name).get());
        }

        System.err.println("Requested counters:" + res);

        return res;
    }

    public static String getCountersHash(Map<Integer, Integer> counters) {
        return Integer.toHexString(U.safeAbs(counters == null ? 0 : counters.hashCode()));
    }

    public AtomicInteger getIntegerForEntry(int name) {
        return counters.computeIfAbsent(name, (k_) -> new AtomicInteger());
    }

    public void increment(int branchName) {
        getIntegerForEntry(branchName).incrementAndGet();
    }
}
