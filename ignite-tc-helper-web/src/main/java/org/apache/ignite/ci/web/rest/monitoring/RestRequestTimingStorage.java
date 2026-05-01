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
package org.apache.ignite.ci.web.rest.monitoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory REST request timing storage.
 */
public class RestRequestTimingStorage {
    /** Max recent requests. */
    private static final int MAX_RECENT = Integer.getInteger("tcbot.rest.recentRequestsSize", 200);

    /** Aggregated stats by method and path. */
    private static final ConcurrentMap<String, StatAccumulator> STATS = new ConcurrentHashMap<>();

    /** Recent completed requests. */
    private static final ConcurrentLinkedDeque<RequestTiming> RECENT = new ConcurrentLinkedDeque<>();

    /**
     * @param method HTTP method.
     * @param path Path without query.
     * @param pathWithQuery Path with query.
     * @param status Response status.
     * @param durationMs Duration in millis.
     */
    public static void record(String method, String path, String pathWithQuery, int status, long durationMs) {
        RequestTiming timing = new RequestTiming(method, pathWithQuery, status, durationMs, System.currentTimeMillis());

        String key = method + " " + path;
        STATS.computeIfAbsent(key, k -> new StatAccumulator(method, path)).record(timing);

        RECENT.addFirst(timing);

        while (RECENT.size() > MAX_RECENT)
            RECENT.pollLast();
    }

    /**
     * @return Aggregated stats.
     */
    public static List<RequestStat> stats() {
        List<RequestStat> res = new ArrayList<>();

        STATS.values().forEach(stat -> res.add(stat.snapshot()));

        res.sort(Comparator.comparing((RequestStat stat) -> stat.totalMs).reversed());

        return res;
    }

    /**
     * @return Recent request timings.
     */
    public static List<RequestTiming> recent() {
        return new ArrayList<>(RECENT);
    }

    /**
     * Clears accumulated stats.
     */
    public static void reset() {
        STATS.clear();
        RECENT.clear();
    }

    /**
     * Mutable request stats accumulator.
     */
    private static class StatAccumulator {
        /** HTTP method. */
        private final String method;

        /** Path without query. */
        private final String path;

        /** Count. */
        private final AtomicLong count = new AtomicLong();

        /** Total duration. */
        private final AtomicLong totalMs = new AtomicLong();

        /** Max duration. */
        private final AtomicLong maxMs = new AtomicLong();

        /** Last duration. */
        private final AtomicLong lastMs = new AtomicLong();

        /** Last status. */
        private final AtomicInteger lastStatus = new AtomicInteger();

        /** Last request path with query. */
        private final AtomicReference<String> lastRequest = new AtomicReference<>();

        /**
         * @param method HTTP method.
         * @param path Path without query.
         */
        private StatAccumulator(String method, String path) {
            this.method = method;
            this.path = path;
        }

        /**
         * @param timing Timing.
         */
        private void record(RequestTiming timing) {
            count.incrementAndGet();
            totalMs.addAndGet(timing.durationMs);
            maxMs.accumulateAndGet(timing.durationMs, Math::max);
            lastMs.set(timing.durationMs);
            lastStatus.set(timing.status);
            lastRequest.set(timing.path);
        }

        /**
         * @return Snapshot.
         */
        private RequestStat snapshot() {
            RequestStat stat = new RequestStat();
            stat.method = method;
            stat.path = path;
            stat.count = count.get();
            stat.totalMs = totalMs.get();
            stat.avgMs = stat.count == 0 ? 0 : stat.totalMs / stat.count;
            stat.maxMs = maxMs.get();
            stat.lastMs = lastMs.get();
            stat.lastStatus = lastStatus.get();
            stat.lastRequest = lastRequest.get();

            return stat;
        }
    }
}
