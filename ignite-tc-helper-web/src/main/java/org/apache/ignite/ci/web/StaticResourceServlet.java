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
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
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

    private static String resourcePath(HttpServletRequest req) {
        String ctx = req.getContextPath();
        String uri = req.getRequestURI();

        String path = uri;

        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx))
            path = uri.substring(ctx.length());

        if (path.startsWith("/"))
            path = path.substring(1);

        path = URLDecoder.decode(path, StandardCharsets.UTF_8);

        if (path.contains("..") || path.contains("\\") || path.startsWith("/"))
            return path;

        String normalized = URI.create("/" + path).normalize().getPath();

        if (normalized.startsWith("/"))
            normalized = normalized.substring(1);

        return normalized;
    }

    /** {@inheritDoc} */
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
        String path;

        try {
            path = resourcePath(req);
        }
        catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (path.isEmpty())
            path = "index.html";

        if (path.contains("..") || path.contains("\\") || path.startsWith("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (path.endsWith("/"))
            path += "index.html";

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
