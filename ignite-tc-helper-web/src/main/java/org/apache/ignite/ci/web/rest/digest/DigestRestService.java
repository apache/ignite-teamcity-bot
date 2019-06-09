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
package org.apache.ignite.ci.web.rest.digest;

import com.google.inject.Injector;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.tcbot.engine.digest.DigestService;
import org.apache.ignite.tcignited.creds.ICredentialsProv;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("digest")
@Produces(MediaType.APPLICATION_JSON)
public class DigestRestService {

    /** */
    @Context
    private ServletContext ctx;

    /** */
    @Context
    private HttpServletRequest req;

    /** */
    @GET
    @Path("html")
    @Produces(MediaType.TEXT_HTML)
    public String generateDigest(@QueryParam("branch") String trBrName) {
        ICredentialsProv creds = ITcBotUserCreds.get(req);
        Injector injector = CtxListener.getInjector(ctx);
        DigestService digestService = injector.getInstance(DigestService.class);

        return digestService.generate(trBrName, creds).toHtml();
    }
}
