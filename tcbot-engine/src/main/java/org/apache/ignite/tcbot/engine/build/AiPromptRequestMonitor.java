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

package org.apache.ignite.tcbot.engine.build;

import com.google.common.base.Strings;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import org.apache.ignite.tcbot.common.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight request monitor for AI prompt generation.
 */
@Singleton
public class AiPromptRequestMonitor {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(AiPromptRequestMonitor.class);

    /** Request sequence. */
    private final AtomicLong seq = new AtomicLong();

    /** Requests. */
    private final ConcurrentMap<Long, Request> requests = new ConcurrentHashMap<>();

    /**
     * @param kind Prompt kind.
     * @param branch Branch.
     * @param server Server.
     * @param suite Suite.
     * @param test Test.
     */
    public long start(String kind, @Nullable String branch, @Nullable String server, @Nullable String suite,
        @Nullable String test) {
        long id = seq.incrementAndGet();
        Request req = new Request(id, kind, branch, server, suite, test);

        requests.put(id, req);

        logger.info("AI prompt request started: {}", req);

        trim();

        return id;
    }

    /**
     * @param id Request id.
     * @param stage Stage.
     */
    public void stage(long id, String stage) {
        Request req = requests.get(id);

        if (req == null)
            return;

        req.stage = stage;

        logger.info("AI prompt request stage: id={}, stage={}", id, stage);
    }

    /**
     * @param id Request id.
     * @param result Result.
     */
    public void finish(long id, String result) {
        Request req = requests.get(id);

        if (req == null)
            return;

        req.finished = System.currentTimeMillis();
        req.stage = "finished";
        req.result = result;

        logger.info("AI prompt request finished: {}", req);
    }

    /**
     * @param id Request id.
     * @param e Error.
     */
    public void fail(long id, Throwable e) {
        Request req = requests.get(id);

        if (req == null)
            return;

        req.finished = System.currentTimeMillis();
        req.stage = "failed";
        req.result = e.getClass().getSimpleName() + ": " + e.getMessage();

        logger.warn("AI prompt request failed: {}", req, e);
    }

    /**
     * @return Snapshot.
     */
    public List<Request> getRequests() {
        return requests.values().stream()
            .sorted(Comparator.comparingLong(Request::id).reversed())
            .collect(Collectors.toList());
    }

    /** */
    private void trim() {
        if (requests.size() <= 200)
            return;

        requests.values().stream()
            .sorted(Comparator.comparingLong(Request::id))
            .limit(requests.size() - 200)
            .map(Request::id)
            .forEach(requests::remove);
    }

    /** Request record. */
    @SuppressWarnings({"WeakerAccess", "PublicField"})
    public static class Request {
        /** Id. */
        public final long id;

        /** Kind. */
        public final String kind;

        /** Branch. */
        public final String branch;

        /** Server. */
        public final String server;

        /** Suite. */
        public final String suite;

        /** Test. */
        public final String test;

        /** Started timestamp. */
        public final long started;

        /** Finished timestamp. */
        public volatile long finished;

        /** Stage. */
        public volatile String stage = "starting";

        /** Result. */
        public volatile String result = "";

        /**
         * @param id Id.
         * @param kind Kind.
         * @param branch Branch.
         * @param server Server.
         * @param suite Suite.
         * @param test Test.
         */
        private Request(long id, String kind, @Nullable String branch, @Nullable String server,
            @Nullable String suite, @Nullable String test) {
            this.id = id;
            this.kind = kind;
            this.branch = Strings.nullToEmpty(branch);
            this.server = Strings.nullToEmpty(server);
            this.suite = Strings.nullToEmpty(suite);
            this.test = Strings.nullToEmpty(test);
            started = System.currentTimeMillis();
        }

        /** @return Id. */
        public long id() {
            return id;
        }

        /** @return Started printable. */
        public String getStart() {
            return TimeUtil.timestampToDateTimePrintable(started);
        }

        /** @return End printable. */
        public String getEnd() {
            return TimeUtil.timestampToDateTimePrintable(finished);
        }

        /** @return Duration printable. */
        public String getDuration() {
            long end = finished == 0 ? System.currentTimeMillis() : finished;

            return TimeUtil.millisToDurationPrintable(end - started);
        }

        /** @return Running. */
        public boolean isRunning() {
            return finished == 0;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "Request{" +
                "id=" + id +
                ", kind='" + kind + '\'' +
                ", branch='" + branch + '\'' +
                ", server='" + server + '\'' +
                ", suite='" + suite + '\'' +
                ", test='" + test + '\'' +
                ", stage='" + stage + '\'' +
                ", started='" + getStart() + '\'' +
                ", duration='" + getDuration() + '\'' +
                ", result='" + result + '\'' +
                '}';
        }
    }
}
