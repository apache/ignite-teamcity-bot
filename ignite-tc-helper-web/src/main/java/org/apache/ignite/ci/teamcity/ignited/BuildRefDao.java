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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;

public class BuildRefDao {
    /** Cache name*/
    public static final String TEAMCITY_BUILD_CACHE_NAME = "teamcityBuild";

    /** Ignite provider. */
    private @Inject Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, BuildRefCompacted> buildsCache;

    public void init () {
        Ignite ignite = igniteProvider.get();
        buildsCache = ignite.getOrCreateCache(TcHelperDb.getCacheV2Config(TEAMCITY_BUILD_CACHE_NAME));
    }

    public Stream<BuildRef> getAllBuilds(long srvIdMaskHigh) {
        return StreamSupport.stream(buildsCache.spliterator(), false)
            .filter(entry -> entry.getKey() >> 32 == srvIdMaskHigh)
            .map(javax.cache.Cache.Entry::getValue)
            .map(BuildRefCompacted::toBuildRef);
    }

    public int saveChunk(long srvIdMaskHigh, List<BuildRef> ghData) {
        Set<Long> ids = ghData.stream().map(BuildRef::getId)
            .filter(Objects::nonNull)
            .map(buildId -> buildIdToCacheKey(srvIdMaskHigh, buildId))
            .collect(Collectors.toSet());

        Map<Long, BuildRefCompacted> existingEntries = buildsCache.getAll(ids);
        Map<Long, BuildRefCompacted> entriesToPut = new TreeMap<>();

        List<BuildRefCompacted> collect = ghData.stream().map(BuildRefCompacted::new)
            .collect(Collectors.toList());

        for (BuildRefCompacted next : collect) {
            long cacheKey = buildIdToCacheKey(srvIdMaskHigh, next.buildId );
            BuildRefCompacted buildPersisted = existingEntries.get(cacheKey);

            if (buildPersisted == null || !buildPersisted.equals(next))
                entriesToPut.put(cacheKey, next);
        }

        int size = entriesToPut.size();
        if (size != 0)
            buildsCache.putAll(entriesToPut);
        return size;
    }

    private long buildIdToCacheKey(long srvIdMaskHigh, int buildId) {
        return (long)buildId | srvIdMaskHigh << 32;
    }
}
