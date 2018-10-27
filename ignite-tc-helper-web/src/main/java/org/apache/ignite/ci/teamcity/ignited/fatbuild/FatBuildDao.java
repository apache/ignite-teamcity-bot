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

package org.apache.ignite.ci.teamcity.ignited.fatbuild;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.validation.constraints.NotNull;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrencesFull;
import org.apache.ignite.ci.teamcity.ignited.BuildRefDao;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.internal.util.GridIntList;
import org.jetbrains.annotations.Nullable;

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
     * @param buildId
     * @param build Build data.
     * @param tests TestOccurrences one or several pages.
     * @param existingBuild existing version of build in the DB.
     * @return Fat Build saved (if modifications detected), otherwise null.
     */
    public FatBuildCompacted saveBuild(long srvIdMaskHigh,
        int buildId,
        @NotNull Build build,
        List<TestOccurrencesFull> tests,
        @Nullable FatBuildCompacted existingBuild) {
        Preconditions.checkNotNull(buildsCache, "init() was not called");
        Preconditions.checkNotNull(build, "build can't be null");

        FatBuildCompacted newBuild = new FatBuildCompacted(compactor, build);

        for (TestOccurrencesFull next : tests)
            newBuild.addTests(compactor, next.getTests());

        if (existingBuild == null || !existingBuild.equals(newBuild)) {
            buildsCache.put(buildIdToCacheKey(srvIdMaskHigh, buildId), newBuild);

            return newBuild;
        }

        return null;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId Build id.
     */
    public static long buildIdToCacheKey(long srvIdMaskHigh, int buildId) {
        return (long)buildId | srvIdMaskHigh << 32;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId Build id.
     */
    public FatBuildCompacted getFatBuild(int srvIdMaskHigh, int buildId) {
        Preconditions.checkNotNull(buildsCache, "init() was not called");

        return buildsCache.get(buildIdToCacheKey(srvIdMaskHigh, buildId));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildsIds Builds ids.
     */
    public Map<Long, FatBuildCompacted> getAllFatBuilds(int srvIdMaskHigh, Collection<Integer> buildsIds) {
        Preconditions.checkNotNull(buildsCache, "init() was not called");

        Set<Long> ids = buildsIds.stream()
            .filter(Objects::nonNull)
            .map(buildId -> buildIdToCacheKey(srvIdMaskHigh, buildId))
            .collect(Collectors.toSet());

        return buildsCache.getAll(ids);
    }

    /**
     * @param key Key.
     * @param srvId Server id.
     */
    private boolean isKeyForServer(Long key, int srvId) {
        return key!=null && key >> 32 == srvId;
    }

    @AutoProfiling
    public int[] getAllIds(int srvId) {
        GridIntList res = new GridIntList(buildsCache.size());

        StreamSupport.stream(buildsCache.spliterator(), false)
                .map(Cache.Entry::getKey)
                .filter(entry -> isKeyForServer(entry, srvId))
                .map(BuildRefDao::cacheKeyToBuildId)
                .forEach(res::add);

        return res.array();
    }
}
