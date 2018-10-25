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

package org.apache.ignite.ci.teamcity.ignited;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.configuration.CacheConfiguration;

/**
 *
 */
public class FatBuildDao {
    /** Cache name */
    public static final String TEAMCITY_FAT_BUILD_CACHE_NAME = "teamcityFatBuild";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, FatBuildCompacted> buildsCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     *
     */
    public void init() {
        buildsCache = igniteProvider.get().getOrCreateCache(TcHelperDb.getCacheV2Config(TEAMCITY_FAT_BUILD_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param ghData Gh data.
     */
    public int saveChunk(long srvIdMaskHigh, List<Build> ghData) {
        Set<Long> ids = ghData.stream().map(BuildRef::getId)
            .filter(Objects::nonNull)
            .map(buildId -> buildIdToCacheKey(srvIdMaskHigh, buildId))
            .collect(Collectors.toSet());

        Map<Long, FatBuildCompacted> existingEntries = buildsCache.getAll(ids);
        Map<Long, FatBuildCompacted> entriesToPut = new TreeMap<>();

        List<FatBuildCompacted> collect = ghData.stream()
            .map(ref -> new FatBuildCompacted(compactor, ref))
            .collect(Collectors.toList());

        for (FatBuildCompacted next : collect) {
            long cacheKey = buildIdToCacheKey(srvIdMaskHigh, next.id());
            FatBuildCompacted buildPersisted = existingEntries.get(cacheKey);

            if (buildPersisted == null || !buildPersisted.equals(next))
                entriesToPut.put(cacheKey, next);
        }

        int size = entriesToPut.size();
        if (size != 0)
            buildsCache.putAll(entriesToPut);

        return size;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId Build id.
     */
    private long buildIdToCacheKey(long srvIdMaskHigh, int buildId) {
        return (long)buildId | srvIdMaskHigh << 32;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId Build id.
     */
    public FatBuildCompacted getFatBuild(int srvIdMaskHigh, int buildId) {
        return buildsCache.get(buildIdToCacheKey(srvIdMaskHigh, buildId));
    }
}
