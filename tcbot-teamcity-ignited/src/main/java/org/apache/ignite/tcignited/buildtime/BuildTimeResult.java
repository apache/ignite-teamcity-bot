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
package org.apache.ignite.tcignited.buildtime;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BuildTimeResult {
    /** Build time summary by build type, map from (srvId||buildTypeId)->Invocations summary. */
    private Map<Long, BuildTimeRecord> btByBuildType = new HashMap<>();
    /** Timed out builds: Build time summary by build type, map from (srvId||buildTypeId)->Invocations summary. */
    private Map<Long, BuildTimeRecord> timedOutByBuildType = new HashMap<>();

    public void addBuild(int srvId, int buildTypeId, long runningTime, boolean hasTimeout) {
        long cacheKey = buildTypeToCacheKey(srvId, buildTypeId);
        btByBuildType.computeIfAbsent(cacheKey, k -> new BuildTimeRecord()).addInvocation(runningTime);

        if (hasTimeout)
            timedOutByBuildType.computeIfAbsent(cacheKey, k -> new BuildTimeRecord()).addInvocation(runningTime);
    }

    public static long buildTypeToCacheKey(long srvId, int btId) {
        return (long)btId | srvId << 32;
    }

    public static int cacheKeyToSrvId(long cacheKey) {
        return (int)(cacheKey >> 32);
    }

    public static int cacheKeyToBuildType(Long cacheKey) {
        long l = cacheKey << 32;
        return (int)(l >> 32);
    }


    public List<Map.Entry<Long, BuildTimeRecord>> topByBuildTypes(Set<Integer> availableServers,
        long minAvgDurationMs,
        int maxCnt,
        long totalDurationMs) {
        return filtered(btByBuildType, availableServers, minAvgDurationMs, totalDurationMs)
                .sorted(Comparator.comparing(
                        (Function<Map.Entry<Long, BuildTimeRecord>, Long>) entry -> entry.getValue().totalDuration())
                        .reversed())
                .limit(maxCnt)
                .collect(Collectors.toList());
    }

    public List<Map.Entry<Long, BuildTimeRecord>> topTimeoutsByBuildTypes(Set<Integer> availableServers,
        long minAvgDurationMs,
        int maxCnt,
        long totalDurationMs) {
        return filtered(timedOutByBuildType, availableServers, minAvgDurationMs, totalDurationMs)
                .sorted(Comparator.comparing(
                        (Function<Map.Entry<Long, BuildTimeRecord>, Long>) entry -> entry.getValue().totalDuration())
                        .reversed())
                .limit(maxCnt)
                .collect(Collectors.toList());
    }

    private Stream<Map.Entry<Long, BuildTimeRecord>> filtered(
        Map<Long, BuildTimeRecord> map,
        Set<Integer> availableSrvs,
        long minAvgDurationMs,
        long totalDurationMs) {
        return map.entrySet().stream()
                .filter(e -> {
                    Long key = e.getKey();
                    int srvId = cacheKeyToSrvId(key);
                    return availableSrvs.contains(srvId);
                })
            .filter(e -> e.getValue().avgDuration() > minAvgDurationMs)
            .filter(e -> e.getValue().totalDuration() > totalDurationMs);
    }
}
