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

import java.util.logging.Handler;
import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class CtxListener implements ServletContextListener {
    /** TC Bot application context servlet attribute. */
    private static final String APPLICATION_CONTEXT = "tcBotApplicationContext";

    @Nullable private static volatile Logger logger;

    public static TcBotApplicationContext getApplicationContext(ServletContext ctx) {
        return (TcBotApplicationContext)ctx.getAttribute(APPLICATION_CONTEXT);
    }

    /** {@inheritDoc} */
    @Override public void contextInitialized(ServletContextEvent sctxEvt) {
        initLoggerBridge();

        TcBotApplicationContext appCtx = TcBotApplicationContexts.create();
        appCtx.start();

        sctxEvt.getServletContext().setAttribute(APPLICATION_CONTEXT, appCtx);
    }

    /**
     * initializes logger bridgle for jul->Slf4j redirection for Jersey.
     */
    private void initLoggerBridge() {
        java.util.logging.Logger rootLog = java.util.logging.LogManager.getLogManager().getLogger("");
        java.util.logging.Handler[] handlers = rootLog.getHandlers();

        for (Handler handler : handlers)
            rootLog.removeHandler(handler);

        org.slf4j.bridge.SLF4JBridgeHandler.install();

        logger = LoggerFactory.getLogger(CtxListener.class);
    }

    /** {@inheritDoc} */
    @Override public void contextDestroyed(ServletContextEvent sctxEvt) {
        try {
            TcBotApplicationContext appCtx = getApplicationContext(sctxEvt.getServletContext());

            if (appCtx != null)
                appCtx.close();
        }
        catch (Exception e) {
            e.printStackTrace();

            if (logger != null)
                logger.error("Exception during TC Bot application context close: " + e.getMessage(), e);
        }
    }
}
