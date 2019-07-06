package org.apache.ignite.tcignited.buildtime;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BuildTimeResult {
    private Map<Long, BuildTimeRecord> btByBuildType = new HashMap<>();

    public void add(int srvId, int buildTypeId, long runningTime) {
        btByBuildType.computeIfAbsent(buildTypeToCacheKey(srvId, buildTypeId), k->new BuildTimeRecord())
                .addInvocation(runningTime);
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


    public List<Map.Entry<Long, BuildTimeRecord>> topBuildTypes(Set<Integer> availableServers, long minAvgDurationMs) {
        return btByBuildType.entrySet().stream()
                .filter(e->{
                    Long key = e.getKey();
                    int srvId = cacheKeyToSrvId(key);
                    return availableServers.contains(srvId);
                })
                .filter(e->{
                    return e.getValue().avgDuration() > minAvgDurationMs;
                })
                .sorted(Comparator.comparing(new Function<Map.Entry<Long, BuildTimeRecord>,
                        Long>() {
                    @Override
                    public Long apply(  Map.Entry<Long, BuildTimeRecord> entry) {
                        return  entry.getValue().avgDuration();
                    }
                })
                .reversed())
                .limit(5)
                .collect(Collectors.toList());
    }
}
