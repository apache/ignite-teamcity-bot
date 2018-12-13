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

import org.apache.ignite.ci.jira.Fields;
import org.apache.ignite.ci.jira.Status;
import org.apache.ignite.ci.jira.Ticket;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;

/**
 *
 */
public class TicketCompacted {
    /** Id. */
    public long id;

    /** Ticket full name like "IGNITE-123". */
    public int igniteId;

    /** Fields. */
    public int status;

    /**
     * @param ticket Jira ticket.
     * @param comp Compactor.
     */
    public TicketCompacted(Ticket ticket, IStringCompactor comp) {
        id = ticket.id;
        igniteId = Integer.valueOf(ticket.key.substring("IGNITE-".length()));
        status = comp.getStringId(ticket.fields.status.name);
    }

    /**
     * @param comp Compactor.
     */
    public Ticket toTicket(IStringCompactor comp) {
        Ticket ticket = new Ticket();

        ticket.id = id;
        ticket.key = "IGNITE-" + igniteId;
        ticket.fields = new Fields();
        ticket.fields.status = new Status(comp.getStringFromId(status));

        return ticket;
    }
}
