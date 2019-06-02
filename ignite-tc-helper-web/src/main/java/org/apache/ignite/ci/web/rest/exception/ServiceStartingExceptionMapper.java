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

import org.apache.ignite.tcbot.common.exeption.ServicesStartingException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Providing response code 418- I'm a teapot for starting server.
 */
@Provider
public class ServiceStartingExceptionMapper implements ExceptionMapper<ServicesStartingException> {
    public ServiceStartingExceptionMapper() {
    }

    @Override
    public Response toResponse(ServicesStartingException exception) {
        return Response.status(418).entity(exception.getMessage())
                .type("text/plain").build();
    }
}
