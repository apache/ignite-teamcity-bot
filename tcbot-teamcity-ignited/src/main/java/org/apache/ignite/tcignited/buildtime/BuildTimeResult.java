package org.apache.ignite.tcignited.buildtime;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BuildTimeResult {
    private Map<Long, BuildTimeRecord> btByBuildType = new HashMap<>();
    private Map<Long, BuildTimeRecord> timedOutByBuildType = new HashMap<>();

    public void add(int srvId, int buildTypeId, long runningTime, boolean hasTimeout) {
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
                                                                  int maxCnt) {
        return filtered(btByBuildType, availableServers, minAvgDurationMs)
                .sorted(Comparator.comparing(
                        (Function<Map.Entry<Long, BuildTimeRecord>, Long>) entry -> entry.getValue().avgDuration())
                        .reversed())
                .limit(maxCnt)
                .collect(Collectors.toList());
    }

    public List<Map.Entry<Long, BuildTimeRecord>> topTimeoutsByBuildTypes(Set<Integer> availableServers,
                                                                  long minAvgDurationMs,
                                                                  int maxCnt) {
        return filtered(timedOutByBuildType, availableServers, minAvgDurationMs)
                .sorted(Comparator.comparing(
                        (Function<Map.Entry<Long, BuildTimeRecord>, Long>) entry -> entry.getValue().avgDuration())
                        .reversed())
                .limit(maxCnt)
                .collect(Collectors.toList());
    }

    private Stream<Map.Entry<Long, BuildTimeRecord>> filtered(Map<Long, BuildTimeRecord> map, Set<Integer> availableServers, long minAvgDurationMs) {
        return map.entrySet().stream()
                .filter(e -> {
                    Long key = e.getKey();
                    int srvId = cacheKeyToSrvId(key);
                    return availableServers.contains(srvId);
                })
                .filter(e -> e.getValue().avgDuration() > minAvgDurationMs);
    }
}
