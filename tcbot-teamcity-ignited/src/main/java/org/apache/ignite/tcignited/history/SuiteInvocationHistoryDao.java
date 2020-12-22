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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcignited.buildref.BuildRefDao;

import static java.util.concurrent.TimeUnit.HOURS;

/**
 * Suite invocation history access object.
 */
public class SuiteInvocationHistoryDao {
    /** Ignite provider. */
    @Inject
    private Provider<Ignite> igniteProvider;

    /** Suite history cache. */
    private IgniteCache<Long, SuiteInvocation> suiteHist;

    public void init() {
        CacheConfiguration<Long , SuiteInvocation> ccfg = CacheConfigs.getCacheV2Config("teamcitySuiteHistory");
        ccfg.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(new Duration(HOURS, 12)));
        ccfg.setEagerTtl(true);

        ccfg.setQueryEntities(Collections.singletonList(new QueryEntity(Long.class, SuiteInvocation.class)));

        Ignite ignite = igniteProvider.get();

        suiteHist = ignite.getOrCreateCache(ccfg);
    }

    @AutoProfiling
    public Map<Integer, SuiteInvocation> getSuiteRunHist(int srvId, int buildTypeId, int normalizedBranchName) {
        Map<Integer, SuiteInvocation> map = new HashMap<>();

        try (QueryCursor<Cache.Entry<Long, SuiteInvocation>> qryCursor = suiteHist.query(
            new SqlQuery<Long, SuiteInvocation>(SuiteInvocation.class, "srvId = ? and buildTypeId = ? and normalizedBranchName = ?")
                .setArgs(srvId, buildTypeId, normalizedBranchName))) {

            for (Cache.Entry<Long, SuiteInvocation> next : qryCursor) {
                Long key = next.getKey();
                int buildId = BuildRefDao.cacheKeyToBuildId(key);

                SuiteInvocation invocation = next.getValue();

                if(invocation.isOutdatedEntityVersion())
                    continue;

                map.put(buildId, invocation);
            }
        }

        return map;
    }

    @AutoProfiling
    public void putAll(int srvId, Map<Integer, SuiteInvocation> addl) {
        Map<Long, SuiteInvocation> data = new HashMap<>();

        addl.forEach((k, v) -> data.put(BuildRefDao.buildIdToCacheKey(srvId, k), v));

        suiteHist.putAll(data);
    }

    public void remove(long key) {
        suiteHist.remove(key);
    }

    public void removeAll(Set<Long> keys) {
        suiteHist.removeAll(keys);
    }
}
