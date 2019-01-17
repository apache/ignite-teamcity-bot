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

package org.apache.ignite.ci.jira.ignited;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.jira.pure.IJiraIntegration;
import org.apache.ignite.ci.jira.pure.IJiraIntegrationProvider;
import org.apache.ignite.ci.jira.Ticket;
import org.apache.ignite.ci.jira.Tickets;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.internal.util.typedef.F;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.ci.util.UrlUtil.escape;

/**
 * Sync serving requests for all JIRA servers.
 */
public class JiraTicketSync {
    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** Mute DAO. */
    @Inject private JiraTicketDao jiraDao;

    /** Jira integration provider. */
    @Inject IJiraIntegrationProvider jiraIntegrationProvider;

    /**
     * @param srvId Server ID
     */
    public void ensureActualizeJiraTickets(String srvId) {
        scheduler.sheduleNamed(taskName("actualizeJiraTickets", srvId),
            () -> actualizeJiraTickets(srvId), 15, TimeUnit.MINUTES);
    }

    /**
     * @param taskName Task name.
     * @param srvId Service ID
     * @return Task name concatenated with server name.
     */
    @NotNull
    private String taskName(String taskName, String srvId) {
        return JiraTicketSync.class.getSimpleName() + "." + taskName + "." + srvId;
    }
    /**
     * @param srvId Server internal identification.
     */
    @MonitoredTask(name = "Actualize Jira", nameExtArgsIndexes = {0})
    protected String actualizeJiraTickets(String srvId) {
        int srvIdMaskHigh = ITeamcityIgnited.serverIdToInt(srvId);
        IJiraIntegration jira = jiraIntegrationProvider.server(srvId);

        String projectName = jira.projectName();
        String baseUrl = "search?jql=" + escape("project=" + projectName + " order by updated DESC")
            + "&fields=status&maxResults=100";

        String url = baseUrl;
        Tickets tickets = jira.getTicketsPage(srvId, url);
        Collection<Ticket> page = tickets.issuesNotNull();

        if (F.isEmpty(page))
            return "Something went wrong - no tickets found. Check jira availability.";

        jiraDao.saveChunk(srvIdMaskHigh, page, jira.ticketPrefix());

        int ticketsSaved = page.size();

        while (tickets.nextStart() > 0) {
            url = baseUrl + "&startAt=" + tickets.nextStart();

            tickets = jira.getTicketsPage(srvId, url);

            page = tickets.issuesNotNull();

            if (F.isEmpty(page))
                break;

            //todo find not updated chunk and exit
            jiraDao.saveChunk(srvIdMaskHigh, page, jira.ticketPrefix());

            ticketsSaved += page.size();
        }

        return "Jira tickets saved " + ticketsSaved + " for " + srvId;
    }
}
