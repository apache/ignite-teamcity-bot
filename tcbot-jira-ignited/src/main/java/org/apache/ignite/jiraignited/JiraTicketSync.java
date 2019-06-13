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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.jiraservice.Tickets;
import org.apache.ignite.jiraservice.Fields;
import org.apache.ignite.jiraservice.IJiraIntegration;
import org.apache.ignite.jiraservice.IJiraIntegrationProvider;
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.internal.util.typedef.F;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.tcbot.common.util.UrlUtil.escape;

/**
 * Sync serving requests for all JIRA servers.
 */
public class JiraTicketSync {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(JiraTicketSync.class);

    /** Scheduler. */
    @Inject private IScheduler scheduler;

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

        String reqFields = Arrays.stream(Fields.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.joining(","));

        IJiraServerConfig cfg = jira.config();
        String projectCode = cfg.projectCodeForVisa();
        String baseUrl = "search?jql=" + escape("project=" + projectCode + " order by updated DESC")
            + "&" +
            "fields=" + reqFields +
            "&maxResults=100";

        String url = baseUrl;

        logger.info("Requesting JIRA tickets using URL " + url + ("\n" + cfg.restApiUrl() + url));
        Tickets tickets = jira.getTicketsPage(url);
        Collection<Ticket> page = tickets.issuesNotNull();

        if (F.isEmpty(page))
            return "Something went wrong - no tickets found. Check jira availability: " +
                "[project=" + projectCode + ", url=" + url + "]";

        int ticketsSaved = jiraDao.saveChunk(srvIdMaskHigh, page, projectCode);

        int ticketsProcessed = page.size();

        if (ticketsSaved != 0 || fullResync) {
            while (tickets.nextStart() > 0) {
                url = baseUrl + "&startAt=" + tickets.nextStart();

                logger.info("Requesting JIRA tickets using URL " + url + ("\n" + cfg.restApiUrl() + url));
                tickets = jira.getTicketsPage(url);

                page = tickets.issuesNotNull();

                if (F.isEmpty(page))
                    break;

                int savedNow = jiraDao.saveChunk(srvIdMaskHigh, page, projectCode);

                ticketsSaved += savedNow;
                ticketsProcessed += page.size();

                if (savedNow == 0 && !fullResync)
                    break; // find not updated chunk and exit
            }
        }

        return "Jira tickets saved " + ticketsSaved + " from " + ticketsProcessed + " checked for service " + srvCode;
    }
}
