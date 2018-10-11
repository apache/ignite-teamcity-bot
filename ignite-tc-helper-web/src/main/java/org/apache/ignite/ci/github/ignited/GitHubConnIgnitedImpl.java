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
package org.apache.ignite.ci.github.ignited;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.configuration.CacheConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
class GitHubConnIgnitedImpl implements IGitHubConnIgnited {
    /** Cache name*/
    public static final String GIT_HUB_PR = "gitHubPr";

    /** Server id. */
    private String srvId;

    /** Pure HTTP Connection API. */
    private IGitHubConnection conn;

    /** Ignite provider. */
    @Inject Provider<Ignite> igniteProvider;
    /** Scheduler. */
    @Inject IScheduler scheduler;

    /** Server ID mask for cache Entries. */
    private long srvIdMaskHigh;

    /** PPs cache. */
    private IgniteCache<Long, PullRequest> prCache;

    public void init(String srvId, IGitHubConnection conn) {
        this.srvId = srvId;
        this.conn = conn;

        srvIdMaskHigh = Math.abs(srvId.hashCode());

        prCache = igniteProvider.get().getOrCreateCache(getCache8PartsConfig(GIT_HUB_PR));
    }

    @NotNull
    public static <K, V> CacheConfiguration<K, V> getCache8PartsConfig(String name) {
        CacheConfiguration<K, V> ccfg = new CacheConfiguration<>(name);

        ccfg.setAffinity(new RendezvousAffinityFunction(false, 8));

        return ccfg;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<PullRequest> getPullRequests() {
        scheduler.sheduleNamed(IGitHubConnIgnited.class.getSimpleName() + ".actualizePrs",
            this::actualizePrs, 2, TimeUnit.MINUTES);

        return StreamSupport.stream(prCache.spliterator(), false)
            .filter(entry -> entry.getKey() >> 32 == srvIdMaskHigh)
            .filter(entry -> PullRequest.OPEN.equals(entry.getValue().getState()))
            .map(javax.cache.Cache.Entry::getValue)
            .collect(Collectors.toList());
    }

    private void actualizePrs() {
        runAtualizePrs(srvId, false);

        // schedule full resync later
        scheduler.invokeLater(this::sheduleResync, 20, TimeUnit.SECONDS);
    }

    /**
     *
     */
    private void sheduleResync() {
        scheduler.sheduleNamed(IGitHubConnIgnited.class.getSimpleName() + ".fullReindex",
            this::fullReindex, 60, TimeUnit.MINUTES);
    }

    /**
     *
     */
    private void fullReindex() {
        runAtualizePrs(srvId, true);
    }

    /**
     * @param srvId Server id.
     * @param fullReindex Reindex all open PRs
     */
    @MonitoredTask(name = "Actualize PRs, full resync", nameExtArgIndex = 1)
    @AutoProfiling
    protected String runAtualizePrs(String srvId, boolean fullReindex) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();

        List<PullRequest> ghData = conn.getPullRequests(null, outLinkNext);
        int cntSaved = saveChunk(ghData);
        int totalChecked = ghData.size();
        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            ghData = conn.getPullRequests(nextPageUrl, outLinkNext);
            int savedThisChunk = saveChunk(ghData);
            cntSaved += savedThisChunk;
            totalChecked += ghData.size();

            if (!fullReindex && savedThisChunk == 0)
                break;
        }

        return "Entries saved " + cntSaved + " PRs checked " + totalChecked;
    }

    private int saveChunk(List<PullRequest> ghData) {
        Set<Long> ids = ghData.stream().map(PullRequest::getNumber)
            .map(this::prNumberToCacheKey)
            .collect(Collectors.toSet());

        Map<Long, PullRequest> existingEntries = prCache.getAll(ids);
        Map<Long, PullRequest> entriesToPut = new TreeMap<>();

        for (PullRequest next : ghData) {
            long cacheKey = prNumberToCacheKey(next.getNumber());
            PullRequest prPersisted = existingEntries.get(cacheKey);

            if (prPersisted == null || !prPersisted.equals(next))
                entriesToPut.put(cacheKey, next);
        }

        int size = entriesToPut.size();
        if (size != 0)
            prCache.putAll(entriesToPut);
        return size;
    }

    private long prNumberToCacheKey(int prNumber) {
        return (long)prNumber | srvIdMaskHigh << 32;
    }
}
