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
package org.apache.ignite.ci.tcbot.conf;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.tcbot.common.interceptor.GuavaCached;
import org.apache.ignite.tcbot.engine.conf.BranchTrackedPersisted;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranchesConfig;
import org.apache.ignite.tcbot.persistence.CacheConfigs;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class MixedFilesAndDbTrackedBranchesConfig implements ITrackedBranchesConfig {
    public static final String TRACKED_BRANCHES = "trackedBranches";
    @Inject
    private LocalFilesBasedConfig filesBasedCfg;

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;


    @Override
    public Stream<ITrackedBranch> branchesStream() {
        //todo internal cached version,
       // @GuavaCached(softValues = true, expireAfterWriteSecs = 3 * 60)
        IgniteCache<String, BranchTrackedPersisted> cache = igniteProvider.get().getOrCreateCache(CacheConfigs.getCache8PartsConfig(TRACKED_BRANCHES));

        Map<String, BranchTrackedPersisted> res = new HashMap<>();
        filesBasedCfg.getConfig()
                .getBranches()
                .stream()
                .map(BranchTrackedPersisted::new).forEach(btp -> {
            res.put(btp.name(), btp);
        });

        Map<String, BranchTrackedPersisted> dbValues = new HashMap<>();
        cache.forEach((entry) -> {
            String key = entry.getKey();
            dbValues.put(key, entry.getValue());
        });

        res.putAll(dbValues); // override config all values by values from DB, enforcing soft del as a priority

        return res.values().stream().filter(BranchTrackedPersisted::isAlive).map(v -> v);
    }
}
