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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs slow REST requests with method, path and response status.
 */
@Provider
@Priority(Priorities.USER)
public class RestRequestTimingFilter implements ContainerRequestFilter, ContainerResponseFilter {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(RestRequestTimingFilter.class);

    /** Request context property with request start time. */
    private static final String START_TIME_NANOS = RestRequestTimingFilter.class.getName() + ".startTimeNanos";

    /** Slow REST request threshold. */
    private static final long SLOW_REQUEST_WARN_MS = Long.getLong("tcbot.rest.slowRequestWarnMs", 1000L);

    /** {@inheritDoc} */
    @Override public void filter(ContainerRequestContext reqCtx) throws IOException {
        reqCtx.setProperty(START_TIME_NANOS, System.nanoTime());
    }

    /** {@inheritDoc} */
    @Override public void filter(ContainerRequestContext reqCtx, ContainerResponseContext respCtx) throws IOException {
        Object startObj = reqCtx.getProperty(START_TIME_NANOS);

        if (!(startObj instanceof Long))
            return;

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - (Long)startObj);

        String rawQry = reqCtx.getUriInfo().getRequestUri().getRawQuery();
        String path = reqCtx.getUriInfo().getPath();
        String pathWithQuery = path;

        if (rawQry != null)
            pathWithQuery += "?" + rawQry;

        RestRequestTimingStorage.record(reqCtx.getMethod(), path, pathWithQuery, respCtx.getStatus(), elapsedMs);

        if (elapsedMs < SLOW_REQUEST_WARN_MS)
            return;

        logger.warn("Slow REST request: method={}, path={}, status={}, durationMs={}",
            reqCtx.getMethod(), pathWithQuery, respCtx.getStatus(), elapsedMs);
    }
}
