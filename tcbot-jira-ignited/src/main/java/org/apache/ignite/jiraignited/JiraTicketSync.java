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

package org.apache.ignite.jiraignited;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.jiraservice.IFields;
import org.apache.ignite.jiraservice.IJiraIntegration;
import org.apache.ignite.jiraservice.IJiraIntegrationProvider;
import org.apache.ignite.jiraservice.ITickets;
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.tcbot.common.util.UrlUtil.escape;

/**
 * Sync serving requests for all JIRA servers.
 */
public class JiraTicketSync {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(JiraTicketSync.class);

    /** Source update signal cache name. Also used by test-fix matching. */
    private static final String SOURCE_UPDATES_CACHE_NAME = "testFixSourceUpdates";

    /** Test-fix JIRA sync state cache name. */
    private static final String TEST_FIX_SYNC_STATE_CACHE_NAME = "jiraTestFixSyncState";

    /** Labeled test-fix catch-up period. */
    private static final long TEST_FIX_LABEL_SYNC_PERIOD_MS = TimeUnit.DAYS.toMillis(1);

    /** Max age for labeled test-fix catch-up. */
    private static final int TEST_FIX_LABEL_LOOKBACK_DAYS = 365;

    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Mute DAO. */
    @Inject private JiraTicketDao jiraDao;

    /** Jira integration provider. */
    @Inject IJiraIntegrationProvider jiraIntegrationProvider;

    /**
     * @param srvCode Server ID
     */
    public void ensureActualizeJiraTickets(String srvCode) {
        scheduler.sheduleNamed(taskName("incrementalSync", srvCode),
            () -> incrementalUpdate(srvCode), 15, TimeUnit.MINUTES);
    }

    /**
     * @param srvCode Server id.
     */
    public String incrementalUpdate(String srvCode) {
        String res = actualizeJiraTickets(srvCode, false);

        scheduler.invokeLater(() -> {
                scheduler.sheduleNamed(taskName("fullResync", srvCode),
                    () -> actualizeJiraTickets(srvCode, true), 3, TimeUnit.HOURS);
            },
            5, TimeUnit.MINUTES);

        return res;
    }

    /**
     * @param taskName Task name.
     * @param srvCode Service ID
     * @return Task name concatenated with server name.
     */
    @Nonnull
    private String taskName(String taskName, String srvCode) {
        return JiraTicketSync.class.getSimpleName() + "." + taskName + "." + srvCode;
    }

    /**
     * @param srvCode Server internal identification.
     * @param fullResync full or incremental.
     */
    @SuppressWarnings("WeakerAccess")
    @MonitoredTask(name = "Actualize Jira(srv, full resync)", nameExtArgsIndexes = {0, 1})
    protected String actualizeJiraTickets(String srvCode, boolean fullResync) {
        int srvIdMaskHigh = IJiraIgnited.serverIdToInt(srvCode);
        IJiraIntegration jira = jiraIntegrationProvider.server(srvCode);

        String reqFields = String.join(",", IFields.FIELD_NAMES);

        IJiraServerConfig cfg = jira.config();
        String projectCode = cfg.projectCodeForVisa();
        String baseUrl = searchUrl(cfg, "project=" + projectCode + " order by updated DESC", reqFields);
        Set<Long> processedKeys = new HashSet<>();

        SyncStats stats = loadQuery(jira, cfg, srvIdMaskHigh, projectCode, baseUrl, fullResync, processedKeys);
        SyncStats recent = new SyncStats();
        SyncStats labeled = new SyncStats();
        int recentDays = 0;
        boolean labelSync = false;

        if (fullResync)
            markTestFixJiraSyncComplete(srvCode);
        else {
            recentDays = recentLookbackDays(srvCode, cfg);
            recent = loadQuery(jira, cfg, srvIdMaskHigh, projectCode,
                searchUrl(cfg, "project=" + projectCode + " AND updated >= -" + recentDays +
                    "d order by updated DESC", reqFields), true, processedKeys);
            markTestFixRecentJiraSyncComplete(srvCode);

            labelSync = shouldSyncTestFixLabels(srvCode);

            if (labelSync) {
                labeled = loadQuery(jira, cfg, srvIdMaskHigh, projectCode,
                    searchUrl(cfg, "project=" + projectCode + " AND labels = " + cfg.testFixesLabel() +
                        " AND updated >= -" + TEST_FIX_LABEL_LOOKBACK_DAYS + "d order by updated DESC", reqFields),
                    true, processedKeys);
                markTestFixLabelJiraSyncComplete(srvCode);
            }
        }

        int saved = stats.saved + recent.saved + labeled.saved;

        if (saved > 0)
            signalSourceUpdate("jira:" + srvCode);

        return "Jira tickets saved " + saved + " from " +
            (stats.processed + recent.processed + labeled.processed) + " checked for service " + srvCode +
            " (recentDays=" + recentDays + ", labelSync=" + labelSync + ", duplicatesSkipped=" +
            (stats.duplicatesSkipped + recent.duplicatesSkipped + labeled.duplicatesSkipped) + ")";
    }

    /**
     * @param key Source key.
     */
    private void signalSourceUpdate(String key) {
        IgniteCache<String, Long> cache = igniteProvider.get().getOrCreateCache(
            CacheConfigs.getCache8PartsConfig(SOURCE_UPDATES_CACHE_NAME));

        cache.put(key, System.currentTimeMillis());
    }

