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

package org.apache.ignite.ci.teamcity.ignited.runhist;

import java.util.List;
import javax.cache.processor.MutableEntry;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.teamcity.ignited.IRunHistory;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.configuration.CacheConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.ci.teamcity.ignited.runhist.RunHistSync.normalizeBranch;

/**
 *
 */
public class RunHistCompactedDao {
    /** Cache name. */
    public static final String TEST_HIST_CACHE_NAME = "teamcityTestRunHistV0";

    /** Build Start time Cache name. */
    public static final String BUILD_START_TIME_CACHE_NAME = "teamcityBuildStartTimeV0";

    /** Suites history Cache name.*/
    public static final String SUITE_HIST_CACHE_NAME = "teamcitySuiteRunHist";


    /** Ignite provider. */
    @Inject
    private Provider<Ignite> igniteProvider;

    /** Test history cache. */
    private IgniteCache<RunHistKey, RunHistCompacted> testHistCache;

    /** Suite history cache. */
    private IgniteCache<RunHistKey, RunHistCompacted> suiteHistCacheName;

    /** Build start time. */
    private IgniteCache<Long, Long> buildStartTime;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * Initialize
     */
    public void init () {
        Ignite ignite = igniteProvider.get();

        final CacheConfiguration<RunHistKey, RunHistCompacted> cfg = TcHelperDb.getCacheV2Config(TEST_HIST_CACHE_NAME);

        cfg.setQueryEntities(Collections.singletonList(new QueryEntity(RunHistKey.class, RunHistCompacted.class)));

        testHistCache = ignite.getOrCreateCache(cfg);

        buildStartTime = ignite.getOrCreateCache(TcHelperDb.getCacheV2Config(BUILD_START_TIME_CACHE_NAME));

        final CacheConfiguration<RunHistKey, RunHistCompacted> cfg2 = TcHelperDb.getCache8PartsConfig(SUITE_HIST_CACHE_NAME);

        cfg2.setQueryEntities(Collections.singletonList(new QueryEntity(RunHistKey.class, RunHistCompacted.class)));

        suiteHistCacheName = ignite.getOrCreateCache(cfg2);

        buildStartTime = ignite.getOrCreateCache(TcHelperDb.getCacheV2Config(BUILD_START_TIME_CACHE_NAME));
    }

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
        return buildStartTime.containsKey(buildIdToCacheKey(srvId, buildId));
    }

    @AutoProfiling
    public boolean setBuildProcessed(int srvId, int buildId, long ts) {
        return buildStartTime.putIfAbsent(buildIdToCacheKey(srvId, buildId), ts);
    }

    @AutoProfiling
    public Integer addTestInvocations(RunHistKey histKey, List<Invocation> list) {
        if (list.isEmpty())
            return 0;

        return testHistCache.invoke(histKey, RunHistCompactedDao::processEntry, list);
    }


    @AutoProfiling
    public Integer addSuiteInvocations(RunHistKey histKey, List<Invocation> list) {
        if (list.isEmpty())
            return 0;

        return suiteHistCacheName.invoke(histKey, RunHistCompactedDao::processEntry, list);
    }

    @NotNull public static Integer processEntry(MutableEntry<RunHistKey, RunHistCompacted> entry, Object[] parms) {
        int cnt = 0;

        RunHistCompacted hist = entry.getValue();

        if (hist == null)
            hist = new RunHistCompacted(entry.getKey());

        int initHashCode = hist.hashCode();

        List<Invocation> invocationList = (List<Invocation>)parms[0];

        for (Invocation invocation : invocationList) {
            boolean added = hist.addTestRun(
                invocation.buildId(),
                invocation);

            if (added)
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
    public IRunHistory getSuiteRunHist(int srvId, String suiteId, @Nullable String branch) {
        RunHistKey key = getKey(srvId, suiteId, branch);
        if (key == null)
            return null;

        return suiteHistCacheName.get(key);
    }
}
