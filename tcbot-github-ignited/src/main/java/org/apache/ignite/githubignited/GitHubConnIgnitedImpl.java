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
package org.apache.ignite.githubignited;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.githubservice.IGitHubConnection;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.ci.github.GitHubBranchKey;
import org.apache.ignite.ci.github.GitHubBranchShort;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
class GitHubConnIgnitedImpl implements IGitHubConnIgnited {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(GitHubConnIgnitedImpl.class);


    /** Server id. */
    private String srvCode;

    /** Pure HTTP Connection API. */
    private IGitHubConnection conn;

    /** Ignite provider. */
    @Inject Provider<Ignite> igniteProvider;

    /** Scheduler. */
    @Inject IScheduler scheduler;

    /** Server ID mask for cache Entries. */
    private int srvIdMaskHigh;

    /** PPs cache. */
    private IgniteCache<Long, PullRequest> prCache;


    /** PPs cache. */
    private IgniteCache<GitHubBranchKey, GitHubBranchShort> branchCache;

    /**
     * @param conn Connection.
     */
    public void init(IGitHubConnection conn) {
        this.conn = conn;
        srvCode = conn.config().code();

        logger.info("Init of GitHub server: srvCode=" + srvCode+ ", API Url=" +
            config().gitApiUrl() + " " +   config().code());

        srvIdMaskHigh = Math.abs(srvCode.hashCode());

        Ignite ignite = igniteProvider.get();
        prCache = ignite.getOrCreateCache(CacheConfigs.getCache8PartsConfig(GIT_HUB_PR));
        branchCache = ignite.getOrCreateCache(CacheConfigs.getCache8PartsConfig(GIT_HUB_BRANCHES));
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
    @Override public IGitHubConfig config() {
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

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<String> getBranches() {
        final int rescanIntervalMins = config().isPreferBranches() ? 5 : 120;

        scheduler.sheduleNamed(taskName("actualizeBranches"),
                this::actualizeBranches,
                rescanIntervalMins, TimeUnit.MINUTES);

        return StreamSupport.stream(branchCache.spliterator(), false)
            .filter(entry -> entry.getKey().srvId() == srvIdMaskHigh)
            .map(javax.cache.Cache.Entry::getKey)
            .map(GitHubBranchKey::branchName)
            .collect(Collectors.toList());
    }

    private void actualizeBranches() {
        runActualizeBranches(srvCode, true);
    }

    /**
     * @param taskName Task name.
     * @return Task name concatenated with server name.
     */
    @Nonnull
    private String taskName(String taskName) {
        return IGitHubConnIgnited.class.getSimpleName() + "." + taskName + "." + srvCode;
    }

    private void actualizePrs() {
        runActualizePrs(srvCode, false);

        // schedule full resync later
        scheduler.invokeLater(
            () -> scheduler.sheduleNamed(taskName("fullReindex"), this::fullReindex, 2, TimeUnit.HOURS),
            5, TimeUnit.MINUTES);
    }

    /**
     *
     */
    private void fullReindex() {
        runActualizePrs(srvCode, true);
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

        List<PullRequest> ghData = conn.getPullRequestsPage(null, outLinkNext);

        Set<Integer> actualPrs = new HashSet<>();

        int cntSaved = savePrsChunk(ghData);
        int totalChecked = ghData.size();
        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            ghData = conn.getPullRequestsPage(nextPageUrl, outLinkNext);
            int savedThisChunk = savePrsChunk(ghData);
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

    /**
     * @param ghData GitHub data to save.
     */
    private int savePrsChunk(List<PullRequest> ghData) {
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

    private long prNumberToCacheKey(int prNum) {
        return (long)prNum | (long)srvIdMaskHigh << 32;
    }

    /**
     * @param srvId Server id.
     * @param fullReindex Reindex all open PRs
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Actualize GitHub Branches(srv, full resync)", nameExtArgsIndexes = {0, 1})
    @AutoProfiling
    protected String runActualizeBranches(String srvId, boolean fullReindex) {
        AtomicReference<String> outLinkNext = new AtomicReference<>();

        List<GitHubBranchShort> ghData = conn.getBranchesPage(null, outLinkNext);

        Set<Integer> actualPrs = new HashSet<>();

        int cntSaved = saveBranchesChunk(ghData);
        int totalChecked = ghData.size();
        while (outLinkNext.get() != null) {
            String nextPageUrl = outLinkNext.get();
            ghData = conn.getBranchesPage(nextPageUrl, outLinkNext);
            int savedThisChunk = saveBranchesChunk(ghData);
            cntSaved += savedThisChunk;
            totalChecked += ghData.size();

            if (!fullReindex && savedThisChunk == 0)
                break;
        }

        if (fullReindex)
            refreshOutdatedPrs(srvId, actualPrs);

        return "Entries saved " + cntSaved + " Branches checked " + totalChecked;
    }


    /**
     * @param ghData GitHub data to save.
     */
    private int saveBranchesChunk(List<GitHubBranchShort> ghData) {
        Set<GitHubBranchKey> ids = ghData.stream()
            .map(this::branchToKey)
            .collect(Collectors.toSet());

        Map<GitHubBranchKey, GitHubBranchShort> existingEntries = branchCache.getAll(ids);
        Map<GitHubBranchKey, GitHubBranchShort> entriesToPut = new TreeMap<>();

        for (GitHubBranchShort next : ghData) {
            GitHubBranchKey cacheKey = branchToKey(next);
            GitHubBranchShort prPersisted = existingEntries.get(cacheKey);

            if (prPersisted == null || !prPersisted.equals(next))
                entriesToPut.put(cacheKey, next);
        }

        int size = entriesToPut.size();

        if (size != 0)
            branchCache.putAll(entriesToPut);

        return size;
    }

    private GitHubBranchKey branchToKey(GitHubBranchShort b) {
        GitHubBranchKey key = new GitHubBranchKey();
        key.branchName(b.name()).srvId(srvIdMaskHigh);
        return key;
    }

}
