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

package org.apache.ignite.tcbot.engine.process;

import com.google.common.base.Strings;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Lightweight in-memory monitor for user-visible bot processes.
 */
@Singleton
public class BotProcessMonitor {
    /** Max statuses kept in memory. */
    private static final int MAX_STATUSES = 500;

    /** Statuses by process id. */
    private final ConcurrentMap<Long, BotProcessStatus> statuses = new ConcurrentHashMap<>();

    /**
     * @param id Process id.
     * @param kind Process kind.
     * @param status Initial status.
     */
    public void start(@Nullable Long id, String kind, String status) {
        if (id == null)
            return;

        statuses.put(id, new BotProcessStatus(id, Strings.nullToEmpty(kind), Strings.nullToEmpty(status)));

        trim();
    }

    /**
     * @param id Process id.
     * @param status Status.
     */
    public void status(@Nullable Long id, String statusText) {
        BotProcessStatus status = find(id);

        if (status == null)
            return;

        synchronized (status) {
            status.status(Strings.nullToEmpty(statusText));
        }
    }

    /**
     * @param id Process id.
     * @param result Result.
     */
    public void finish(@Nullable Long id, @Nullable String result) {
        BotProcessStatus status = find(id);

        if (status == null)
            return;

        synchronized (status) {
            status.status(Strings.nullToEmpty(result));
            status.finished = System.currentTimeMillis();
            status.failed = false;
        }
    }

    /**
     * @param id Process id.
     * @param e Error.
     */
    public void fail(@Nullable Long id, Throwable e) {
        fail(id, e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    /**
     * @param id Process id.
     * @param error Error.
     */
    public void fail(@Nullable Long id, @Nullable String error) {
        BotProcessStatus status = find(id);

        if (status == null)
            return;

        synchronized (status) {
            status.status("Failed: " + Strings.nullToEmpty(error));
            status.finished = System.currentTimeMillis();
            status.failed = true;
        }
    }

    /**
     * @param id Process id.
     * @return Status snapshot.
     */
    public BotProcessStatus status(@Nullable Long id) {
        BotProcessStatus status = find(id);

        return status == null ? new BotProcessStatus(id) : new BotProcessStatus(status);
    }

    /**
     * @param id Process id.
     */
    @Nullable private BotProcessStatus find(@Nullable Long id) {
        return id == null ? null : statuses.get(id);
    }

    /** */
    private void trim() {
        if (statuses.size() <= MAX_STATUSES)
            return;

        statuses.values().stream()
            .sorted(Comparator.comparingLong(status -> status.updated))
            .limit(statuses.size() - MAX_STATUSES)
            .map(status -> status.id)
            .forEach(statuses::remove);
    }
}
