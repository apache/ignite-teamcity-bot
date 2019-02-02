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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.github.pure.IGitHubConnection;
import org.apache.ignite.ci.tcbot.conf.IGitHubConfig;
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

        prCache = igniteProvider.get().getOrCreateCache(TcHelperDb.getCache8PartsConfig(GIT_HUB_PR));
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Nullable
    @Override public PullRequest getPullRequest(int prNum) {
        return prCache.get(prNumberToCacheKey(prNum));
    }

    /** {@inheritDoc} */
    @Override public void notifyGit(String url, String body) {
        conn.notifyGit(url, body);
    }

    /** {@inheritDoc} */
    @Override public String gitBranchPrefix() {
        return config().gitBranchPrefix();
    }
    /** {@inheritDoc} */
    @Override
    public IGitHubConfig config() {
        return conn.config();
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<PullRequest> getPullRequests() {
        scheduler.sheduleNamed(taskName("actualizePrs"), this::actualizePrs, 2, TimeUnit.MINUTES);

        return StreamSupport.stream(prCache.spliterator(), false)
            .filter(entry -> entry.getKey() >> 32 == srvIdMaskHigh)
            .filter(entry -> PullRequest.OPEN.equals(entry.getValue().getState()))
            .map(javax.cache.Cache.Entry::getValue)
            .collect(Collectors.toList());
    }

    /**
     * @param taskName Task name.
     * @return Task name concatenated with server name.
     */
    @NotNull
    private String taskName(String taskName) {
        return IGitHubConnIgnited.class.getSimpleName() + "." + taskName + "." + srvId;
    }


    private void actualizePrs() {
        runActualizePrs(srvId, false);

        // schedule full resync later
        scheduler.invokeLater(
            () -> scheduler.sheduleNamed(taskName("fullReindex"), this::fullReindex, 2, TimeUnit.HOURS),
            5, TimeUnit.MINUTES);
    }

    /**
     *
     */
    private void fullReindex() {
        runActualizePrs(srvId, true);
    }

    /**
     * @param srvId Server id.
     * @param fullReindex Reindex all open PRs
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Actualize PRs(srv, full resync)", nameExtArgsIndexes = {0, 1})
    @AutoProfiling
    protected String runActualizePrs(String srvId, boolean fullReindex) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();

        List<PullRequest> ghData = conn.getPullRequests(null, outLinkNext);

        Set<Integer> actualPrs = new HashSet<>();

        int cntSaved = saveChunk(ghData);
        int totalChecked = ghData.size();
        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            ghData = conn.getPullRequests(nextPageUrl, outLinkNext);
            int savedThisChunk = saveChunk(ghData);
            cntSaved += savedThisChunk;
            totalChecked += ghData.size();

            if (fullReindex) {
                actualPrs.addAll(ghData.stream()
                    .map(PullRequest::getNumber)
                    .collect(Collectors.toSet()));
            }

            if (!fullReindex && savedThisChunk == 0)
                break;
        }

        if (fullReindex)
            refreshOutdatedPrs(srvId, actualPrs);

        return "Entries saved " + cntSaved + " PRs checked " + totalChecked;
    }

    /** */
    @AutoProfiling
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Check Outdated PRs(srv)", nameExtArgsIndexes = {0})
    protected String refreshOutdatedPrs(String srvId, Set<Integer> actualPrs) {
        final long cnt = StreamSupport.stream(prCache.spliterator(), false)
                .filter(entry -> entry.getKey() >> 32 == srvIdMaskHigh)
                .filter(entry -> PullRequest.OPEN.equals(entry.getValue().getState()))
                .filter(entry -> !actualPrs.contains(entry.getValue().getNumber()))
                .peek(entry -> prCache.put(entry.getKey(), conn.getPullRequest(entry.getValue().getNumber())))
                .count();

        return "PRs updated for " + srvId + ": " + cnt + " from " + prCache.size();
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
