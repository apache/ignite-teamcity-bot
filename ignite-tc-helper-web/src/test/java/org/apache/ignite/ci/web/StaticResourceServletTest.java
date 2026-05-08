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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StaticResourceServletTest {
    @Test
    public void servesRootFromWebappResourceBase() throws Exception {
        ServletContext ctx = mock(ServletContext.class);
        byte[] html = "<html>ok</html>".getBytes(StandardCharsets.UTF_8);

        when(ctx.getResourceAsStream("/index.html")).thenReturn(new ByteArrayInputStream(html));
        when(ctx.getMimeType("index.html")).thenReturn("text/html");

        TestStaticResourceServlet servlet = new TestStaticResourceServlet(ctx);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContextPath()).thenReturn("");
        when(req.getRequestURI()).thenReturn("/");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getOutputStream()).thenReturn(new ByteArrayServletOutputStream(body));

        servlet.get(req, resp);

        assertEquals("<html>ok</html>", body.toString(StandardCharsets.UTF_8.name()));

        verify(resp).setContentType("text/html");
        verify(resp, never()).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private static class TestStaticResourceServlet extends StaticResourceServlet {
        /** Context. */
        private final ServletContext ctx;

        /**
         * @param ctx Context.
         */
        private TestStaticResourceServlet(ServletContext ctx) {
            this.ctx = ctx;
        }

        /** {@inheritDoc} */
        @Override public ServletContext getServletContext() {
            return ctx;
        }

        /**
         * @param req Request.
         * @param resp Response.
         */
        private void get(HttpServletRequest req, HttpServletResponse resp) throws Exception {
            doGet(req, resp);
        }
    }

    private static class ByteArrayServletOutputStream extends ServletOutputStream {
        /** Delegate. */
        private final ByteArrayOutputStream delegate;

        /**
         * @param delegate Delegate.
         */
        private ByteArrayServletOutputStream(ByteArrayOutputStream delegate) {
            this.delegate = delegate;
        }

        /** {@inheritDoc} */
        @Override public boolean isReady() {
            return true;
        }

        /** {@inheritDoc} */
        @Override public void setWriteListener(WriteListener listener) {
            // No async IO in this test.
        }

        /** {@inheritDoc} */
        @Override public void write(int b) throws IOException {
            delegate.write(b);
        }
    }
}
