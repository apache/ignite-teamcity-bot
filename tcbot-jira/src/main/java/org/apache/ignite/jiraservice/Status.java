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

package org.apache.ignite.jiraservice;

import java.io.ObjectStreamException;

/**
 * Status for Jira ticket.
 */
public class Status {
    /** Status text (open, resolved, etc). */
    public String name;

    /** Internal JIRA status ID. */
    public int id;

    /** Resolved well-known status code. */
    public JiraTicketStatusCode statusCode;

    /**
     * @param id JIRA status ID.
     */
    public Status(int id) {
        this.id = id;

        try {
            readResolve();
        } catch (ObjectStreamException e) {
            // Should be never thrown.
        }
    }

    /**
     * Reconstructs object on unmarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of unmarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        statusCode = JiraTicketStatusCode.fromId(id);
        name = JiraTicketStatusCode.text(statusCode);

        return this;
    }
}