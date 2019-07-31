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

import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicSequence;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.tcbot.persistence.CacheConfigs;

public class DefectStorage {
    /** Bot detected defects. */
    public static final String BOT_DETECTED_DEFECTS = "botDetectedDefects";
    /** Bot detected defects sequence. */
    public static final String BOT_DETECTED_DEFECTS_SEQ = "botDetectedDefectsSeq";

    @Inject
    private Provider<Ignite> igniteProvider;

    public DefectStorage() {
    }

    private IgniteAtomicSequence sequence() {

        Ignite ignite = getIgnite();
        return ignite.atomicSequence(BOT_DETECTED_DEFECTS_SEQ, 0, true);
    }

    private IgniteCache<Integer, DefectCompacted> cache() {
        return botDetectedIssuesCache(getIgnite());
    }

    private Ignite getIgnite() {
        return igniteProvider.get();
    }

    public static IgniteCache<Integer, DefectCompacted> botDetectedIssuesCache(Ignite ignite) {
        CacheConfiguration<Integer, DefectCompacted> ccfg = CacheConfigs.getCacheV2TxConfig(BOT_DETECTED_DEFECTS);

        ccfg.setQueryEntities(Collections.singletonList(new QueryEntity(Integer.class, DefectCompacted.class)));

        return ignite.getOrCreateCache(ccfg);
    }
}
