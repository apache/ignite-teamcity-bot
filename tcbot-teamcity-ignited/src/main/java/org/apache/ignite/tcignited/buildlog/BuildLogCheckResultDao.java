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
package org.apache.ignite.tcignited.buildlog;

import java.util.Set;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.buildref.BuildRefDao;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;

public class BuildLogCheckResultDao {
    /** Cache name */
    public static final String TEAMCITY_CHANGE_CACHE_NAME = "buildLogCheckResult";

    /** Ignite provider. */
    @Inject
    private Provider<Ignite> igniteProvider;

    /** Change Compacted cache. */
    private IgniteCache<Long, LogCheckResultCompacted> logsCache;

    public static long getCacheKey(String serverCode, int buildId) {
        int serverId = ITeamcityIgnited.serverIdToInt(serverCode);

        return BuildRefDao.buildIdToCacheKey(serverId, buildId);
    }

    /** */
    public void init() {
        CacheConfiguration<Long, LogCheckResultCompacted> cfg = CacheConfigs.getCacheV2Config(TEAMCITY_CHANGE_CACHE_NAME);

        logsCache = igniteProvider.get().getOrCreateCache(cfg);
    }

    @Nullable public LogCheckResultCompacted get(String srvCode, int buildId) {
        return logsCache.get(getCacheKey(srvCode, buildId));
    }

    public void put(String srvCode, int buildId, LogCheckResultCompacted logCheckResultCompacted) {
        logsCache.put(getCacheKey(srvCode, buildId), logCheckResultCompacted);
    }

    public void remove(long key) {
        logsCache.remove(key);
    }

    public void removeAll(Set<Long> keys) {
        logsCache.removeAll(keys);
    }
}
