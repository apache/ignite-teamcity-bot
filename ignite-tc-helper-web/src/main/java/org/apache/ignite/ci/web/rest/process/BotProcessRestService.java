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

package org.apache.ignite.ci.web.rest.process;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
import org.apache.ignite.tcbot.engine.process.BotProcessStatus;
import org.jetbrains.annotations.Nullable;

/**
 * REST access to user-visible bot process statuses.
 */
@Path("process")
@Produces(MediaType.APPLICATION_JSON)
public class BotProcessRestService {
    /** Servlet context. */
    @Context private ServletContext ctx;

    /**
     * @param id Process id supplied by UI to the long-running endpoint.
     * @return Current process status.
     */
    @GET
    @Path("status")
    public BotProcessStatus status(@Nullable @QueryParam("id") Long id) {
        return monitor().status(id);
    }

    /** */
    private BotProcessMonitor monitor() {
        return CtxListener.getApplicationContext(ctx).getInstance(BotProcessMonitor.class);
    }
}