    /**
     * @param srvCode Server code.
     * @param cfg JIRA config.
     */
    private int recentLookbackDays(String srvCode, IJiraServerConfig cfg) {
        Long lastSyncTs = testFixSyncStateCache().get(testFixRecentSyncKey(srvCode));
        int maxDays = Math.max(1, cfg.testFixesLookbackDays());

        if (lastSyncTs == null)
            return maxDays;

        long elapsedMs = Math.max(0, System.currentTimeMillis() - lastSyncTs);
        int elapsedDays = (int)(elapsedMs / TimeUnit.DAYS.toMillis(1)) + 1;

        return Math.min(maxDays, Math.max(1, elapsedDays));
    }

    /**
     * @param srvCode Server code.
     */
    private boolean shouldSyncTestFixLabels(String srvCode) {
        Long lastSyncTs = testFixSyncStateCache().get(testFixLabelSyncKey(srvCode));

        return lastSyncTs == null || System.currentTimeMillis() - lastSyncTs >= TEST_FIX_LABEL_SYNC_PERIOD_MS;
    }

    /**
     * @param srvCode Server code.
     */
    private void markTestFixJiraSyncComplete(String srvCode) {
        long now = System.currentTimeMillis();
        IgniteCache<String, Long> cache = testFixSyncStateCache();

        cache.put(testFixRecentSyncKey(srvCode), now);
        cache.put(testFixLabelSyncKey(srvCode), now);
    }

    /**
     * @param srvCode Server code.
     */
    private void markTestFixRecentJiraSyncComplete(String srvCode) {
        testFixSyncStateCache().put(testFixRecentSyncKey(srvCode), System.currentTimeMillis());
    }

    /**
     * @param srvCode Server code.
     */
    private void markTestFixLabelJiraSyncComplete(String srvCode) {
        testFixSyncStateCache().put(testFixLabelSyncKey(srvCode), System.currentTimeMillis());
    }

    /** */
    private IgniteCache<String, Long> testFixSyncStateCache() {
        return igniteProvider.get().getOrCreateCache(CacheConfigs.getCache8PartsConfig(TEST_FIX_SYNC_STATE_CACHE_NAME));
    }

    /**
     * @param srvCode Server code.
     */
    private static String testFixRecentSyncKey(String srvCode) {
        return "recent:" + srvCode;
    }

    /**
     * @param srvCode Server code.
     */
    private static String testFixLabelSyncKey(String srvCode) {
        return "label:" + srvCode;
    }

    /**
     * @param cfg JIRA config.
     * @param jql JQL query.
     * @param reqFields Requested fields.
     */
    private String searchUrl(IJiraServerConfig cfg, String jql, String reqFields) {
        return cfg.getApiVersion().searchUrl() + escape(jql) + "&fields=" + reqFields + "&maxResults=100";
    }

    /**
     * @param jira JIRA facade.
     * @param cfg JIRA config.
     * @param srvIdMaskHigh Server id mask high.
     * @param projectCode Project code.
     * @param baseUrl Search URL.
     * @param fullResync Full resync flag.
     */
    private SyncStats loadQuery(IJiraIntegration jira, IJiraServerConfig cfg, int srvIdMaskHigh, String projectCode,
        String baseUrl, boolean fullResync, Set<Long> processedKeys) {
        String url = baseUrl;

        logger.info("Requesting JIRA tickets using URL " + url + ("\n" + cfg.restApiUrl() + url));
        ITickets tickets = jira.getTicketsPage(url);
        Collection<Ticket> page = tickets.issues();

        if (F.isEmpty(page))
            return new SyncStats();

        SyncStats res = new SyncStats();
        Collection<Ticket> uniquePage = filterProcessedTickets(srvIdMaskHigh, page, projectCode, processedKeys, res);
        int ticketsSaved = jiraDao.saveChunk(srvIdMaskHigh, uniquePage, projectCode);

        res.saved += ticketsSaved;
        res.processed += uniquePage.size();

        if (ticketsSaved != 0 || fullResync) {
            while (tickets.hasNextPage()) {
                url = baseUrl + tickets.nextPagePosition();

                logger.info("Requesting JIRA tickets using URL " + url + ("\n" + cfg.restApiUrl() + url));
                tickets = jira.getTicketsPage(url);

                page = tickets.issues();

                if (F.isEmpty(page))
                    break;

                uniquePage = filterProcessedTickets(srvIdMaskHigh, page, projectCode, processedKeys, res);
                int savedNow = jiraDao.saveChunk(srvIdMaskHigh, uniquePage, projectCode);

                res.saved += savedNow;
                res.processed += uniquePage.size();

                if (savedNow == 0 && !fullResync)
                    break; // find not updated chunk and exit
            }
        }

        return res;
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param page Tickets page.
     * @param projectCode Project code.
     * @param processedKeys Already processed cache keys.
     * @param stats Sync stats.
     */
    private static Collection<Ticket> filterProcessedTickets(int srvIdMaskHigh, Collection<Ticket> page,
        String projectCode, Set<Long> processedKeys, SyncStats stats) {
        Collection<Ticket> res = new ArrayList<>();

        for (Ticket ticket : page) {
            long key = JiraTicketDao.ticketToCacheKey(srvIdMaskHigh, ticket.keyWithoutProject(projectCode));

            if (processedKeys.add(key))
                res.add(ticket);
            else
                stats.duplicatesSkipped++;
        }

        return res;
    }

    /** Sync stats. */
    private static class SyncStats {
        /** Saved count. */
        private int saved;

        /** Processed count. */
        private int processed;

        /** Duplicate tickets skipped before save. */
        private int duplicatesSkipped;
    }
}
