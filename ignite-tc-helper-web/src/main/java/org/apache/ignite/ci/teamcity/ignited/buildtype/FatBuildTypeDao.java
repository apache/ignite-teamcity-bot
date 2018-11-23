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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildType;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import javax.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.getCache8PartsConfig;

public class FatBuildTypeDao {
    /** Cache name*/
    public static final String TEAMCITY_FAT_BUILD_TYPES_CACHE_NAME = "teamcityFatBuildType";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** BuildTypes cache. */
    private IgniteCache<Long, FatBuildTypeCompacted> buildTypesCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * Initialize.
     */
    public void init () {
        Ignite ignite = igniteProvider.get();
        buildTypesCache = ignite.getOrCreateCache(getCache8PartsConfig(TEAMCITY_FAT_BUILD_TYPES_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildType BuildType.
     * @param existingBuildType Existing version of buildType in the DB.
     * @return Fat BuildType saved (if modifications detected), otherwise null.
     */
    public FatBuildTypeCompacted saveBuildType(int srvIdMaskHigh,
        @NotNull BuildType buildType,
        @Nullable FatBuildTypeCompacted existingBuildType) {
        Preconditions.checkNotNull(buildTypesCache, "init() was not called");
        Preconditions.checkNotNull(buildType, "buildType can't be null");

        FatBuildTypeCompacted newBuildType = new FatBuildTypeCompacted(compactor, buildType);

        if (existingBuildType != null)
            existingBuildType.buildNumberCounter(newBuildType.buildNumberCounter());


        if (existingBuildType == null || !existingBuildType.equals(newBuildType)) {
            buildTypesCache.put(buildTypeIdToCacheKey(srvIdMaskHigh, buildType.getId()), newBuildType);

            return newBuildType;
        }

        return null;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeId BuildType id.
     * @return Saved fat buildType.
     */
    public FatBuildTypeCompacted getFatBuildType(int srvIdMaskHigh, @NotNull String buildTypeId) {
        Preconditions.checkNotNull(buildTypesCache, "init() was not called");

        return buildTypesCache.get(buildTypeIdToCacheKey(srvIdMaskHigh, buildTypeId));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return List of saved fat buildTypes.
     */
    public List<FatBuildTypeCompacted> buildTypesCompacted(int srvIdMaskHigh, @Nullable String projectId) {
        return buildTypesCompactedStream(srvIdMaskHigh, projectId).collect(Collectors.toList());
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return Stream of saved to current fat Teamcity's buildTypes.
     */
    protected Stream<FatBuildTypeCompacted> buildTypesCompactedStream(int srvIdMaskHigh, @Nullable String projectId) {
        Stream<FatBuildTypeCompacted> stream = compactedFatBuildTypesStreamForServer(srvIdMaskHigh);

        if (Strings.isNullOrEmpty(projectId))
            return stream;

        final int stringIdForProjectId = compactor.getStringId(projectId);

        return stream
            .filter(bt -> bt.projectId() == stringIdForProjectId)
            .filter(bt -> !bt.removed());
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return List of saved composite fat buildTypes.
     */
    public List<FatBuildTypeCompacted> compositeBuildTypesCompacted(int srvIdMaskHigh, @Nullable String projectId) {
        final int nameId = compactor.getStringId("buildConfigurationType");
        final int valueId = compactor.getStringId("COMPOSITE");

        return buildTypesCompactedStream(srvIdMaskHigh, projectId)
            .filter(bt -> bt.settings().findPropertyStringId(nameId) == valueId)
            .collect(Collectors.toList());
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectId Project id.
     * @return List of saved composite fat buildTypes.
     */
    private List<FatBuildTypeCompacted> compositeBuildTypesCompactedSortedByBuildNumberCounter(int srvIdMaskHigh, @Nullable String projectId) {
        List<FatBuildTypeCompacted> res = compositeBuildTypesCompacted(srvIdMaskHigh, projectId);

        Comparator<FatBuildTypeCompacted> comparator = Comparator
            .comparingInt(FatBuildTypeCompacted::buildNumberCounter);

        res.sort(comparator);

        Collections.reverse(res);

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
    @NotNull protected Stream<FatBuildTypeCompacted> compactedFatBuildTypesStreamForServer(int srvIdMaskHigh) {
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

    private long buildTypeIdToCacheKey(int srvIdMaskHigh, String buildTypeId) {
        int buildTypeStringId = compactor.getStringId(buildTypeId);

        return buildTypeStringIdToCacheKey(srvIdMaskHigh, buildTypeStringId);
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeStringId BuildType stringId.
     */
    public static long buildTypeStringIdToCacheKey(int srvIdMaskHigh, int buildTypeStringId) {
        return (long)buildTypeStringId | srvIdMaskHigh << 32;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildTypeStringId BuildType stringId.
     */
    public boolean containsKey(int srvIdMaskHigh, int buildTypeStringId) {
        return buildTypesCache.containsKey(buildTypeStringIdToCacheKey(srvIdMaskHigh, buildTypeStringId));
    }
}
