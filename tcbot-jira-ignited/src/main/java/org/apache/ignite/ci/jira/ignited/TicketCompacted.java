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

import org.apache.ignite.ci.tcbot.common.StringFieldCompacted;
import org.apache.ignite.jiraservice.Fields;
import org.apache.ignite.jiraservice.Status;
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.Persisted;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 *
 */
@Persisted
public class TicketCompacted {
    /** Id. */
    public long id;

    /** Ticket number, integer value like 123 from name like "IGNITE-123". */
    public int igniteId;

    /**
     * Id of string: Fields/status/name, value compacted.
     * @deprecated {@link #statusCodeId} is used for storing status now,
     * field is kept to prevent accidental binary schema compatibility breaking in the future.
     */
    @Deprecated public int status;

    /** Summary, nullable because of older entries. */
    @Nullable private StringFieldCompacted summary = new StringFieldCompacted();

    /** Custom field, to be queriable after persisting in Ignite, nullable because of older entry versions. */
    @Nullable private StringFieldCompacted customfield_11050 = new StringFieldCompacted();

    /** Full description, nullable because of older entry versions. */
    @Nullable private StringFieldCompacted description = new StringFieldCompacted();

    /** Internal JIRA id of ticket status, see {@link org.apache.ignite.jiraservice.JiraTicketStatusCode}. */
    public int statusCodeId;

    /**
     * @param ticket Jira ticket.
     * @param comp Compactor.
     * @param projectCode project name for commenting jira.
     */
    public TicketCompacted(Ticket ticket, IStringCompactor comp, String projectCode) {
        id = ticket.id;
        igniteId = ticket.keyWithoutProject(projectCode);
        statusCodeId = ticket.fields.status.id;
        summary.setValue(ticket.fields.summary);
        customfield_11050.setValue(ticket.fields.customfield_11050);
        description.setValue(ticket.fields.description);
    }

    /**
     * @param comp Compactor.
     * @param projectCode project code for VISA and filtering tasks.
     */
    public Ticket toTicket(IStringCompactor comp, String projectCode) {
        Ticket ticket = new Ticket();

        ticket.id = id;
        ticket.key = projectCode + Ticket.PROJECT_DELIM + igniteId;
        ticket.fields = new Fields();
        ticket.fields.status = new Status(statusCodeId);
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
            statusCodeId == compacted.statusCodeId &&
            Objects.equals(summary, compacted.summary) &&
            Objects.equals(customfield_11050, compacted.customfield_11050) &&
            Objects.equals(description, compacted.description);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(id, igniteId, statusCodeId, summary, customfield_11050, description);
    }
}
