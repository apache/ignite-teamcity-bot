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

import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.tcbot.common.TcBotConst;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcignited.buildref.BuildRefDao;

/**
 */
public class BuildStartTimeStorage {
    /** Build Start time Cache name. */
    public static final String BUILD_START_TIME_CACHE_NAME = "teamcityBuildStartTime";

    /** Ignite provider. */
    @Inject
    private Provider<Ignite> igniteProvider;

    /** Build start time. */
    private IgniteCache<Long, Long> buildStartTime;

    /**
     * Biggest build ID, which is older than particular days count.
     * Map: server ID-> Array of build Ids
     * Array[0] = max build ID
     * Array[1] = max build ID older than 1 day
     */
    private final ConcurrentMap<Integer, AtomicIntegerArray> maxBuildIdOlderThanDays = new ConcurrentHashMap<>();

    /** Millis in day. */
    private static final long MILLIS_IN_DAY = Duration.ofDays(1).toMillis();

    /**
     * Initialize
     */
    public void init() {
        Ignite ignite = igniteProvider.get();

        buildStartTime = ignite.getOrCreateCache(CacheConfigs.getCacheV2Config(BUILD_START_TIME_CACHE_NAME));
    }

    /**
     * @param srvId Server id mask high.
     * @param buildId Build id.
     */
    public static long buildIdToCacheKey(long srvId, int buildId) {
        return (long)buildId | srvId << 32;
    }

    /**
     * @param srvId Server id.
     * @param buildId Build id.
     */
    @AutoProfiling
    @Nullable public Long getBuildStartTime(int srvId, int buildId) {
        Long ts = buildStartTime.get(buildIdToCacheKey(srvId, buildId));
        if (ts == null || ts <= 0)
            return null;

        processBuildForBorder(srvId, buildId, ts);

        return ts;
    }

    public boolean setBuildStartTime(int srvId, int buildId, long ts) {
        if (ts <= 0)
            return false;

        processBuildForBorder(srvId, buildId, ts);

        return buildStartTime.putIfAbsent(buildIdToCacheKey(srvId, buildId), ts);
    }

    @AutoProfiling
    public boolean setBuildProcessed(int srvId, int buildId, long ts) {
        if (ts <= 0)
            return false;

        processBuildForBorder(srvId, buildId, ts);

        return buildStartTime.putIfAbsent(buildIdToCacheKey(srvId, buildId), ts);
    }

    private static Set<Long> buildsIdsToCacheKeys(int srvId, Collection<Integer> ids) {
        return ids.stream()
            .filter(Objects::nonNull).map(id -> buildIdToCacheKey(srvId, id)).collect(Collectors.toSet());
    }

    public Map<Integer, Long> getBuildsStartTime(int srvId, Set<Integer> ids) {
        Set<Long> cacheKeys = buildsIdsToCacheKeys(srvId, ids);

        Map<Integer, Long> res = new HashMap<>();

        buildStartTime.getAll(cacheKeys).forEach((k, ts) -> {
            if (ts != null && ts > 0) {
                int buildId = BuildRefDao.cacheKeyToBuildId(k);

                res.put(buildId, ts);

                processBuildForBorder(srvId, buildId, ts);
            }
        });

        return res;
    }

    public void setBuildsStartTime(int srvId, Map<Integer, Long> builds) {
        Map<Long, Long> res = new HashMap<>();

        builds.forEach((buildId, ts) -> {
            if (ts != null && ts > 0) {
                res.put(buildIdToCacheKey(srvId, buildId), ts);

                processBuildForBorder(srvId, buildId, ts);
            }
        });

        buildStartTime.putAll(res);
    }

    private void processBuildForBorder(int srvId, Integer buildId, Long ts) {
        if (ts == null || ts <= 0)
            return;

        AtomicIntegerArray arr = maxBuildIdOlderThanDays.computeIfAbsent(srvId,
            k -> new AtomicIntegerArray(TcBotConst.BUILD_MAX_DAYS + 1));

        long ageMs = System.currentTimeMillis() - ts;
        if (ageMs < 0)
            return;

        long days = ageMs / MILLIS_IN_DAY;
        if (days > TcBotConst.BUILD_MAX_DAYS)
            days = TcBotConst.BUILD_MAX_DAYS;

        arr.accumulateAndGet((int)days, buildId, Math::max);
    }

    @Nullable public Integer getBorderForAgeForBuildId(int srvId, int ageDays) {
        AtomicIntegerArray arr = maxBuildIdOlderThanDays.get(srvId);
        if (arr == null)
            return null;

        for (int i = ageDays; i < TcBotConst.BUILD_MAX_DAYS; i++) {
            int buildId = arr.get(i);
            if (buildId != 0)
                return buildId;
        }

        return null;
    }

    public void remove(long key) {
        buildStartTime.remove(key);
    }

    public void removeAll(Set<Long> keys) {
        buildStartTime.removeAll(keys);
    }

}
