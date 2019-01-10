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

package org.apache.ignite.ci.jira;

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
     * Produce visa message(see {@link Visa}) based on passed
     * parameters and publish it as a comment for specified ticket
     * on Jira server.
     *
     * @param srvId TC Server ID to take information about token from.
     * @param prov Credentials.
     * @param buildTypeId Suite name.
     * @param branchForTc Branch for TeamCity.
     * @param ticket JIRA ticket full name. E.g. IGNITE-5555
     * @return {@link Visa} instance.
     */
    public Visa notifyJira(String srvId, ICredentialsProv prov, String buildTypeId, String branchForTc,
        String ticket);

    /**
     * Produce wrapper for collection of Jira tickets for given server.
     *
     * @param srvId Server id.
     * @param prov Prov.
     * @param ticketId Ticket id.
     * @return Jira tickets.
     */
    public Tickets getTickets(String srvId, ICredentialsProv prov, String ticketId);

    /** */
    public String jiraUrl();

    /** @return JIRA ticket prefix. */
    @NotNull public String ticketPrefix();

    /** */
    public void init(String srvId);

    /**
     * @param ticketFullName Ticket full name (e.g IGNITE-8331)
     *
     * @return URL which is used as link to Jira ticket with specified name.
     */
    public String generateTicketUrl(String ticketFullName);

    /**
     * @param ticketFullName Ticket full name (e.g IGNITE-8331)
     *
     * @return URL which is used as link to Jira comment with specified id.
     */
    public String generateCommentUrl(String ticketFullName, int commentId);
}
