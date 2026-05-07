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

package org.apache.ignite.ci.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Serves static UI resources from the webapp classpath.
 */
public class StaticResourceServlet extends HttpServlet {
    /** Static resources classpath root. */
    private static final String STATIC_ROOT = "static/";

    /** {@inheritDoc} */
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
        String path = req.getPathInfo();

        if (path == null || path.equals("/") || path.isEmpty())
            path = "index.html";
        else
            path = path.substring(1);

        if (path.endsWith("/"))
            path += "index.html";

        if (path.contains("..") || path.startsWith("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String resPath = STATIC_ROOT + path;

        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resPath)) {
            if (in == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String mimeType = getServletContext().getMimeType(path);

            if (mimeType == null)
                mimeType = URLConnection.guessContentTypeFromName(path);

            if (mimeType != null)
                resp.setContentType(mimeType);

            in.transferTo(resp.getOutputStream());
        }
    }
}
