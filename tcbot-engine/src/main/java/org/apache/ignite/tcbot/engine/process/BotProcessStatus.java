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

/**
 * User-visible bot process status.
 */
@SuppressWarnings({"WeakerAccess", "PublicField"})
public class BotProcessStatus {
    /** Process id supplied by UI. */
    public Long id;

    /** Process kind. */
    public String kind;

    /** Human-readable status published by the Java process. */
    public String status;

    /** Started timestamp. */
    public long started;

    /** Last update timestamp. */
    public long updated;

    /** Finished timestamp. */
    public long finished;

    /** Failure flag. */
    public boolean failed;

    /**
     * @param id Process id.
     */
    public BotProcessStatus(Long id) {
        this(id, "", "The bot has not reported this process yet.");
    }

    /**
     * @param id Process id.
     * @param kind Process kind.
     * @param status Status text.
     */
    BotProcessStatus(Long id, String kind, String status) {
        this.id = id;
        this.kind = kind;
        this.status = status;

        long now = System.currentTimeMillis();

        started = now;
        updated = now;
    }

    /**
     * Copy constructor.
     *
     * @param src Source.
     */
    BotProcessStatus(BotProcessStatus src) {
        id = src.id;
        kind = src.kind;
        status = src.status;
        started = src.started;
        updated = src.updated;
        finished = src.finished;
        failed = src.failed;
    }

    /**
     * @param status Status text.
     */
    void status(String status) {
        this.status = status;
        updated = System.currentTimeMillis();
    }

    /** @return Running flag. */
    public boolean isRunning() {
        return finished == 0 && id != null;
    }
}
