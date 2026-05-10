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

package org.apache.ignite.ci.web.rest.testfixes;

import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.auth.AuthenticationFilter;
import org.apache.ignite.tcbot.engine.testfixes.TestFixRefUi;
import org.apache.ignite.tcbot.engine.testfixes.TestFixesService;

/**
 * Test fix history REST service.
 */
@Path("testFixes")
@Produces(MediaType.APPLICATION_JSON)
public class TestFixesRestService {
    /** Servlet Context. */
    @Context private ServletContext ctx;

    /**
     * @param limit Max rows.
     */
    @GET
    @Path("recent")
    public List<TestFixRefUi> recent(@QueryParam("limit") Integer limit) {
        int actualLimit = limit == null ? 0 : limit;

        return CtxListener.getApplicationContext(ctx).getInstance(TestFixesService.class).recent(actualLimit);
    }

    /**
     * Schedules an immediate refresh and returns currently cached rows.
     *
     * @param limit Max rows.
     * @param processId Optional user-visible process id.
     */
    @GET
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("refresh")
    public List<TestFixRefUi> refresh(@QueryParam("limit") Integer limit, @QueryParam("processId") Long processId) {
        TestFixesService svc = CtxListener.getApplicationContext(ctx).getInstance(TestFixesService.class);

        svc.requestMatchNow(processId);

        return recent(limit);
    }
}
