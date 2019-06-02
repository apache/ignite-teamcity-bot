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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcservice.model.changes.ChangesList;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcservice.model.result.problems.ProblemOccurrence;
import org.apache.ignite.tcservice.model.result.stat.Statistics;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrencesFull;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class FatBuildDao {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(FatBuildDao.class);

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
    public FatBuildDao init() {
        buildsCache = igniteProvider.get().getOrCreateCache(CacheConfigs.getCacheV2Config(TEAMCITY_FAT_BUILD_CACHE_NAME));

        return this;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId
     * @param build Build data.
     * @param tests TestOccurrences one or several pages.
     * @param problems
     * @param statistics
     * @param changesList
     * @param existingBuild existing version of build in the DB.
     * @return Fat Build saved (if modifications detected), otherwise null.
     */
    @Nullable public FatBuildCompacted saveBuild(int srvIdMaskHigh,
                                       int buildId,
                                       @Nonnull Build build,
                                       @Nonnull List<TestOccurrencesFull> tests,
                                       @Nullable List<ProblemOccurrence> problems,
                                       @Nullable Statistics statistics,
                                       @Nullable ChangesList changesList,
                                       @Nullable FatBuildCompacted existingBuild) {
        Preconditions.checkNotNull(buildsCache, "init() was not called");
        Preconditions.checkNotNull(build, "build can't be null");

        FatBuildCompacted newBuild = new FatBuildCompacted(compactor, build);

        for (TestOccurrencesFull next : tests)
            newBuild.addTests(compactor, next.getTests());

        if (problems != null)
            newBuild.addProblems(compactor, problems);

        if (statistics != null)
            newBuild.statistics(compactor, statistics);

        if (changesList != null)
            newBuild.changes(extractChangeIds(changesList));

        if (existingBuild == null || !existingBuild.equals(newBuild)) {
            putFatBuild(srvIdMaskHigh, buildId, newBuild);

            return newBuild;
        }

        return null;
    }

    @AutoProfiling
    public void putFatBuild(int srvIdMaskHigh, int buildId, FatBuildCompacted newBuild) {
        buildsCache.put(buildIdToCacheKey(srvIdMaskHigh, buildId), newBuild);
    }

    public static int[] extractChangeIds(@Nonnull ChangesList changesList) {
        return changesList.changes().stream().mapToInt(
                        ch -> {
                            try {
                                return Integer.parseInt(ch.id);
                            } catch (Exception e) {
                                logger.error("Unable to parse change id ", e);
                                return -1;
                            }
                        }
                ).filter(id -> id > 0).toArray();
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId Build id.
     */
    public static long buildIdToCacheKey(int srvIdMaskHigh, int buildId) {
        return (long)buildId | (long)srvIdMaskHigh << 32;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId Build id.
     */
    @AutoProfiling
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
    public static boolean isKeyForServer(Long key, int srvId) {
        return key != null && key >> 32 == srvId;
    }

    public boolean containsKey(int srvIdMaskHigh, int buildId) {
        return buildsCache.containsKey(buildIdToCacheKey(srvIdMaskHigh, buildId));
    }

    public Stream<Cache.Entry<Long, FatBuildCompacted>> outdatedVersionEntries(int srvId) {
        return StreamSupport.stream(buildsCache.spliterator(), false)
            .filter(entry -> entry.getValue().isOutdatedEntityVersion())
            .filter(entry -> isKeyForServer(entry.getKey(), srvId));
    }
}
