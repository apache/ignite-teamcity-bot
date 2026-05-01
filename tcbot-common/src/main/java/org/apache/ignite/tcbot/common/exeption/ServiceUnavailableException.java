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

package org.apache.ignite.tcbot.common.exeption;

import javax.annotation.Nullable;

/**
 * The service is temporarily unable to handle the request.
 *
 * This exception is thrown in case HTTP 503-Service Unavailable is returned.
 */
public class ServiceUnavailableException extends RuntimeException {
    /** Service URL. */
    private final String url;

    /** HTTP status code. */
    private final int responseCode;

    /** Retry-After delay in milliseconds, or {@code -1} if it is absent or cannot be parsed. */
    private final long retryAfterMs;

    /**
     * @param msg Message.
     * @param url Service URL.
     * @param responseCode HTTP status code.
     * @param retryAfterMs Retry-After delay in milliseconds, or {@code -1}.
     */
    public ServiceUnavailableException(String msg, String url, int responseCode, long retryAfterMs) {
        this(msg, url, responseCode, retryAfterMs, null);
    }

    /**
     * @param msg Message.
     * @param url Service URL.
     * @param responseCode HTTP status code.
     * @param retryAfterMs Retry-After delay in milliseconds, or {@code -1}.
     * @param cause Cause.
     */
    public ServiceUnavailableException(String msg, String url, int responseCode, long retryAfterMs,
        @Nullable Throwable cause) {
        super(msg, cause);

        this.url = url;
        this.responseCode = responseCode;
        this.retryAfterMs = retryAfterMs;
    }

    /**
     * @return Service URL.
     */
    public String url() {
        return url;
    }

    /**
     * @return HTTP status code.
     */
    public int responseCode() {
        return responseCode;
    }

    /**
     * @return Retry-After delay in milliseconds, or {@code -1}.
     */
    public long retryAfterMs() {
        return retryAfterMs;
    }
}
