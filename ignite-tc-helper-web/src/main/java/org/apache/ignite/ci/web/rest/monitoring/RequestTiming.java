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
package org.apache.ignite.ci.web.rest.monitoring;

/**
 * Single REST request timing.
 */
@SuppressWarnings("WeakerAccess")
public class RequestTiming {
    public String method;
    public String path;
    public int status;
    public long durationMs;
    public long timestamp;

    /**
     * Default constructor.
     */
    public RequestTiming() {
    }

    /**
     * @param method HTTP method.
     * @param path Request path with query.
     * @param status Response status.
     * @param durationMs Request duration in millis.
     * @param timestamp Completion timestamp.
     */
    public RequestTiming(String method, String path, int status, long durationMs, long timestamp) {
        this.method = method;
        this.path = path;
        this.status = status;
        this.durationMs = durationMs;
        this.timestamp = timestamp;
    }
}
