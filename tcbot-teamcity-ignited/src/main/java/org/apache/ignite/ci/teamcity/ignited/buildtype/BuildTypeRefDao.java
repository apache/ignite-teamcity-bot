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
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.tcservice.model.conf.BuildType;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

import static org.apache.ignite.tcbot.persistence.CacheConfigs.getCache8PartsConfig;
import static org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeDao.buildTypeStringIdToCacheKey;

public class BuildTypeRefDao {
    /** Cache name*/
    public static final String TEAMCITY_BUILD_TYPES_CACHE_NAME = "teamcityBuildTypeRef";

    /** Ignite. */
    @Inject private Ignite ignite;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * References to BuildTypes cache.
     */
    private IgniteCache<Long, BuildTypeRefCompacted> buildTypesCache() {
        return ignite.getOrCreateCache(getCache8PartsConfig(TEAMCITY_BUILD_TYPES_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeRef BuildType reference.
     * @param existingBuildTypeRef Existing version of buildType reference in the DB.
     * @return BuildTypes references saved (if modifications detected), otherwise null.
     */
    public BuildTypeRefCompacted saveBuildTypeRef(int srvIdMaskHigh,
        @Nonnull BuildType buildTypeRef,
        @Nullable BuildTypeRefCompacted existingBuildTypeRef) {
        Preconditions.checkNotNull(buildTypeRef, "buildType can't be null");

        BuildTypeRefCompacted newBuildType = new BuildTypeRefCompacted(compactor, buildTypeRef);

        if (existingBuildTypeRef == null || !existingBuildTypeRef.equals(newBuildType)) {
            buildTypesCache().put(buildTypeIdToCacheKey(srvIdMaskHigh, buildTypeRef.getId()), newBuildType);

            return newBuildType;
        }

        return null;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param ghData Data for saving.
     * @return List of added entries' keys.
     */
    public Set<Long> saveChunk(int srvIdMaskHigh, List<BuildType> ghData) {
        Set<Long> ids = ghData.stream().map(BuildType::getId)
            .filter(Objects::nonNull)
            .map(id -> buildTypeIdToCacheKey(srvIdMaskHigh, id))
            .collect(Collectors.toSet());

        Map<Long, BuildTypeRefCompacted> existingEntries = buildTypesCache().getAll(ids);
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
            buildTypesCache().putAll(entriesToPut);

        return entriesToPut.keySet();
    }

    /**
     * Method compares the received list with the list on cache and marks missing ids as removed.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param currListOfBuildTypeIdsOnTeamcity Current list of suite ids on Teamcity.
     * @param projectId Project id.
     * @return List of marked as removed buildType ids.
     */
    public Set<String> markMissingBuildsAsRemoved(int srvIdMaskHigh,
        List<String> currListOfBuildTypeIdsOnTeamcity, String projectId) {

        Map<Long, String> ids = currListOfBuildTypeIdsOnTeamcity.stream()
                .collect(Collectors.toMap(id -> buildTypeIdToCacheKey(srvIdMaskHigh, id), id -> id));

        int projectStrId = compactor.getStringId(projectId);

        Set<String> rmvBuildTypes = new TreeSet<>();

        Map<Long, BuildTypeRefCompacted> rmvEntries = StreamSupport.stream(buildTypesCache().spliterator(), false)
            .filter(entry -> isKeyForServer(entry.getKey(), srvIdMaskHigh))
            .filter(entry -> entry.getValue().projectId() == projectStrId)
            .filter(entry -> !ids.containsKey(entry.getKey()))
            .collect(Collectors.toMap(Cache.Entry::getKey, entry -> {
                BuildTypeRefCompacted buildTypeRef = entry.getValue();
                buildTypeRef.markRemoved();

                rmvBuildTypes.add(entry.getValue().id(compactor));

                return buildTypeRef;
            }));

        buildTypesCache().putAll(rmvEntries);

        return rmvBuildTypes;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeId BuildType id.
     * @return Saved reference to buildType.
     */
    public BuildTypeRefCompacted getBuildTypeRef(int srvIdMaskHigh, @Nonnull String buildTypeId) {
        return buildTypesCache().get(buildTypeIdToCacheKey(srvIdMaskHigh, buildTypeId));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return List of saved current Teamcity's buildTypes.
     */
    public List<BuildTypeRefCompacted> buildTypesCompacted(int srvIdMaskHigh, @Nullable String projectId) {
        return buildTypesCompactedStream(srvIdMaskHigh, projectId).collect(Collectors.toList());
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return List of saved current Teamcity's buildTypes ids.
     */
    public List<String> buildTypeIds(int srvIdMaskHigh, @Nullable String projectId) {
        return buildTypesCompactedStream(srvIdMaskHigh, projectId)
            .map(bt -> bt.id(compactor)).collect(Collectors.toList());
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return Map of saved buildTypes ids with 'removed' flag.
     */
    public Map<String, Boolean> allBuildTypeIds(int srvIdMaskHigh, @Nullable String projectId) {
        return allBuildTypesCompactedStream(srvIdMaskHigh, projectId)
            .collect(Collectors.toMap(bt -> bt.id(compactor), BuildTypeRefCompacted::removed));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return Stream of saved references to current Teamcity's buildTypes.
     */
    protected Stream<BuildTypeRefCompacted> buildTypesCompactedStream(int srvIdMaskHigh, @Nullable String projectId) {
        return allBuildTypesCompactedStream(srvIdMaskHigh, projectId).filter(bt -> !bt.removed());
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return Stream of saved references to all buildTypes (including deleted).
     */
    protected Stream<BuildTypeRefCompacted> allBuildTypesCompactedStream(int srvIdMaskHigh, @Nullable String projectId) {
        Stream<BuildTypeRefCompacted> stream = compactedBuildTypeRefsStreamForServer(srvIdMaskHigh);

        if (Strings.isNullOrEmpty(projectId))
            return stream;

        final int strIdForProjectId = compactor.getStringId(projectId);

        return stream
            .filter(bt -> bt.projectId() == strIdForProjectId);
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @return all buildTypes for a server, full scan.
     */
    @Nonnull protected Stream<BuildTypeRefCompacted> compactedBuildTypeRefsStreamForServer(int srvIdMaskHigh) {
        return StreamSupport.stream(buildTypesCache().spliterator(), false)
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
    private long buildTypeIdToCacheKey(int srvIdMaskHigh, String buildTypeId) {
        int buildTypeStrId = compactor.getStringId(buildTypeId);

        return buildTypeStringIdToCacheKey(srvIdMaskHigh, buildTypeStrId);
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeStrId BuildType stringId.
     */
    public boolean containsKey(int srvIdMaskHigh, int buildTypeStrId) {
        return buildTypesCache().containsKey(buildTypeStringIdToCacheKey(srvIdMaskHigh, buildTypeStrId));
    }
}
