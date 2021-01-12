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

package org.apache.ignite.tcbot.engine.newtests;

import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.tcbot.persistence.CacheConfigs;

/**
 * The storage contains tests which were identified as new tests in the tcbot visa
 */
public class NewTestsStorage {
    /** */
    @Inject
    private Provider<Ignite> igniteProvider;

    /** */
    private IgniteCache<NewTestKey, NewTestInfo> cache() {
        return botNewTestsCache(getIgnite());
    }

    /** */
    private Ignite getIgnite() {
        return igniteProvider.get();
    }

    /** */
    public static IgniteCache<NewTestKey, NewTestInfo> botNewTestsCache(Ignite ignite) {
        CacheConfiguration<NewTestKey, NewTestInfo> ccfg = CacheConfigs.getCache8PartsConfig("newTestsCache");

        return ignite.getOrCreateCache(ccfg);
    }

    /** */
    public boolean isNewTest(String srvId, Long testId, String baseBranch, String branch) {
        NewTestInfo savedTest = cache().get(new NewTestKey(srvId, testId, baseBranch));

        if (savedTest == null)
            return true;
        else
            return savedTest.branch().startsWith(branch);
    }

    /** */
    public boolean isNewTestAndPut(String srvId, Long testId, String baseBranch, String branch) {
        NewTestKey testKey = new NewTestKey(srvId, testId, baseBranch);

        NewTestInfo savedTest = cache().get(testKey);

        if (savedTest == null) {
            cache().put(testKey, new NewTestInfo(branch, System.currentTimeMillis()));
            return true;
        }
        else
            return savedTest.branch().startsWith(branch);
    }

    /** */
    public void removeOldTests(long thresholdDate) {
        ScanQuery<NewTestKey, NewTestInfo> scan =
            new ScanQuery<>((key, testInfo) -> testInfo.timestamp() < thresholdDate);

        for (Cache.Entry<NewTestKey, NewTestInfo> entry : cache().query(scan))
            cache().remove(entry.getKey());
    }
}
