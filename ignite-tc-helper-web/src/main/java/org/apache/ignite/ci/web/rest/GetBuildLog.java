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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import org.apache.ignite.ci.ITcAnalytics;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.web.CtxListener;

/**
 * Build log download, now provides thread dumps
 */
@Path(GetBuildLog.GET_BUILD_LOG)
@Produces(MediaType.TEXT_PLAIN)
public class GetBuildLog {
    public static final String GET_BUILD_LOG = "getBuildLog";
    public static final String THREAD_DUMP = "threadDump";
    public static final String SERVER_ID = "serverId";
    public static final String BUILD_NO = "buildNo";
    public static final String FILE_IDX = "fileIdx";

    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path(THREAD_DUMP)
    @PermitAll
    public Response getThreadDump(
        @QueryParam(SERVER_ID) String srvId,
        @QueryParam(BUILD_NO) Integer buildNo,
        @Deprecated @QueryParam(FILE_IDX) Integer fileIdx) {

        ITcHelper helper = CtxListener.getTcHelper(ctx);
        ITcAnalytics srv = helper.tcAnalytics(srvId);
        String cached = srv.getThreadDumpCached(buildNo);

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

    private Response sendFile(File file) {
        final StreamingOutput stream = os -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(os));

            try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)){
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.write("\n");
                }
                writer.flush();
            }
        };
        return Response.ok(stream).build();
    }

}
