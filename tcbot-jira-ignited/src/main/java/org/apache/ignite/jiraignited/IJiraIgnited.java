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
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;

import javax.annotation.Nullable;

/**
 *
 */
public interface IJiraIgnited {
    /**
     * @return Jira tickets.
     */
    public Set<Ticket> getTickets();

    /**
     * @param ticketFullName Ticket full name (e.g IGNITE-8331)
     * @return URL which is used as link to Jira comment with specified id.
     */
    public String generateCommentUrl(String ticketFullName, int commentId);

    /**
     * @param id Ticket full ID (e.g IGNITE-8331)
     * @return URL which is used as link to Jira ticket with specified name.
     */
    public String generateTicketUrl(String id);

    /**
     * @param ticket JIRA ticket full name. E.g 'IGNITE-5555'.
     * @param comment Comment to be placed in the ticket conversation.
     * @return {@code True} if ticket was succesfully commented. Otherwise - {@code false}.
     * @throws IOException If failed to comment JIRA ticket.
     * @throws IllegalStateException If can't find URL to the JIRA.
     */
    public String postJiraComment(String ticket, String comment) throws IOException;

    public IJiraServerConfig config();

    /**
     * @param srvId Server id.
     * @return integer representation of server ID.
     */
    public static int serverIdToInt(@Nullable final String srvId) {
        if (srvId == null)
            return 0;

        return Math.abs(srvId.hashCode());
    }
}
