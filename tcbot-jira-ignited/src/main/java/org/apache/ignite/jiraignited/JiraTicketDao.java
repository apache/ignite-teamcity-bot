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

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.jira.ignited.TicketCompacted;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.GuavaCached;
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
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

    /** JIRA tickets cache : (srvId || ticketNuber) -> Ticket data compacted. */
    private IgniteCache<Long, TicketCompacted> jiraCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     *
     */
    public void init() {
        jiraCache = igniteProvider.get().getOrCreateCache(CacheConfigs.getCache8PartsConfig(TEAMCITY_JIRA_TICKET_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param projectCode project code. WIth delim gives Fixed prefix for JIRA tickets.
     * @return Jira tickets.
     */
    @GuavaCached(expireAfterWriteSecs = 60, softValues = true)
    public Set<Ticket> getTickets(int srvIdMaskHigh, String projectCode) {
        Preconditions.checkNotNull(jiraCache, "init() was not called");
        long srvId = (long)srvIdMaskHigh << 32;

        Set<Ticket> res = new HashSet<>();

        for (Cache.Entry<Long, TicketCompacted> entry : jiraCache) {
            if ((entry.getKey() & srvId) == srvId)
                res.add(entry.getValue().toTicket(compactor, projectCode));
        }

        return res;
    }

    /**
     * Combine server and project into key for storage.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param igniteId Ticket number without project name.
     * @return Key from server-project pair.
     */
    public static long ticketToCacheKey(int srvIdMaskHigh, int igniteId) {
        return (long)igniteId | (long)srvIdMaskHigh << 32;
    }

    /**
     * Save small part of loaded mutes.
     *
     * @param srvIdMaskHigh Server id mask high.
     * @param chunk Chunk.
     * @param projectCode Project code for contributions listing and for comments.
     * @return number of tickets totally saved.
     */
    @AutoProfiling
    public int saveChunk(int srvIdMaskHigh, Collection<Ticket> chunk, String projectCode) {
        Preconditions.checkNotNull(jiraCache, "init() was not called");

        if (F.isEmpty(chunk))
            return 0;

        Map<Long, TicketCompacted> compactedTickets = new HashMap<>(U.capacity(chunk.size()));

        for (Ticket ticket : chunk) {
            long key = ticketToCacheKey(srvIdMaskHigh, ticket.keyWithoutProject(projectCode));
            TicketCompacted val = new TicketCompacted(ticket, compactor, projectCode);

            compactedTickets.put(key, val);
        }

        Map<Long, TicketCompacted> dbVal = jiraCache.getAll(compactedTickets.keySet());

        Map<Long, TicketCompacted> ticketsToUpdate = new HashMap<>(U.capacity(chunk.size()));

        compactedTickets.forEach((k, v) -> {
            TicketCompacted existing = dbVal.get(k);

            if (existing == null || !v.equals(existing))
                ticketsToUpdate.put(k, v);
        });

        if (!ticketsToUpdate.isEmpty())
            jiraCache.putAll(ticketsToUpdate);

        return ticketsToUpdate.size();
    }
}
