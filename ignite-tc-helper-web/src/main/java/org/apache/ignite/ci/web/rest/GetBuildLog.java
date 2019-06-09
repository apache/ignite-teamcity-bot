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

package org.apache.ignite.ci.web.rest;

import com.google.inject.Injector;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.tcbot.engine.ui.BotUrls;
import org.apache.ignite.tcignited.buildlog.IBuildLogProcessor;

import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Build log download, now provides thread dumps
 */
@Path(BotUrls.GetBuildLog.GET_BUILD_LOG)
@Produces(MediaType.TEXT_PLAIN)
public class GetBuildLog {

    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path(BotUrls.GetBuildLog.THREAD_DUMP)
    @PermitAll
    public Response getThreadDump(
        @QueryParam(BotUrls.GetBuildLog.SERVER_ID) String srvCode,
        @QueryParam(BotUrls.GetBuildLog.BUILD_NO) Integer buildId) {
        Injector injector = CtxListener.getInjector(ctx);

        IBuildLogProcessor instance = injector.getInstance(IBuildLogProcessor.class);

        String cached = instance.getThreadDumpCached(srvCode, buildId);

        if (cached == null)
            return sendString("No data found for [" + srvCode + ", " + buildId + "]");

        return sendString(cached);
    }

    private Response sendString(String data) {
        final StreamingOutput stream = os -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(os));
            writer.write(data);
            writer.flush();
        };
        return Response.ok(stream).build();
    }

}
