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

package org.apache.ignite.ci.teamcity.ignited.buildtype;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
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
import javax.validation.constraints.NotNull;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.tcmodel.conf.BuildTypeRef;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.getCache8PartsConfig;
import static org.apache.ignite.ci.teamcity.ignited.buildtype.FatBuildTypeDao.buildTypeStringIdToCacheKey;

public class BuildTypeRefDao {
    /** Cache name*/
    public static final String TEAMCITY_BUILD_TYPES_CACHE_NAME = "teamcityBuildTypeRef";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** BuildTypes cache. */
    private IgniteCache<Long, BuildTypeRefCompacted> buildTypesCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * Initialize.
     */
    public void init () {
        Ignite ignite = igniteProvider.get();
        buildTypesCache = ignite.getOrCreateCache(getCache8PartsConfig(TEAMCITY_BUILD_TYPES_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeRef BuildType reference.
     * @param existingBuildTypeRef Existing version of buildType reference in the DB.
     * @return BuildTypes references saved (if modifications detected), otherwise null.
     */
    public BuildTypeRefCompacted saveBuildTypeRef(long srvIdMaskHigh,
        @NotNull BuildTypeRef buildTypeRef,
        @Nullable BuildTypeRefCompacted existingBuildTypeRef) {
        Preconditions.checkNotNull(buildTypesCache, "init() was not called");
        Preconditions.checkNotNull(buildTypeRef, "buildType can't be null");

        BuildTypeRefCompacted newBuildType = new BuildTypeRefCompacted(compactor, buildTypeRef);

        if (existingBuildTypeRef == null || !existingBuildTypeRef.equals(newBuildType)) {
            buildTypesCache.put(buildTypeIdToCacheKey(srvIdMaskHigh, buildTypeRef.getId()), newBuildType);

            return newBuildType;
        }

        return null;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param ghData Data for saving.
     * @return List of added entries' keys.
     */
    public Set<Long> saveChunk(long srvIdMaskHigh, List<BuildTypeRef> ghData) {
        Set<Long> ids = ghData.stream().map(BuildTypeRef::getId)
            .filter(Objects::nonNull)
            .map(buildId -> buildTypeIdToCacheKey(srvIdMaskHigh, buildId))
            .collect(Collectors.toSet());

        Map<Long, BuildTypeRefCompacted> existingEntries = buildTypesCache.getAll(ids);
        Map<Long, BuildTypeRefCompacted> entriesToPut = new TreeMap<>();

        List<BuildTypeRefCompacted> collect = ghData.stream()
            .map(ref -> new BuildTypeRefCompacted(compactor, ref))
            .collect(Collectors.toList());

        for (BuildTypeRefCompacted next : collect) {
            long cacheKey = buildTypeStringIdToCacheKey(srvIdMaskHigh, next.id());
            BuildTypeRefCompacted buildTypePersisted = existingEntries.get(cacheKey);

            if (buildTypePersisted == null || !buildTypePersisted.equals(next))
                entriesToPut.put(cacheKey, next);
        }

        int size = entriesToPut.size();
        if (size != 0)
            buildTypesCache.putAll(entriesToPut);

        return entriesToPut.keySet();
    }

    public BuildTypeRefCompacted getBuildTypeRef(long srvIdMaskHigh, @NotNull String buildTypeId) {
        Preconditions.checkNotNull(buildTypesCache, "init() was not called");

        return buildTypesCache.get(buildTypeIdToCacheKey(srvIdMaskHigh, buildTypeId));
    }

    public List<BuildTypeRefCompacted> buildTypesCompacted(int srvIdMaskHigh, @Nullable String projectId) {
        return buildTypesCompactedStream(srvIdMaskHigh, projectId).collect(Collectors.toList());
    }

    public List<String> buildTypeIds(int srvIdMaskHigh, @Nullable String projectId) {
        return buildTypesCompactedStream(srvIdMaskHigh, projectId)
            .map(bt -> bt.id(compactor)).collect(Collectors.toList());

    }

    protected Stream<BuildTypeRefCompacted> buildTypesCompactedStream(int srvIdMaskHigh, @Nullable String projectId) {
        Stream<BuildTypeRefCompacted> stream = compactedBuildTypeRefsStreamForServer(srvIdMaskHigh);

        if (Strings.isNullOrEmpty(projectId))
            return stream;

        final int stringIdForProjectId = compactor.getStringId(projectId);

        return stream.filter(bt -> bt.projectId() == stringIdForProjectId);
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @return all buildTypes for a server, full scan.
     */
    @NotNull protected Stream<BuildTypeRefCompacted> compactedBuildTypeRefsStreamForServer(int srvIdMaskHigh) {
        return StreamSupport.stream(buildTypesCache.spliterator(), false)
            .filter(entry -> isKeyForServer(entry.getKey(), srvIdMaskHigh))
            .map(javax.cache.Cache.Entry::getValue);
    }

    /**
     * @param key Key.
     * @param srvIdMaskHigh Server id mask high.
     */
    private boolean isKeyForServer(Long key, int srvIdMaskHigh) {
        return key!=null && key >> 32 == srvIdMaskHigh;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeId BuildType id.
     * @return BuildType stringId.
     */
    private long buildTypeIdToCacheKey(long srvIdMaskHigh, String buildTypeId) {
        int buildTypeStringId = compactor.getStringId(buildTypeId);

        return buildTypeStringIdToCacheKey(srvIdMaskHigh, buildTypeStringId);
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeStringId BuildType stringId.
     */
    public boolean containsKey(int srvIdMaskHigh, int buildTypeStringId) {
        return buildTypesCache.containsKey(buildTypeStringIdToCacheKey(srvIdMaskHigh, buildTypeStringId));
    }
}
