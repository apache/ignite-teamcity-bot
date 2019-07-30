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
package org.apache.ignite.tcbot.engine.defect;

import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.tcbot.persistence.CacheConfigs;

public class DefectStorage {
    public static final String BOT_DETECTED_ISSUES = "botDetectedDefects";

    @Inject
    private Provider<Ignite> igniteProvider;

    public DefectStorage() {
    }

    private IgniteCache<DefectKey, DefectCompacted> cache() {
        return botDetectedIssuesCache(getIgnite());
    }

    private Ignite getIgnite() {
        return igniteProvider.get();
    }

    public static IgniteCache<DefectKey, DefectCompacted> botDetectedIssuesCache(Ignite ignite) {
        return ignite.getOrCreateCache(CacheConfigs.getCacheV2TxConfig(BOT_DETECTED_ISSUES));
    }
}
