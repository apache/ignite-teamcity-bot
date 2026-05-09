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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.xml.bind.JAXBException;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.core.Response;
import org.apache.ignite.tcbot.common.conf.TcBotSystemProperties;
import org.apache.ignite.tcbot.common.exeption.ServicesStartingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs exception stacktraces.
 */
@Provider
public class ExeptionsTraceLogger implements ExceptionMapper<Throwable> {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(ExeptionsTraceLogger.class);

    /** Max causes to include in HTTP response. */
    private static final int MAX_CAUSE_DEPTH = 8;

    /** {@inheritDoc} */
    @Override public Response toResponse(Throwable t) {
        ServicesStartingException starting = findCause(t, ServicesStartingException.class);

        if (starting != null) {
            logger.info("Service is still starting: " + starting.getMessage(), t);

            return Response.status(418).entity(starting.getMessage()).type("text/plain").build();
        }

        logger.error("Error during processing request (Internal Server Error [500]). Caused by: ", t);

        if (Boolean.valueOf(System.getProperty(TcBotSystemProperties.DEV_MODE)))
            t.printStackTrace();

        return Response.serverError().entity(errorMessage(t)).build();
    }

    /**
     * @param t Exception.
     * @return Short user-facing error text with the useful root cause preserved.
     */
    private static String errorMessage(Throwable t) {
        StringBuilder res = new StringBuilder("Internal Server Error [500].");
        Throwable root = rootCause(t);
        String rootMsg = formatCause(root);

        if (!rootMsg.isEmpty())
            res.append("\nReason: ").append(rootMsg);

        res.append("\n\nCause chain:");

        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cur = t;
        int depth = 0;

        while (cur != null && seen.add(cur) && depth < MAX_CAUSE_DEPTH) {
            res.append("\n- ").append(formatCause(cur));

            cur = nextCause(cur);
            depth++;
        }

        if (cur != null)
            res.append("\n- ...");

        return res.toString();
    }

    /**
     * @param t Exception.
     * @return Root cause, including JAXB linked exceptions.
     */
    private static Throwable rootCause(Throwable t) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cur = t;
        Throwable next;

        while (cur != null && seen.add(cur) && (next = nextCause(cur)) != null)
            cur = next;

        return cur == null ? t : cur;
    }

    /**
     * @param t Exception.
     * @return Next cause.
     */
    private static Throwable nextCause(Throwable t) {
        Throwable cause = t.getCause();

        if (cause != null)
            return cause;

        if (t instanceof JAXBException)
            return ((JAXBException)t).getLinkedException();

        return null;
    }

    /**
     * @param t Exception.
     * @return Cause text.
     */
    private static String formatCause(Throwable t) {
        if (t == null)
            return "";

        String msg = t.getMessage();
        String cls = t.getClass().getSimpleName();

        if (msg == null || msg.trim().isEmpty())
            return cls;

        return cls + ": " + msg.replace('\r', ' ').replace('\n', ' ').trim();
    }

    /**
     * @param t Exception.
     * @param type Cause type.
     */
    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cur = t;

        while (cur != null && seen.add(cur)) {
            if (type.isInstance(cur))
                return type.cast(cur);

            cur = nextCause(cur);
        }

        return null;
    }
}
