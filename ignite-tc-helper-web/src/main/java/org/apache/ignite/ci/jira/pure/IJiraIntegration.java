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

package org.apache.ignite.ci.jira.pure;

import java.io.IOException;
import org.apache.ignite.ci.jira.Tickets;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.jetbrains.annotations.NotNull;

/**
 * Reperesents methods to provide interaction with Jira servers.
 */
public interface IJiraIntegration {
    /** Message to show user when JIRA ticket was successfully commented by the Bot. */
    public static String JIRA_COMMENTED = "JIRA commented.";

    /**
     * @param ticket JIRA ticket full name. E.g 'IGNITE-5555'.
     * @param comment Comment to be placed in the ticket conversation.
     * @return {@code True} if ticket was succesfully commented. Otherwise - {@code false}.
     * @throws IOException If failed to comment JIRA ticket.
     * @throws IllegalStateException If can't find URL to the JIRA.
     */
    public String sendJiraComment(String ticket, String comment) throws IOException;

    /**
     * Produce wrapper for collection of Jira tickets for given server.
     *
     * @param srvId Server id.
     * @param prov Prov.
     * @param url Ticket id.
     * @return Jira tickets.
     */
    public Tickets getTickets(@Deprecated String srvId, ICredentialsProv prov, String url);

    /** */
    public String jiraUrl();

    /** @return JIRA ticket prefix. */
    @NotNull public String ticketPrefix();

    /** */
    public void init(String srvId);

    /**
     * @param ticketFullName Ticket full name (e.g IGNITE-8331)
     * @return URL which is used as link to Jira ticket with specified name.
     */
    public String generateTicketUrl(String ticketFullName);

    /**
     * @param ticketFullName Ticket full name (e.g IGNITE-8331)
     * @return URL which is used as link to Jira comment with specified id.
     */
    public String generateCommentUrl(String ticketFullName, int commentId);

    String getJiraApiUrl();

    boolean isJiraTokenAvailable();
}
