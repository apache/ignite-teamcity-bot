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
package org.apache.ignite.ci.web.rest.exception;

import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.core.Response;
import org.apache.ignite.tcbot.common.conf.TcBotSystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs exception stacktraces.
 */
@Provider
public class ExeptionsTraceLogger implements ExceptionMapper<Throwable> {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(ExeptionsTraceLogger.class);

    /** {@inheritDoc} */
    @Override public Response toResponse(Throwable t) {
        logger.error("Error during processing request (Internal Server Error [500]). Caused by: ", t);

        if (Boolean.valueOf(System.getProperty(TcBotSystemProperties.DEV_MODE)))
            t.printStackTrace();

        return Response.serverError().entity(t.getMessage()).build();
    }
}