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

package org.apache.ignite.tcbot.engine.testfixes;

import com.google.common.base.Strings;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.github.GitHubBranch;
import org.apache.ignite.ci.github.GitHubUser;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.teamcity.ignited.buildtype.BuildTypeCompacted;
import org.apache.ignite.ci.teamcity.ignited.buildtype.SnapshotDependencyCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.githubignited.IGitHubConnIgnited;
import org.apache.ignite.githubignited.IGitHubConnIgnitedProvider;
import org.apache.ignite.jiraignited.IJiraIgnited;
import org.apache.ignite.jiraignited.IJiraIgnitedProvider;
import org.apache.ignite.jiraservice.JiraTicketStatusCode;
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedChain;
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
import org.apache.ignite.tcbot.engine.ui.ShortSuiteUi;
import org.apache.ignite.tcbot.engine.ui.ShortTestFailureUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcbot.persistence.scheduler.MaintenanceActionRegistry;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.history.ISuiteRunHistory;
import org.apache.ignite.tcservice.model.conf.BuildType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.tcignited.buildref.BranchEquivalence.normalizeBranch;

/**
 * Matches JIRA tickets and GitHub PRs mentioning tests or suites that are being fixed.
 */
public class TestFixesService {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TestFixesService.class);

    /** Reverse lookup cache name. */
    private static final String REFS_CACHE_NAME = "testFixRefsByTest";

    /** Source fix cache name. */
    private static final String SOURCE_CACHE_NAME = "testFixSourcesById";

    /** Background scheduled task name. */
    private static final String TASK_NAME = TestFixesService.class.getSimpleName() + ".refresh";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** Admin maintenance actions. */
    @Inject private MaintenanceActionRegistry maintenanceActions;

    /** Config. */
    @Inject private ITcBotConfig cfg;

    /** JIRA provider. */
    @Inject private IJiraIgnitedProvider jiraProvider;

    /** GitHub provider. */
    @Inject private IGitHubConnIgnitedProvider ghProvider;

    /** TeamCity provider. */
    @Inject private ITeamcityIgnitedProvider tcProvider;

    /** User-visible process monitor. */
    @Inject private BotProcessMonitor processMonitor;

    /** String compactor. */
    @Inject private IStringCompactor compactor;

    /** Source fix cache keyed by synthetic source id. */
    private volatile IgniteCache<String, TestFixSource> sourceCache;

    /** Reverse lookup cache keyed by suite/test spelling. */
    private volatile IgniteCache<String, TestFixRefs> lookupCache;

    /**
     * Starts rare background refresh.
     */
    public void start() {
        maintenanceActions.register(TASK_NAME, "Refresh JIRA/GitHub test-fix mapping", this::refresh);

        scheduler.invokeLater(this::refreshAndReschedule, 2, TimeUnit.MINUTES);
    }

    /**
     * Schedules earlier matching after data update signal.
     */
    public void requestMatchSoon() {
        scheduler.sheduleNamed(TASK_NAME + ".soon", this::safeRefresh, 3, TimeUnit.MINUTES);
    }

    /**
     * Schedules an immediate manual refresh.
     *
     * @param processId Optional user-visible process id.
     * @return {@code true} if refresh was accepted.
     */
    public boolean requestMatchNow(@Nullable Long processId) {
        processMonitor.start(processId, "testFixesRefresh", "Test fix mapping refresh queued.");

        boolean accepted = scheduler.runNamedNow(TASK_NAME + ".manual", () -> safeRefresh(processId), processId);

        if (!accepted)
            processMonitor.fail(processId, "Test fix mapping refresh is already queued or running.");

        return accepted;
    }

    /**
     * @param suite Suite UI.
     */
    public void decorate(ShortSuiteUi suite) {
        if (suite == null)
            return;

        decorate(Collections.singletonList(suite));
    }

    /**
     * @param suites Suites UI.
     */
    public void decorate(Collection<ShortSuiteUi> suites) {
        if (suites == null || suites.isEmpty())
            return;

        TestFixLookupIndex idx = lookupIndex(suites);

        for (ShortSuiteUi suite : suites)
            decorate(suite, idx);
    }

    /**
     * @param suite Suite UI.
     * @param idx Test fix lookup index.
     */
    private void decorate(ShortSuiteUi suite, TestFixLookupIndex idx) {
        if (suite == null)
            return;

        suite.fixRefs = idx.findSuite(suite.suiteId);

        for (ShortTestFailureUi failure : suite.testFailures())
            decorate(failure, suite.suiteId, idx);
    }

    /**
     * @param failure Test failure UI.
     */
    public void decorate(ShortTestFailureUi failure) {
        if (failure == null)
            return;

        Set<String> keys = new LinkedHashSet<>();

        addLookupKey(keys, TestFixLookupIndex.testLookupKey(failure.suiteId, failure.testNameId));

        decorate(failure, failure.suiteId, lookupIndex(keys));
    }

    /**
     * @param failure Test failure UI.
     * @param suiteId Suite id.
     * @param idx Test fix lookup index.
     */
    private void decorate(ShortTestFailureUi failure, @Nullable String suiteId, TestFixLookupIndex idx) {
        if (failure == null)
            return;

        failure.fixRefs = idx.findTest(suiteId, failure.testNameId);
    }

    /**
     * @param limit Max rows.
     */
    public List<TestFixRefUi> recent(int limit) {
        ensureCache();

        List<TestFixRefs> mappings = StreamSupport.stream(lookupCache.spliterator(), false)
            .map(Cache.Entry::getValue)
            .collect(Collectors.toList());

        Map<String, TestFixSource> sources = sources(sourceIds(mappings));

        java.util.stream.Stream<TestFixRefUi> stream = mappings.stream()
            .flatMap(mapping -> refsFor(mapping, sources).stream())
            .sorted(Comparator.comparingLong((TestFixRefUi ref) -> sortTs(ref)).reversed());

        if (limit > 0)
            stream = stream.limit(limit);

        return uniqueRefs(stream.collect(Collectors.toList()));
    }

    /**
     * Refreshes match cache.
     */
    @AutoProfiling
    public String refresh() {
        return refresh(null);
    }

    /**
     * Refreshes match cache.
     *
     * @param processId Optional user-visible process id.
     */
    @AutoProfiling
    public String refresh(@Nullable Long processId) {
        ensureCache();

        RefreshStats stats = new RefreshStats();

        publishRefreshStatus(processId, "collecting test names from tracked suite history", stats);
        Map<String, List<TestFixCandidate>> candidatesByServer = candidatesByServer();
        stats.collectCandidateStats(candidatesByServer);
        publishRefreshStatus(processId, "collected test names from tracked suite history", stats);

        for (String srvCode : serverCodes()) {
            List<TestFixCandidate> srvCandidates = candidatesByServer.get(srvCode);

            if (srvCandidates == null || srvCandidates.isEmpty())
                continue;

            publishRefreshStatus(processId, "loading recent GitHub pull requests for " + srvCode, stats);
            List<PullRequest> recentPrs = recentPullRequests(srvCode);
            stats.githubLoaded += recentPrs.size();
            publishRefreshStatus(processId, "loaded " + recentPrs.size() + " recent GitHub pull requests for " +
                srvCode, stats);

            refreshJira(srvCode, srvCandidates, recentPrs, stats, processId);
            refreshGithub(srvCode, srvCandidates, recentPrs, stats, processId);
        }

        return stats.finishText();
    }

    /**
     * Refreshes and schedules next rare run.
     */
    private void refreshAndReschedule() {
        safeRefresh();
        scheduler.sheduleNamed(TASK_NAME, this::refreshAndReschedule, 6, TimeUnit.HOURS);
    }

    /**
     * Safe refresh wrapper for background tasks.
     */
    private void safeRefresh() {
        safeRefresh(null);
    }

    /**
     * Safe refresh wrapper for background tasks.
     *
     * @param processId Optional user-visible process id.
     */
    private void safeRefresh(@Nullable Long processId) {
        try {
            String res = refresh(processId);

            processMonitor.finish(processId, res);
            logger.info(res);
        }
        catch (RuntimeException e) {
            processMonitor.fail(processId, e);
            logger.warn("Failed to refresh test fix matches", e);
        }
    }

    /**
     * @param srvCode Server code.
     * @param recentPrs Recent GitHub pull requests.
     */
    private void refreshJira(String srvCode, List<TestFixCandidate> candidates, List<PullRequest> recentPrs,
        RefreshStats stats, @Nullable Long processId) {
        try {
            IJiraIgnited jira = jiraProvider.server(srvCode);
            IJiraServerConfig jiraCfg = jira.config();
            String label = jiraCfg.testFixesLabel();
            long cutoffTs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(jiraCfg.testFixesLookbackDays());

            publishRefreshStatus(processId, "checking JIRA tickets for " + srvCode, stats);
            for (Ticket ticket : jira.getTickets()) {
                if (ticket == null || ticket.fields == null)
                    continue;

                stats.jiraChecked++;

                if (stats.jiraChecked % 100 == 0)
                    publishRefreshStatus(processId, "checked JIRA tickets for " + srvCode, stats);

                Long updatedTs = parseDate(ticket.fields.updated());
                Long closedTs = parseDate(ticket.fields.resolutionDate());
                boolean labeled = ticket.fields.labels().stream().anyMatch(label::equalsIgnoreCase);

                if (!labeled && updatedTs != null && updatedTs < cutoffTs)
                    continue;

                String text = Strings.nullToEmpty(ticket.fields.summary()) + "\n" +
                    Strings.nullToEmpty(ticket.fields.description());

                List<PullRequest> linkedPrs = relatedPullRequests(ticket, recentPrs);

                for (TestFixCandidate candidate : matchingCandidates(text, candidates)) {
                    TestFixMatch match = candidate.newMatch();
                    match.sourceType = "jira";
                    match.sourceId = ticket.key;
                    match.sourceUrl = jira.generateTicketUrl(ticket.key);
                    match.title = ticket.fields.summary();
                    match.status = statusText(ticket.status());
                    match.updatedTs = updatedTs;
                    match.closedTs = closedTs;

                    saveMatch(match);
                    stats.jiraSaved++;

                    for (PullRequest pr : linkedPrs) {
                        TestFixMatch linkedPrMatch = githubMatch(candidate, pr);

                        saveMatch(linkedPrMatch);
                        stats.githubSaved++;
                    }
                }
            }

            publishRefreshStatus(processId, "checked JIRA tickets for " + srvCode, stats);
        }
        catch (RuntimeException e) {
            logger.debug("Skipping JIRA test fix matching for " + srvCode, e);
            publishRefreshStatus(processId, "skipped JIRA tickets for " + srvCode + ": " + e.getMessage(), stats);
        }
    }

    /**
     * @param srvCode Server code.
     * @param recentPrs Recent GitHub pull requests.
     */
    private void refreshGithub(String srvCode, List<TestFixCandidate> candidates, List<PullRequest> recentPrs,
        RefreshStats stats, @Nullable Long processId) {
        try {
            publishRefreshStatus(processId, "checking GitHub pull requests for " + srvCode, stats);
            for (PullRequest pr : recentPrs) {
                stats.githubChecked++;

                if (stats.githubChecked % 25 == 0)
                    publishRefreshStatus(processId, "checked GitHub pull requests for " + srvCode, stats);

                String text = Strings.nullToEmpty(pr.getTitle()) + "\n" + Strings.nullToEmpty(pr.getBody());

                for (TestFixCandidate candidate : matchingCandidates(text, candidates)) {
                    TestFixMatch match = githubMatch(candidate, pr);

                    saveMatch(match);
                    stats.githubSaved++;
                }
            }

            publishRefreshStatus(processId, "checked GitHub pull requests for " + srvCode, stats);
        }
        catch (RuntimeException e) {
            logger.debug("Skipping GitHub test fix matching for " + srvCode, e);
            publishRefreshStatus(processId, "skipped GitHub pull requests for " + srvCode + ": " + e.getMessage(),
                stats);
        }
    }

    /**
     * @param srvCode Server code.
     */
    private List<PullRequest> recentPullRequests(String srvCode) {
        try {
            IJiraServerConfig jiraCfg = cfg.getJiraConfig(srvCode);
            IGitHubConnIgnited gh = ghProvider.server(srvCode);

            return gh.getRecentPullRequests(jiraCfg.testFixesLookbackDays());
        }
        catch (RuntimeException e) {
            logger.debug("Skipping GitHub PR loading for test fix matching for " + srvCode, e);

            return new ArrayList<>();
        }
    }

    /**
     * @param candidate Candidate.
     * @param pr Pull request.
     */
    private static TestFixMatch githubMatch(TestFixCandidate candidate, PullRequest pr) {
        TestFixMatch match = candidate.newMatch();

        match.sourceType = "github";
        match.sourceId = "PR #" + pr.getNumber();
        match.sourceUrl = pr.htmlUrl();
        match.title = pr.getTitle();
        match.status = pr.getState();
        match.updatedTs = parseDate(pr.getTimeUpdate());
        match.closedTs = parseDate(pr.mergedAt());

        fillGithubAuthor(match, pr.gitHubUser());
        fillCommit(match, pr);

        return match;
    }

    /**
     * @param ticket JIRA ticket.
     * @param prs Recent pull requests.
     */
    private static List<PullRequest> relatedPullRequests(Ticket ticket, List<PullRequest> prs) {
        if (ticket == null || Strings.isNullOrEmpty(ticket.key) || prs.isEmpty())
            return new ArrayList<>();

        String ticketKey = ticket.key.toUpperCase(Locale.ROOT);

        return prs.stream()
            .filter(pr -> mentionsTicket(pr, ticketKey))
            .collect(Collectors.toList());
    }

    /**
     * @param pr Pull request.
     * @param ticketKey Uppercase JIRA ticket key.
     */
    private static boolean mentionsTicket(PullRequest pr, String ticketKey) {
        GitHubBranch head = pr.head();

        return containsIgnoreCase(pr.getTitle(), ticketKey) ||
            containsIgnoreCase(pr.getBody(), ticketKey) ||
            (head != null && containsIgnoreCase(head.ref(), ticketKey));
    }

    /**
     * @param text Text.
     * @param token Uppercase token.
     */
    private static boolean containsIgnoreCase(@Nullable String text, String token) {
        return !Strings.isNullOrEmpty(text) && text.toUpperCase(Locale.ROOT).contains(token);
    }

    /**
     * @param match Match.
     * @param user GitHub user.
     */
    private static void fillGithubAuthor(TestFixMatch match, @Nullable GitHubUser user) {
        if (user == null)
            return;

        match.author = user.login();
        match.authorUrl = Strings.isNullOrEmpty(user.login()) ? null : "https://github.com/" + user.login();
        match.authorAvatarUrl = user.avatarUrl();
    }

    /**
     * @param match Match.
     * @param pr Pull request.
     */
    private static void fillCommit(TestFixMatch match, PullRequest pr) {
        String sha = pr.mergeCommitSha();

        if (Strings.isNullOrEmpty(sha)) {
            GitHubBranch head = pr.head();

            if (head == null || Strings.isNullOrEmpty(head.sha()))
                return;

            sha = head.sha();
        }

        match.commitSha = sha;
        match.commitUrl = commitUrl(pr.htmlUrl(), sha);
    }

    /**
     * @param prUrl Pull request URL.
     * @param sha Commit SHA.
     */
    @Nullable private static String commitUrl(@Nullable String prUrl, String sha) {
        if (Strings.isNullOrEmpty(prUrl))
            return null;

        int pullIdx = prUrl.indexOf("/pull/");

        if (pullIdx < 0)
            return null;

        return prUrl.substring(0, pullIdx) + "/commit/" + sha;
    }

    /**
     * @param refs Refs.
     */
    private static List<TestFixRefUi> uniqueRefs(List<TestFixRefUi> refs) {
        Map<String, TestFixRefUi> res = new HashMap<>();

        for (TestFixRefUi ref : refs)
            res.putIfAbsent(ref.sourceType + ":" + ref.text, ref);

        return new ArrayList<>(res.values());
    }

    /**
     * @param refs Reverse refs.
     * @param source Source.
     */
    private TestFixRefUi toUi(TestFixRefs refs, TestFixSource source) {
        TestFixRefUi res = new TestFixRefUi();

        res.entityName = refs.entityName;
        res.suiteId = refs.suiteId;
        res.suiteName = refs.suiteName;
        res.testName = refs.testName;
        res.trackedBranch = refs.trackedBranch;
        res.currentStatusUrl = refs.currentStatusUrl;
        res.sourceType = source.sourceType;
        res.text = source.sourceId;
        res.url = source.sourceUrl;
        res.title = source.title;
        res.status = source.status;
        res.author = source.author;
        res.authorUrl = source.authorUrl;
        res.authorAvatarUrl = source.authorAvatarUrl;
        res.updatedDate = formatDate(source.updatedTs);
        res.closedDate = formatDate(source.closedTs);
        res.commitUrl = source.commitUrl;
        res.commitText = Strings.isNullOrEmpty(source.commitSha) ? null : shortSha(source.commitSha);

        return res;
    }

    /**
     * @param status Status.
     */
    private static String statusText(@Nullable JiraTicketStatusCode status) {
        return JiraTicketStatusCode.text(status);
    }

    /**
     * @param sha SHA.
     */
    private static String shortSha(String sha) {
        return sha.length() <= PullRequest.INCLUDE_SHORT_VER ? sha : sha.substring(0, PullRequest.INCLUDE_SHORT_VER);
    }

    /**
     * @param match Match.
     */
    private void saveMatch(TestFixMatch match) {
        if (!isPlausibleMatch(match))
            return;

        String sourceId = sourceKey(match);

        sourceCache.put(sourceId, source(match));

        for (String key : lookupKeys(match)) {
            TestFixRefs refs = lookupCache.get(key);

            if (refs == null)
                refs = refs(match);

            if (!refs.sourceIds.contains(sourceId))
                refs.sourceIds.add(sourceId);

            lookupCache.put(key, refs);
        }
    }

    /**
     * @param match Match.
     */
    private static TestFixSource source(TestFixMatch match) {
        TestFixSource res = new TestFixSource();

        res.sourceType = match.sourceType;
        res.sourceId = match.sourceId;
        res.sourceUrl = match.sourceUrl;
        res.title = match.title;
        res.status = match.status;
        res.author = match.author;
        res.authorUrl = match.authorUrl;
        res.authorAvatarUrl = match.authorAvatarUrl;
        res.updatedTs = match.updatedTs;
        res.closedTs = match.closedTs;
        res.commitSha = match.commitSha;
        res.commitUrl = match.commitUrl;

        return res;
    }

    /**
     * @param match Match.
     */
    private static TestFixRefs refs(TestFixMatch match) {
        TestFixRefs res = new TestFixRefs();

        res.entityName = match.entityName;
        res.suiteId = match.suiteId;
        res.suiteName = match.suiteName;
        res.testName = match.testName;
        res.testNameId = match.testNameId;
        res.trackedBranch = match.trackedBranch;
        res.currentStatusUrl = match.currentStatusUrl;

        return res;
    }

    /**
     * @param match Match.
     */
    private static String sourceKey(TestFixMatch match) {
        return normalize(match.sourceType) + ":" + normalize(match.sourceId);
    }

    /**
     * @param match Match.
     */
    private static Set<String> lookupKeys(TestFixMatch match) {
        Set<String> res = new LinkedHashSet<>();

        addLookupKey(res, TestFixLookupIndex.suiteLookupKey(match.suiteId));
        addLookupKey(res, TestFixLookupIndex.testLookupKey(match.suiteId, match.testNameId));

        return res;
    }

    /**
     * @param res Target keys.
     * @param key Lookup key.
     */
    private static void addLookupKey(Set<String> res, String key) {
        if (!Strings.isNullOrEmpty(key))
            res.add(key);
    }

    /**
     * @param suites Suites UI.
     */
    private TestFixLookupIndex lookupIndex(Collection<ShortSuiteUi> suites) {
        Set<String> keys = new LinkedHashSet<>();

        for (ShortSuiteUi suite : suites)
            collectLookupKeys(suite, keys);

        return lookupIndex(keys);
    }

    /**
     * @param lookupKeys Requested lookup keys.
     */
    private TestFixLookupIndex lookupIndex(Set<String> lookupKeys) {
        ensureCache();

        TestFixLookupIndex idx = new TestFixLookupIndex();

        if (lookupKeys.isEmpty())
            return idx;

        Map<String, TestFixRefs> mappings = lookupCache.getAll(lookupKeys);
        Map<String, TestFixSource> sources = sources(sourceIds(mappings.values()));

        for (Map.Entry<String, TestFixRefs> entry : mappings.entrySet())
            idx.add(entry.getKey(), refsFor(entry.getValue(), sources));

        idx.finish();

        return idx;
    }

    /**
     * @param suite Suite UI.
     * @param keys Target keys.
     */
    private static void collectLookupKeys(ShortSuiteUi suite, Set<String> keys) {
        if (suite == null)
            return;

        addLookupKey(keys, TestFixLookupIndex.suiteLookupKey(suite.suiteId));

        for (ShortTestFailureUi failure : suite.testFailures()) {
            addLookupKey(keys, TestFixLookupIndex.testLookupKey(suite.suiteId, failure.testNameId));
        }
    }

    /**
     * @param mappings Reverse mappings.
     */
    private static Set<String> sourceIds(Collection<TestFixRefs> mappings) {
        Set<String> res = new LinkedHashSet<>();

        for (TestFixRefs mapping : mappings) {
            if (mapping != null && mapping.sourceIds != null)
                res.addAll(mapping.sourceIds);
        }

        return res;
    }

    /**
     * @param sourceIds Source ids.
     */
    private Map<String, TestFixSource> sources(Set<String> sourceIds) {
        return sourceIds.isEmpty() ? Collections.emptyMap() : sourceCache.getAll(sourceIds);
    }

    /**
     * @param refs Reverse refs.
     * @param sources Source fixes by id.
     */
    private List<TestFixRefUi> refsFor(TestFixRefs refs, Map<String, TestFixSource> sources) {
        List<TestFixRefUi> res = new ArrayList<>();

        if (refs == null || refs.sourceIds == null)
            return res;

        for (String sourceId : refs.sourceIds) {
            TestFixSource source = sources.get(sourceId);

            if (source != null)
                res.add(toUi(refs, source));
        }

        return res.stream()
            .sorted(Comparator.comparingLong((TestFixRefUi ref) -> sortTs(ref)).reversed())
            .collect(Collectors.toList());
    }

    /**
     * @param value Value.
     */
    private static String normalize(@Nullable String value) {
        return Strings.nullToEmpty(value).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @param match Match.
     */
    private static long sortTs(TestFixMatch match) {
        if (match.closedTs != null)
            return match.closedTs;

        if (match.updatedTs != null)
            return match.updatedTs;

        return 0;
    }

    /**
     * @param ref UI ref.
     */
    private static long sortTs(TestFixRefUi ref) {
        long closed = dateScore(ref.closedDate);

        if (closed > 0)
            return closed;

        return dateScore(ref.updatedDate);
    }

    /**
     * @param date ISO local date.
     */
    private static long dateScore(@Nullable String date) {
        if (Strings.isNullOrEmpty(date))
            return 0;

        try {
            return LocalDate.parse(date).toEpochDay();
        }
        catch (DateTimeParseException ignored) {
            return 0;
        }
    }

    /**
     * @param date Date string.
     */
    @Nullable private static Long parseDate(@Nullable String date) {
        if (Strings.isNullOrEmpty(date))
            return null;

        try {
            return Instant.parse(date).toEpochMilli();
        }
        catch (DateTimeParseException ignored) {
            // Try JIRA offset format below.
        }

        try {
            return OffsetDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
                .toInstant().toEpochMilli();
        }
        catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * @param ts Timestamp.
     */
    @Nullable private static String formatDate(@Nullable Long ts) {
        if (ts == null)
            return null;

        return DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(ts).atZone(java.time.ZoneOffset.UTC));
    }

    /**
     * @return Server codes.
     */
    private Collection<String> serverCodes() {
        Set<String> res = new HashSet<>(cfg.getServerIds());

        if (!Strings.isNullOrEmpty(cfg.primaryServerCode()))
            res.add(cfg.primaryServerCode());

        return res;
    }

    /**
     * @return Match candidates grouped by TeamCity/GitHub/JIRA server code.
     */
    private Map<String, List<TestFixCandidate>> candidatesByServer() {
        Map<String, List<TestFixCandidate>> res = new HashMap<>();

        cfg.getTrackedBranches().branchesStream().forEach(branch -> collectCandidates(branch, res));

        return res;
    }

    /**
     * @param branch Tracked branch.
     * @param res Target map.
     */
    private void collectCandidates(ITrackedBranch branch, Map<String, List<TestFixCandidate>> res) {
        branch.chainsStream().forEach(chain -> {
            try {
                res.computeIfAbsent(chain.serverCode(), k -> new ArrayList<>())
                    .addAll(candidates(branch, chain));
            }
            catch (RuntimeException e) {
                logger.debug("Failed to collect test fix candidates [branch={}, server={}, suite={}]",
                    branch.name(), chain.serverCode(), chain.tcSuiteId(), e);
            }
        });
    }

    /**
     * @param branch Tracked branch.
     * @param chain Tracked chain.
     */
    private List<TestFixCandidate> candidates(ITrackedBranch branch, ITrackedChain chain) {
        ITeamcityIgnited tc = tcProvider.server(chain.serverCode(), null);
        String baseBranch = chain.tcBaseBranch().orElse(chain.tcBranch());
        Integer baseBranchId = compactor.getStringIdIfPresent(normalizeBranch(baseBranch));

        if (baseBranchId == null)
            return new ArrayList<>();

        List<TestFixCandidate> res = new ArrayList<>();

        for (String suiteId : suiteIdsForHistory(tc, chain.tcSuiteId(), baseBranch))
            res.addAll(candidates(branch, tc, baseBranchId, suiteId));

        return res;
    }

    /**
     * @param branch Tracked branch.
     * @param tc TeamCity storage.
     * @param baseBranchId Compacted base branch id.
     * @param suiteId TeamCity build configuration id.
     */
    private List<TestFixCandidate> candidates(ITrackedBranch branch, ITeamcityIgnited tc, Integer baseBranchId,
        String suiteId) {
        Integer compactedSuiteId = compactor.getStringIdIfPresent(suiteId);

        if (compactedSuiteId == null)
            return new ArrayList<>();

        ISuiteRunHistory hist = tc.getSuiteRunHist(compactedSuiteId, baseBranchId);

        if (hist == null)
            return new ArrayList<>();

        List<TestFixCandidate> res = new ArrayList<>();

        for (Integer testNameId : hist.testNames()) {
            String fullTestName = compactor.getStringFromId(testNameId);

            if (Strings.isNullOrEmpty(fullTestName))
                continue;

            String shortTestName = ShortTestFailureUi.extractTest(fullTestName);

            if (!isPlausibleTestName(shortTestName))
                continue;

            String suiteName = testSuiteName(fullTestName, shortTestName);

            String currentStatusUrl = tc.host() + "buildConfiguration/" + suiteId + "?branch=" +
                org.apache.ignite.tcbot.common.util.UrlUtil.escape(ITcServerConfig.DEFAULT_TRACKED_BRANCH_NAME);

            res.add(new TestFixCandidate(branch.name(), suiteId, suiteName, testNameId, fullTestName, shortTestName,
                currentStatusUrl));
        }

        return res;
    }

    /**
     * @param tc TeamCity storage.
     * @param rootSuiteId Root tracked chain build configuration id.
     * @param baseBranch Base branch in TeamCity history.
     */
    private List<String> suiteIdsForHistory(ITeamcityIgnited tc, String rootSuiteId, String baseBranch) {
        LinkedHashSet<String> res = new LinkedHashSet<>();

        collectSuiteIds(tc, rootSuiteId, res, new HashSet<>());
        collectSuiteIdsFromRecentBuilds(tc, rootSuiteId, baseBranch, res);

        return new ArrayList<>(res);
    }

    /**
     * @param tc TeamCity storage.
     * @param suiteId TeamCity build configuration id.
     * @param res Collected suite ids.
     * @param visited Visited build configurations.
     */
    private void collectSuiteIds(ITeamcityIgnited tc, @Nullable String suiteId, LinkedHashSet<String> res,
        Set<String> visited) {
        if (Strings.isNullOrEmpty(suiteId) || !visited.add(suiteId))
            return;

        res.add(suiteId);

        BuildTypeCompacted buildType = tc.getBuildType(suiteId);

        if (buildType == null)
            return;

        for (SnapshotDependencyCompacted dep : buildType.snapshotDependencies()) {
            BuildType depBuildType = dep.toSnapshotDependency(compactor).bt();

            if (depBuildType != null)
                collectSuiteIds(tc, depBuildType.getId(), res, visited);
        }
    }

    /**
     * @param tc TeamCity storage.
     * @param rootSuiteId Root tracked chain build configuration id.
     * @param baseBranch Base branch in TeamCity history.
     * @param res Collected suite ids.
     */
    private void collectSuiteIdsFromRecentBuilds(ITeamcityIgnited tc, String rootSuiteId, String baseBranch,
        LinkedHashSet<String> res) {
        try {
            for (Integer buildId : tc.getLastNBuildsFromHistory(rootSuiteId, baseBranch, 3))
                collectSuiteIdsFromBuild(tc, buildId, res, new HashSet<>());
        }
        catch (RuntimeException e) {
            logger.debug("Failed to collect test fix suite ids from recent builds [suite={}, branch={}]",
                rootSuiteId, baseBranch, e);
        }
    }

    /**
     * @param tc TeamCity storage.
     * @param buildId Build id.
     * @param res Collected suite ids.
     * @param visitedBuilds Visited build ids.
     */
    private void collectSuiteIdsFromBuild(ITeamcityIgnited tc, @Nullable Integer buildId, LinkedHashSet<String> res,
        Set<Integer> visitedBuilds) {
        if (buildId == null || !visitedBuilds.add(buildId))
            return;

        FatBuildCompacted build = tc.getFatBuild(buildId);

        if (build == null || build.isFakeStub())
            return;

        String suiteId = build.buildTypeId(compactor);

        if (!Strings.isNullOrEmpty(suiteId))
            res.add(suiteId);

        for (int depId : build.snapshotDependencies())
            collectSuiteIdsFromBuild(tc, depId, res, visitedBuilds);
    }

    /**
     * @param fullTestName Full TeamCity test name.
     * @param shortTestName Short test name in Class.method form.
     */
    @Nullable private static String testSuiteName(String fullTestName, @Nullable String shortTestName) {
        if (!Strings.isNullOrEmpty(shortTestName)) {
            int dot = shortTestName.lastIndexOf('.');

            if (dot > 0)
                return shortTestName.substring(0, dot);
        }

        return ShortTestFailureUi.extractSuite(fullTestName);
    }

    /**
     * @param text Source title/body/description.
     * @param candidates Candidates from tracked branch histories.
     */
    static Collection<TestFixCandidate> matchingCandidates(String text, List<TestFixCandidate> candidates) {
        String rawSrc = normalize(text);
        String src = mentionKey(rawSrc);
        Map<String, TestFixCandidate> res = new LinkedHashMap<>();

        for (TestFixCandidate candidate : candidates) {
            if (candidate.matchesQualified(src))
                res.putIfAbsent(candidate.key(), candidate);
        }

        if (!res.isEmpty())
            return res.values();

        for (TestFixCandidate candidate : candidates) {
            if (candidate.matchesUnqualified(src, rawSrc))
                res.putIfAbsent(candidate.key(), candidate);
        }

        return res.values();
    }

    /**
     * @param match Persisted match.
     */
    private static boolean isPlausibleMatch(TestFixMatch match) {
        return match != null && isPlausibleTestName(match.testName) && !Strings.isNullOrEmpty(match.suiteId);
    }

    /**
     * @param testName Short test name in Class.method form.
     */
    static boolean isPlausibleTestName(@Nullable String testName) {
        if (Strings.isNullOrEmpty(testName))
            return false;

        return testName.matches("[A-Za-z_$][A-Za-z0-9_$]*[.][A-Za-z_$][A-Za-z0-9_$]*(?:\\[[^\\]]+\\])?");
    }

    /**
     * @param value Source text or a candidate alias.
     */
    private static String mentionKey(@Nullable String value) {
        String normalized = normalize(value);
        StringBuilder res = new StringBuilder(normalized.length());
        boolean lastSeparator = true;

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);

            if (Character.isLetterOrDigit(ch)) {
                res.append(ch);
                lastSeparator = false;
            }
            else if (!lastSeparator) {
                res.append('.');
                lastSeparator = true;
            }
        }

        int len = res.length();

        if (len > 0 && res.charAt(len - 1) == '.')
            res.deleteCharAt(len - 1);

        return res.toString();
    }

    /**
     * @param src Canonical source text.
     * @param token Source token.
     */
    private static boolean containsMention(String src, @Nullable String token) {
        if (Strings.isNullOrEmpty(token))
            return false;

        String needle = mentionKey(token);

        if (Strings.isNullOrEmpty(needle))
            return false;

        int start = 0;

        while (start <= src.length() - needle.length()) {
            int idx = src.indexOf(needle, start);

            if (idx < 0)
                return false;

            int end = idx + needle.length();
            boolean leftBoundary = idx == 0 || src.charAt(idx - 1) == '.';
            boolean rightBoundary = end == src.length() || src.charAt(end) == '.';

            if (leftBoundary && rightBoundary)
                return true;

            start = idx + 1;
        }

        return false;
    }

    /**
     * @param src Normalized source text.
     * @param token Standalone name token.
     */
    private static boolean containsStandaloneName(String src, @Nullable String token) {
        String needle = normalize(token);

        if (Strings.isNullOrEmpty(needle))
            return false;

        int start = 0;

        while (start <= src.length() - needle.length()) {
            int idx = src.indexOf(needle, start);

            if (idx < 0)
                return false;

            int end = idx + needle.length();
            boolean leftBoundary = idx == 0 || !Character.isLetterOrDigit(src.charAt(idx - 1));
            boolean rightBoundary = end == src.length() || !Character.isLetterOrDigit(src.charAt(end));
            boolean followedByMethod = end + 1 < src.length() && (src.charAt(end) == '.' || src.charAt(end) == '#') &&
                Character.isLetterOrDigit(src.charAt(end + 1));

            if (leftBoundary && rightBoundary && !followedByMethod)
                return true;

            start = idx + 1;
        }

        return false;
    }

    /**
     * Initializes cache.
     */
    private void ensureCache() {
        if (sourceCache != null)
            return;

        synchronized (this) {
            if (sourceCache == null) {
                sourceCache = igniteProvider.get().getOrCreateCache(
                    CacheConfigs.getCache8PartsConfig(SOURCE_CACHE_NAME));
                lookupCache = igniteProvider.get().getOrCreateCache(
                    CacheConfigs.getCache8PartsConfig(REFS_CACHE_NAME));
            }
        }
    }

    /**
     * @param processId Optional user-visible process id.
     * @param stage Current refresh stage.
     * @param stats Refresh stats.
     */
    private void publishRefreshStatus(@Nullable Long processId, String stage, RefreshStats stats) {
        processMonitor.status(processId, "Test fix mapping: " + stage + ". " + stats.progressText());
    }

    /** Test fix refresh counters. */
    private static class RefreshStats {
        /** Candidate test names from tracked suite histories. */
        private int candidates;

        /** Unique suites with history candidates. */
        private int suites;

        /** Unique full test names with history candidates. */
        private int tests;

        /** Recent GitHub PRs loaded from cache. */
        private int githubLoaded;

        /** GitHub PRs checked against candidates. */
        private int githubChecked;

        /** JIRA tickets checked against candidates. */
        private int jiraChecked;

        /** Saved JIRA matches. */
        private int jiraSaved;

        /** Saved GitHub matches. */
        private int githubSaved;

        /**
         * @param candidatesByServer Candidates by server.
         */
        private void collectCandidateStats(Map<String, List<TestFixCandidate>> candidatesByServer) {
            Set<String> suiteIds = new HashSet<>();
            Set<String> testNames = new HashSet<>();

            for (Map.Entry<String, List<TestFixCandidate>> entry : candidatesByServer.entrySet()) {
                for (TestFixCandidate candidate : entry.getValue()) {
                    candidates++;
                    suiteIds.add(entry.getKey() + ":" + candidate.suiteId);
                    testNames.add(candidate.fullTestName);
                }
            }

            suites = suiteIds.size();
            tests = testNames.size();
        }

        /** */
        private int saved() {
            return jiraSaved + githubSaved;
        }

        /** */
        private String progressText() {
            return "Analyzed " + tests + " unique test names (" + candidates + " candidate rows) in " + suites +
                " suites. Checked sources: GitHub " + githubChecked + "/" + githubLoaded + " PRs, JIRA " +
                jiraChecked + " tickets. Matches saved: " + saved() + " (JIRA " + jiraSaved + ", GitHub " +
                githubSaved + ").";
        }

        /** */
        private String finishText() {
            return "Test fix matches refreshed: " + saved() + " (tests=" + tests + ", candidates=" + candidates +
                ", suites=" + suites + ", githubChecked=" + githubChecked + "/" + githubLoaded +
                ", jiraChecked=" + jiraChecked + ", jira=" + jiraSaved + ", github=" + githubSaved + ")";
        }
    }

    /** Test fix candidate resolved from tracked branch suite history. */
    static class TestFixCandidate {
        /** Tracked branch. */
        private final String trackedBranch;

        /** Suite id. */
        private final String suiteId;

        /** Suite display name. */
        private final String suiteName;

        /** Compacted full test name id. */
        private final Integer testNameId;

        /** Full test name. */
        private final String fullTestName;

        /** Short test name. */
        private final String shortTestName;

        /** Current master TeamCity suite status URL. */
        private final String currentStatusUrl;

        /**
         * @param trackedBranch Tracked branch.
         * @param suiteId Suite id.
         * @param suiteName Suite display name.
         * @param testNameId Compacted full test name id.
         * @param fullTestName Full test name.
         * @param shortTestName Short test name.
         */
        TestFixCandidate(String trackedBranch, String suiteId, @Nullable String suiteName, Integer testNameId,
            String fullTestName, @Nullable String shortTestName, String currentStatusUrl) {
            this.trackedBranch = trackedBranch;
            this.suiteId = suiteId;
            this.suiteName = suiteName;
            this.testNameId = testNameId;
            this.fullTestName = fullTestName;
            this.shortTestName = shortTestName;
            this.currentStatusUrl = currentStatusUrl;
        }

        /**
         * @param src Canonical source text.
         */
        private boolean matchesQualified(String src) {
            return containsMention(src, suiteId + "." + shortTestName) ||
                containsMention(src, suiteId + "." + fullTestName);
        }

        /**
         * @param src Canonical source text.
         * @param rawSrc Normalized source text.
         */
        private boolean matchesUnqualified(String src, String rawSrc) {
            return containsMention(src, fullTestName) ||
                containsMention(src, shortTestName);
        }

        /** */
        private TestFixMatch newMatch() {
            TestFixMatch match = new TestFixMatch();

            match.entityName = Strings.isNullOrEmpty(shortTestName) ? fullTestName : shortTestName;
            match.trackedBranch = trackedBranch;
            match.suiteId = suiteId;
            match.suiteName = suiteName;
            match.testName = Strings.isNullOrEmpty(shortTestName) ? fullTestName : shortTestName;
            match.testNameId = testNameId;
            match.currentStatusUrl = currentStatusUrl;

            return match;
        }

        /** */
        private String key() {
            return normalize(suiteId) + ":" + normalize(fullTestName);
        }

    }
}
