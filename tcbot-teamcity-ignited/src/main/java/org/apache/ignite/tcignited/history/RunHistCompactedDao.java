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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.cache.processor.MutableEntry;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCluster;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistKey;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.tcbot.common.TcBotConst;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.GuavaCached;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.buildref.BuildRefDao;

import static org.apache.ignite.tcignited.history.RunHistSync.normalizeBranch;

/**
 * TODO: rename to build start time storage
 */
public class RunHistCompactedDao {
    /** Cache name. */
    public static final String TEST_HIST_CACHE_NAME = "teamcityTestRunHist";

    /** Build Start time Cache name. */
    public static final String BUILD_START_TIME_CACHE_NAME = "teamcityBuildStartTime";

    /** Suites history Cache name. */
    public static final String SUITE_HIST_CACHE_NAME = "teamcitySuiteRunHist";

    /** Ignite provider. */
    @Inject
    private Provider<Ignite> igniteProvider;

    /** Test history cache. */
    @Deprecated
    private IgniteCache<RunHistKey, RunHistCompacted> testHistCache;

    /** Suite history cache. */
    @Deprecated
    private IgniteCache<RunHistKey, RunHistCompacted> suiteHistCache;

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

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * Initialize
     */
    public void init() {
        Ignite ignite = igniteProvider.get();

        final CacheConfiguration<RunHistKey, RunHistCompacted> cfg = CacheConfigs.getCacheV2Config(TEST_HIST_CACHE_NAME);

        cfg.setQueryEntities(Collections.singletonList(new QueryEntity(RunHistKey.class, RunHistCompacted.class)));

        testHistCache = ignite.getOrCreateCache(cfg);

        final CacheConfiguration<RunHistKey, RunHistCompacted> cfg2 = CacheConfigs.getCache8PartsConfig(SUITE_HIST_CACHE_NAME);

        cfg2.setQueryEntities(Collections.singletonList(new QueryEntity(RunHistKey.class, RunHistCompacted.class)));

        suiteHistCache = ignite.getOrCreateCache(cfg2);

        buildStartTime = ignite.getOrCreateCache(CacheConfigs.getCacheV2Config(BUILD_START_TIME_CACHE_NAME));
    }

    @GuavaCached(maximumSize = 50000, expireAfterWriteSecs = 120, softValues = true)
    public IRunHistory getTestRunHist(int srvIdMaskHigh, String name, @Nullable String branch) {
        RunHistKey key = getKey(srvIdMaskHigh, name, branch);
        if (key == null)
            return null;

        return testHistCache.get(key);
    }

    @Nullable public RunHistKey getKey(int srvIdMaskHigh, String testOrSuiteName, @Nullable String branch) {
        final Integer testName = compactor.getStringIdIfPresent(testOrSuiteName);
        if (testName == null)
            return null;

        final Integer branchId = compactor.getStringIdIfPresent(normalizeBranch(branch));
        if (branchId == null)
            return null;

        return new RunHistKey(srvIdMaskHigh, testName, branchId);
    }

    /**
     * @param srvId Server id mask high.
     * @param buildId Build id.
     */
    public static long buildIdToCacheKey(long srvId, int buildId) {
        return (long)buildId | srvId << 32;
    }

    @AutoProfiling
    public boolean buildWasProcessed(int srvId, int buildId) {
        return getBuildStartTime(srvId, buildId) != null;
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

    @AutoProfiling
    @Deprecated
    public Integer addTestInvocations(RunHistKey histKey, List<Invocation> list) {
        if (list.isEmpty())
            return 0;

        return testHistCache.invoke(histKey, RunHistCompactedDao::processEntry, list);
    }

    @AutoProfiling
    public Integer addSuiteInvocations(RunHistKey histKey, List<Invocation> list) {
        if (list.isEmpty())
            return 0;

        return suiteHistCache.invoke(histKey, RunHistCompactedDao::processEntry, list);
    }

    @Nonnull public static Integer processEntry(MutableEntry<RunHistKey, RunHistCompacted> entry, Object[] parms) {
        int cnt = 0;

        RunHistCompacted hist = entry.getValue();

        if (hist == null)
            hist = new RunHistCompacted(entry.getKey());

        int initHashCode = hist.hashCode();

        List<Invocation> invocationList = (List<Invocation>)parms[0];

        for (Invocation invocation : invocationList) {
            if (hist.addInvocation(invocation))
                cnt++;
        }

        if (cnt > 0 || hist.hashCode() != initHashCode)
            entry.setValue(hist);

        return cnt;
    }

    /**
     * @param srvId Server id.
     * @param suiteId Suite id.
     * @param branch Branch.
     */
    @GuavaCached(maximumSize = 200, expireAfterWriteSecs = 120, softValues = true)
    public IRunHistory getSuiteRunHist(int srvId, String suiteId, @Nullable String branch) {
        RunHistKey key = getKey(srvId, suiteId, branch);
        if (key == null)
            return null;

        return suiteHistCache.get(key);
    }

    public IRunStat getSuiteRunStatAllBranches(int srvIdMaskHigh, String btId) {
        final Integer testName = compactor.getStringIdIfPresent(btId);
        if (testName == null)
            return null;

        AtomicInteger runs = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        try (QueryCursor<Cache.Entry<RunHistKey, RunHistCompacted>> qryCursor = suiteHistCache.query(
            new SqlQuery<RunHistKey, RunHistCompacted>(RunHistCompacted.class, "testOrSuiteName = ? and srvId = ?")
                .setArgs(testName, srvIdMaskHigh))) {

            for (Cache.Entry<RunHistKey, RunHistCompacted> next : qryCursor) {
                RunHistCompacted val = next.getValue();

                runs.addAndGet(val.getRunsCount());
                failures.addAndGet(val.getFailuresCount());
            }
        }

        return new IRunStat() {
            @Override public int getRunsCount() {
                return runs.get();
            }

            @Override public int getFailuresCount() {
                return failures.get();
            }
        };
    }

    public void disableWal() {
        IgniteCluster cluster = igniteProvider.get().cluster();
        if(!cluster.isWalEnabled(testHistCache.getName()))
            return;

        System.err.println("Too much test entries to be saved, disabling WAL");

        cluster.disableWal(testHistCache.getName());
        cluster.disableWal(suiteHistCache.getName());
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

}
