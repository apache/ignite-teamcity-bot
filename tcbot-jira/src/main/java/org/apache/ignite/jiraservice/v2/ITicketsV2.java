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

package org.apache.ignite.jiraservice.v2;

import java.util.Collection;
import java.util.Collections;
import org.apache.ignite.jiraservice.ITickets;
import org.apache.ignite.jiraservice.Ticket;

/**
 * See example of GSON here https://issues.apache.org/jira/rest/api/2/search?jql=project%20=%20IGNITE%20order%20by%20updated%20DESC&fields=status
 */
public class ITicketsV2 implements ITickets {
    /** Start at. */
    private int startAt;

    /** Max amount of tickets on the page. */
    private int maxResults;

    /** Total tickets. */
    private int total;

    /** Jira tickets. */
    private Collection<Ticket> issues;

    /** {@inheritDoc} */
    @Override public boolean hasNextPage() {
        int next = nextStart();
        return next != -1;
    }

    /** {@inheritDoc} */
    @Override public String nextPagePosition() {
        return "&startAt=" + nextStart();
    }

    /** {@inheritDoc} */
    @Override public Collection<Ticket> issues() {
        return issues == null ? Collections.emptyList() : issues;
    }

    /**
     * @return Start index for next page. Return -1 if it is last page.
     */
    private int nextStart() {
        int next = startAt + maxResults;

        if (next < total)
            return next;

        return -1;
    }
}
