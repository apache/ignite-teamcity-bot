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

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.teamcity.ignited.IRunHistory;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.configuration.CacheConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collections;

public class RunHistCompactedDao {
    /** Cache name*/
    public static final String TEST_HIST_CACHE_NAME = "testRunHist";

    /** Ignite provider. */
    @Inject
    private Provider<Ignite> igniteProvider;

    /** Test history cache. */
    private IgniteCache<RunHistKey, RunHistCompacted> testHistCache;

    /** Build start time. */
    private IgniteCache<Integer, Long> buildStartTime;

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
    }

    public void save(RunHistKey k, RunHistCompacted v) {
        testHistCache.put(k, v);
    }

    public IRunHistory getTestRunHist(int srvIdMaskHigh, String name, String branch) {
        final Integer testName = compactor.getStringIdIfPresent(name);
        if (testName == null)
            return null;

        final Integer branchId = compactor.getStringIdIfPresent(branch);
        if (branchId == null)
            return null;

        return testHistCache.get(new RunHistKey(srvIdMaskHigh, testName, branchId));
    }
}
