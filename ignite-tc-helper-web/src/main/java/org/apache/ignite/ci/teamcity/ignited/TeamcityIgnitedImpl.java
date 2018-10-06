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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.configuration.CacheConfiguration;
import org.jetbrains.annotations.NotNull;

//todo currently this implementation is shared between all users
public class TeamcityIgnitedImpl implements ITeamcityIgnited {
    /** Cache name*/
    public static final String TEAMCITY_BUILD_CACHE_NAME = "teamcityBuild";

    //todo move to string compacter
    /** Cache name */
    public static final String STRING_CACHE_NAME = "strings";

    /** Server id. */
    private String srvId;

    /** Pure HTTP Connection API. */
    private ITeamcity conn;

    /** Ignite provider. */
    @Inject Provider<Ignite> igniteProvider;

    /** Scheduler. */
    @Inject IScheduler scheduler;

    /** Server ID mask for cache Entries. */
    private long srvIdMaskHigh;

    /** PPs cache. */
    private IgniteCache<Long, BuildRefCompacted> buildsCache;

    public void init(String srvId, ITeamcity conn) {
        this.srvId = srvId;
        this.conn = conn;

        srvIdMaskHigh = Math.abs(srvId.hashCode());

        Ignite ignite = igniteProvider.get();
        buildsCache = ignite.getOrCreateCache(TcHelperDb.getCacheV2Config(TEAMCITY_BUILD_CACHE_NAME));
    }

    @NotNull
    public static <K, V> CacheConfiguration<K, V> getCache8PartsConfig(String name) {
        CacheConfiguration<K, V> ccfg = new CacheConfiguration<>(name);

        ccfg.setAffinity(new RendezvousAffinityFunction(false, 8));

        return ccfg;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<BuildRef> getBuildHistory(
        @Nullable String buildTypeId,
        @Nullable String branchName) {
        scheduler.sheduleNamed(ITeamcityIgnited.class.getSimpleName() + ".actualizeRecentBuilds",
            this::actualizeRecentBuilds, 2, TimeUnit.MINUTES);

        return allBuildsEver()
            .filter(e -> Objects.equals(e.buildTypeId, buildTypeId))
            .filter(e -> Objects.equals(e.branchName, branchName))
            .collect(Collectors.toList());
    }

    @NotNull private Stream<BuildRef> allBuildsEver() {
        return StreamSupport.stream(buildsCache.spliterator(), false)
            .filter(entry -> entry.getKey() >> 32 == srvIdMaskHigh)
            .map(javax.cache.Cache.Entry::getValue)
            .map(BuildRefCompacted::toBuildRef);
    }

    private void actualizeRecentBuilds() {
        runAtualizeBuilds(srvId, false);

        // schedule full resync later
        scheduler.invokeLater(this::sheduleResync, 20, TimeUnit.SECONDS);
    }

    /**
     *
     */
    private void sheduleResync() {
        scheduler.sheduleNamed(ITeamcityIgnited.class.getSimpleName() + ".fullReindex",
            this::fullReindex, 60, TimeUnit.MINUTES);
    }

    /**
     *
     */
    private void fullReindex() {
        runAtualizeBuilds(srvId, true);
    }

    /**
     * @param srvId Server id.
     * @param fullReindex Reindex all open PRs
     */
    @MonitoredTask(name = "Actualize BuildRefs, full resync", nameExtArgIndex = 1)
    @AutoProfiling
    protected String runAtualizeBuilds(String srvId, boolean fullReindex) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();

        List<BuildRef> tcData = conn.getFinishedBuilds(null, null);//todo, outLinkNext);
        int cntSaved = saveChunk(tcData);
        int totalChecked = tcData.size();
        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            tcData = conn.getFinishedBuilds(null,null); //todo nextPageUrl, outLinkNext);
            cntSaved += saveChunk(tcData);
            totalChecked += tcData.size();

            if(!fullReindex)
                break; // 2 pages
        }

        return "Entries saved " + cntSaved + " Builds checked " + totalChecked;
    }

    private int saveChunk(List<BuildRef> ghData) {
        Set<Long> ids = ghData.stream().map(BuildRef::getId)
            .filter(Objects::nonNull)
            .map(this::buildIdToCacheKey)
            .collect(Collectors.toSet());

        Map<Long, BuildRefCompacted> existingEntries = buildsCache.getAll(ids);
        Map<Long, BuildRefCompacted> entriesToPut = new TreeMap<>();

        List<BuildRefCompacted> collect = ghData.stream().map(BuildRefCompacted::new)
            .collect(Collectors.toList());

        for (BuildRefCompacted next : collect) {
            long cacheKey = buildIdToCacheKey(next.buildId );
            BuildRefCompacted buildPersisted = existingEntries.get(cacheKey);

            if (buildPersisted == null || !buildPersisted.equals(next))
                entriesToPut.put(cacheKey, next);
        }

        int size = entriesToPut.size();
        if (size != 0)
            buildsCache.putAll(entriesToPut);
        return size;
    }

    private long buildIdToCacheKey(int buildId) {
        return (long)buildId | srvIdMaskHigh << 32;
    }
}
