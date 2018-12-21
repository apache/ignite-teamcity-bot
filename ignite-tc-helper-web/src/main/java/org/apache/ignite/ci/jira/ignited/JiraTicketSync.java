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
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.jira.IJiraIntegrationProvider;
import org.apache.ignite.ci.jira.Ticket;
import org.apache.ignite.ci.jira.Tickets;
import org.apache.ignite.ci.teamcity.pure.ITeamcityConn;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.internal.util.typedef.F;

/**
 * 
 */
public class JiraTicketSync {
    /** Scheduler. */
    @Inject private IScheduler scheduler;

    /** Mute DAO. */
    @Inject private JiraTicketDao jiraDao;

    /** Jira integration provider. */
    @Inject IJiraIntegrationProvider jiraIntegrationProvider;

    /**
     * @param taskName Task name.
     * @param srvIdMaskHigh Server id mask high.
     * @param creds Credentials.
     * @param conn Connection.
     */
    public void ensureActualizeJiraTickets(String taskName, int srvIdMaskHigh, ICredentialsProv creds, ITeamcityConn conn) {
        scheduler.sheduleNamed(taskName, () -> actualizeJiraTickets(srvIdMaskHigh, conn, creds), 15, TimeUnit.MINUTES);
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param conn Connection.
     * @param creds Credentials.
     */
    @MonitoredTask(name = "Actualize Jira", nameExtArgsIndexes = {0})
    private String actualizeJiraTickets(int srvIdMaskHigh, ITeamcityConn conn, ICredentialsProv creds) {
        String srvId = conn.serverId();
        IJiraIntegration jira = jiraIntegrationProvider.server(srvId);
        String url = "search?jql=project%20=%20IGNITE%20order%20by%20updated%20DESC&fields=status&maxResults=100";
        Tickets tickets = jira.getTickets(srvId, creds, url);
        Collection<Ticket> page = tickets.issuesNotNull();

        if (F.isEmpty(page))
            return "Something went wrong - no tickets found. Check jira availability.";

        jiraDao.saveChunk(srvIdMaskHigh, page, jira.ticketTemplate());

        int ticketsSaved = page.size();

        while (tickets.nextStart() > 0) {
            url = "search?jql=project%20=%20IGNITE%20order%20by%20updated%20DESC&fields=status&maxResults=100&startAt=" +
                tickets.nextStart();

            tickets = jira.getTickets(srvId, creds, url);

            page = tickets.issuesNotNull();

            if (F.isEmpty(page))
                break;

            jiraDao.saveChunk(srvIdMaskHigh, page, jira.ticketTemplate());

            ticketsSaved += page.size();
        }

        return "Jira tickets saved " + ticketsSaved + " for " + srvId;
    }
}
