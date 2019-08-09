package org.apache.ignite.tcignited.build;

import java.util.Collection;
import java.util.Iterator;
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

        return res;
    }

    public static String getCountersHash(Map<Integer, Integer> counters) {
       return Integer.toHexString(U.safeAbs(counters.hashCode()));
    }

    public AtomicInteger getIntegerForEntry(int name) {
        return counters.computeIfAbsent(name, (k_) -> new AtomicInteger());
    }

    public void increment(int branchName) {
        getIntegerForEntry(branchName).incrementAndGet();
    }
}
