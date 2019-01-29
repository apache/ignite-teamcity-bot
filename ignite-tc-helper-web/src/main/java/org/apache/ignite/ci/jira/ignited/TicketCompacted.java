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

import com.google.common.base.Objects;
import org.apache.ignite.ci.jira.pure.Fields;
import org.apache.ignite.ci.jira.pure.Status;
import org.apache.ignite.ci.jira.pure.Ticket;
import org.apache.ignite.ci.tcbot.common.StringFieldCompacted;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class TicketCompacted {
    /** Id. */
    public long id;

    /** Ticket number, integer value like 123 from name like "IGNITE-123". */
    public int igniteId;

    /** Id of string: Fields/status/name, value compacted. */
    public int status;

    /** Summary, nullable because of older entries. */
    @Nullable private StringFieldCompacted summary = new StringFieldCompacted();

    /** Custom field, to be queriable after persisting in Ignite, nullable because of older entry versions.  */
    @Nullable private StringFieldCompacted customfield_11050 = new StringFieldCompacted();

    /** Full description, nullable because of older entry versions.  */
    @Nullable private StringFieldCompacted description = new StringFieldCompacted();

    /**
     * @param ticket Jira ticket.
     * @param comp Compactor.
     * @param ticketForCommentPrefix Ticket name prefix.
     */
    public TicketCompacted(Ticket ticket, IStringCompactor comp, String ticketForCommentPrefix) {
        id = ticket.id;
        igniteId = Integer.valueOf(ticket.key.substring(ticketForCommentPrefix.length()));
        status = comp.getStringId(ticket.fields.status.name);
        summary.setValue(ticket.fields.summary);
        customfield_11050.setValue(ticket.fields.customfield_11050);
        description.setValue(ticket.fields.description);
    }

    /**
     * @param comp Compactor.
     * @param ticketPrefix Ticket name fixed prefix for the project.
     */
    public Ticket toTicket(IStringCompactor comp, String ticketPrefix) {
        Ticket ticket = new Ticket();

        ticket.id = id;
        ticket.key = ticketPrefix + igniteId;
        ticket.fields = new Fields();
        ticket.fields.status = new Status(comp.getStringFromId(status));
        ticket.fields.summary = summary != null ? summary.getValue() : null;
        ticket.fields.customfield_11050 = customfield_11050 != null ? customfield_11050.getValue() : null;
        ticket.fields.description = description != null ? description.getValue() : null;

        return ticket;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TicketCompacted compacted = (TicketCompacted)o;
        return id == compacted.id &&
            igniteId == compacted.igniteId &&
            status == compacted.status &&
            Objects.equal(summary, compacted.summary) &&
            Objects.equal(customfield_11050, compacted.customfield_11050) &&
            Objects.equal(description, compacted.description);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hashCode(id, igniteId, status, summary, customfield_11050, description);
    }
}
