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

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.jira.Ticket;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;

/**
 *
 */
public class JiraTicketDao {
    /** Cache name. */
    public static final String TEAMCITY_JIRA_TICKET_CACHE_NAME = "jiraTickets";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, TicketCompacted> jiraCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     *
     */
    public void init() {
        jiraCache = igniteProvider.get().getOrCreateCache(TcHelperDb.getCache8PartsConfig(TEAMCITY_JIRA_TICKET_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @return Jira tickets.
     */
    public Set<Ticket> getTickets(int srvIdMaskHigh) {
        Preconditions.checkNotNull(jiraCache, "init() was not called");
        long srvId = (long) srvIdMaskHigh << 32;

        Set<Ticket> res = new HashSet<>();

        for (Cache.Entry<Long, TicketCompacted> entry : jiraCache) {
            if ((entry.getKey() & srvId) == srvId)
                res.add(entry.getValue().toTicket(compactor));
        }

        return res;
    }

    /**
     * Combine server and project into key for storage.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param igniteId Ticket.
     * @return Key from server-project pair.
     */
    public static long ticketToCacheKey(int srvIdMaskHigh, int igniteId) {
        return (long) igniteId | (long) srvIdMaskHigh << 32;
    }

    /**
     * Save small part of loaded mutes.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param chunk Chunk.
     * @param ticketPrefix Ticket name template.
     */
    @AutoProfiling
    public void saveChunk(int srvIdMaskHigh, Collection<Ticket> chunk, String ticketPrefix) {
        Preconditions.checkNotNull(jiraCache, "init() was not called");

        if (F.isEmpty(chunk))
            return;

        HashMap<Long, TicketCompacted> compactedTickets = new HashMap<>(U.capacity(chunk.size()));

        for (Ticket ticket : chunk) {
            long key = ticketToCacheKey(srvIdMaskHigh, ticket.igniteId(ticketPrefix));
            TicketCompacted val = new TicketCompacted(ticket, compactor, ticketPrefix);

            compactedTickets.put(key, val);
        }

        jiraCache.putAll(compactedTickets);
    }
}
