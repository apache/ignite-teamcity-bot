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

import java.io.IOException;
import java.util.Set;
import javax.inject.Inject;
import org.apache.ignite.jiraservice.IJiraIntegration;
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;

/**
 *
 */
class JiraIgnited implements IJiraIgnited {
    /** Pure HTTP Jira connection. */
    private IJiraIntegration jira;

    /** Jira ticket DAO. */
    @Inject private JiraTicketDao jiraTicketDao;

    /** Jira ticket Sync. */
    @Inject private JiraTicketSync jiraTicketSync;

    /** Server internal ID to bind services. */
    private String srvCode;

    /** Server id mask high. */
    private int srvIdMaskHigh;

    /**
     * @param jira Pure Jira integration.
     */
    public void init(IJiraIntegration jira) {
        this.jira = jira;

        srvCode = jira.config().getCode();

        srvIdMaskHigh = IJiraIgnited.serverIdToInt(srvCode);

        jiraTicketDao.init();
    }

    /** {@inheritDoc} */
    @Override public Set<Ticket> getTickets() {
        jiraTicketSync.ensureActualizeJiraTickets(srvCode);

        return jiraTicketDao.getTickets(srvIdMaskHigh, jira.config().projectCodeForVisa());
    }

    /** {@inheritDoc} */
    @Override public String generateCommentUrl(String ticketFullName, int commentId) {
        return jira.generateCommentUrl(ticketFullName, commentId);
    }

    /** {@inheritDoc} */
    @Override public String generateTicketUrl(String id) {
        return jira.generateTicketUrl(id);
    }

    /** {@inheritDoc} */
    @Override public String postJiraComment(String ticket, String comment) throws IOException {
        return jira.postJiraComment(ticket, comment);
    }

    /** {@inheritDoc} */
    @Override public IJiraServerConfig config() {
        return jira.config();
    }
}
