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

package org.apache.ignite.ci.teamcity.ignited.buildcondition;

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

import static org.apache.ignite.tcbot.persistence.CacheConfigs.getCache8PartsConfig;

public class BuildConditionDao {
    /** Cache name*/
    public static final String BUILDS_CONDITIONS_CACHE_NAME = "buildsConditions";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, BuildConditionCompacted> buildsCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * Initialize
     */
    public void init () {
        Ignite ignite = igniteProvider.get();
        buildsCache = ignite.getOrCreateCache(getCache8PartsConfig(BUILDS_CONDITIONS_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId Build id.
     */
    private long buildIdToCacheKey(long srvIdMaskHigh, int buildId) {
        return (long)buildId | srvIdMaskHigh << 32;
    }

    public BuildCondition getBuildCondition(long srvIdMaskHigh, int buildId) {
        long key = buildIdToCacheKey(srvIdMaskHigh, buildId);

        return buildsCache.containsKey(key) ? buildsCache.get(key).toBuildCondition(compactor) : null;
    }

    public boolean setBuildCondition(long srvIdMaskHigh, BuildCondition cond) {
        long key = buildIdToCacheKey(srvIdMaskHigh, cond.buildId);

        if (cond.isValid)
            return buildsCache.remove(key);
        else {
            if (!buildsCache.containsKey(key)) {
                buildsCache.put(key, new BuildConditionCompacted(compactor, cond));

                return true;
            }
        }

        return false;
    }

    public void remove(long key) {
        buildsCache.remove(key);
    }

    public void removeAll(Set<Long> keys) {
        buildsCache.removeAll(keys);
    }
}
