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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcservice.model.conf.bt.BuildTypeFull;
import org.apache.ignite.tcbot.persistence.IStringCompactor;

import static org.apache.ignite.tcbot.persistence.CacheConfigs.getCache8PartsConfig;

public class BuildTypeDao {
    /** Cache name*/
    public static final String TEAMCITY_FAT_BUILD_TYPES_CACHE_NAME = "teamcityFatBuildType";

    /** Ignite. */
    @Inject private Ignite ignite;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * BuildTypes cache.
     */
    private IgniteCache<Long, BuildTypeCompacted> buildTypesCache() {
        return ignite.getOrCreateCache(getCache8PartsConfig(TEAMCITY_FAT_BUILD_TYPES_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildType BuildType.
     * @param existingBuildType Existing version of buildType in the DB.
     * @return Fat BuildType saved (if modifications detected), otherwise null.
     */
    public BuildTypeCompacted saveBuildType(int srvIdMaskHigh,
        @Nonnull BuildTypeFull buildType,
        @Nullable BuildTypeCompacted existingBuildType) {
        Preconditions.checkNotNull(buildType, "buildType can't be null");

        BuildTypeCompacted newBuildType = new BuildTypeCompacted(compactor, buildType);

        if (existingBuildType != null)
            existingBuildType.buildNumberCounter(newBuildType.buildNumberCounter());


        if (existingBuildType == null || !existingBuildType.equals(newBuildType)) {
            buildTypesCache().put(buildTypeIdToCacheKey(srvIdMaskHigh, buildType.getId()), newBuildType);

            return newBuildType;
        }

        return null;
    }

    /**
     * @param srvIdMaskHigh  Server id mask high.
     * @param refCompacted Reference compacted.
     */
    @AutoProfiling
    public boolean save(int srvIdMaskHigh, BuildTypeCompacted refCompacted) {
        long cacheKey = buildTypeStringIdToCacheKey(srvIdMaskHigh, refCompacted.id());

        BuildTypeCompacted buildTypePersisted = buildTypesCache().get(cacheKey);

        if (buildTypePersisted == null || !buildTypePersisted.equals(refCompacted)) {
            buildTypesCache().put(cacheKey, refCompacted);

            return true;
        }

        return false;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeId BuildType id.
     * @return Saved fat buildType.
     */
    public BuildTypeCompacted getFatBuildType(int srvIdMaskHigh, @Nonnull String buildTypeId) {
        Preconditions.checkNotNull(buildTypesCache(), "init() was not called");

        return buildTypesCache().get(buildTypeIdToCacheKey(srvIdMaskHigh, buildTypeId));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return List of saved fat buildTypes.
     */
    public List<BuildTypeCompacted> buildTypesCompacted(int srvIdMaskHigh, @Nullable String projectId) {
        return buildTypesCompactedStream(srvIdMaskHigh, projectId).collect(Collectors.toList());
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return Stream of saved to current fat Teamcity's buildTypes.
     */
    protected Stream<BuildTypeCompacted> buildTypesCompactedStream(int srvIdMaskHigh, @Nullable String projectId) {
        Stream<BuildTypeCompacted> stream = compactedFatBuildTypesStreamForServer(srvIdMaskHigh);

        if (Strings.isNullOrEmpty(projectId))
            return stream;

        final int strIdForProjectId = compactor.getStringId(projectId);

        return stream
            .filter(bt -> bt.projectId() == strIdForProjectId)
            .filter(bt -> !bt.removed());
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return List of saved composite fat buildTypes.
     */
    public List<BuildTypeCompacted> compositeBuildTypesCompacted(int srvIdMaskHigh, @Nullable String projectId) {
        final int nameId = compactor.getStringId("buildConfigurationType");
        final int valId = compactor.getStringId("COMPOSITE");

        return buildTypesCompactedStream(srvIdMaskHigh, projectId)
            .filter(bt -> bt.settings().findPropertyStringId(nameId) == valId)
            .collect(Collectors.toList());
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return List of saved composite fat buildTypes.
     */
    private List<BuildTypeCompacted> compositeBuildTypesCompactedSortedByBuildNumberCounter(int srvIdMaskHigh, @Nullable String projectId) {
        List<BuildTypeCompacted> res = compositeBuildTypesCompacted(srvIdMaskHigh, projectId);

        Comparator<BuildTypeCompacted> comp = Comparator.comparingInt(BuildTypeCompacted::buildNumberCounter);

        res.sort(comp.reversed());

        return res;
    }

    /**
     * Return list of composite suite ids sorted by number of snapshot dependency.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return List of buildTypes ids.
     */
    public List<String> compositeBuildTypesIdsSortedByBuildNumberCounter(int srvIdMaskHigh, @Nullable String projectId) {
        return compositeBuildTypesCompactedSortedByBuildNumberCounter(srvIdMaskHigh, projectId).stream()
            .map(bt -> compactor.getStringFromId(bt.id()))
            .collect(Collectors.toList());
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @return All buildTypes for a server, full scan.
     */
    @Nonnull protected Stream<BuildTypeCompacted> compactedFatBuildTypesStreamForServer(int srvIdMaskHigh) {
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
     * @param buildTypeId Build type id.
     */
    private long buildTypeIdToCacheKey(int srvIdMaskHigh, String buildTypeId) {
        int buildTypeStrId = compactor.getStringId(buildTypeId);

        return buildTypeStringIdToCacheKey(srvIdMaskHigh, buildTypeStrId);
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeStrId BuildType stringId.
     */
    public static long buildTypeStringIdToCacheKey(int srvIdMaskHigh, int buildTypeStrId) {
        return (long)buildTypeStrId | (long)srvIdMaskHigh << 32;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeStrId BuildType stringId.
     */
    public boolean containsKey(int srvIdMaskHigh, int buildTypeStrId) {
        return buildTypesCache().containsKey(buildTypeStringIdToCacheKey(srvIdMaskHigh, buildTypeStrId));
    }
}
