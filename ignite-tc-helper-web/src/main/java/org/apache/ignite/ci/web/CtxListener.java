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

import java.util.concurrent.ExecutorService;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.TcHelper;
import org.apache.ignite.ci.db.TcHelperDb;

/**
 */
public class CtxListener implements ServletContextListener {
    private static final String TC_HELPER = "tcHelper";

    public static final String IGNITE = "ignite";

    public static final String UPDATER = "updater";


    private static final String POOL = "pool";

    public static Ignite getIgnite(ServletContext ctx) {
        return (Ignite)ctx.getAttribute(IGNITE);
    }

    public static ITcHelper getTcHelper(ServletContext ctx) {
        return (ITcHelper)ctx.getAttribute(TC_HELPER);
    }

    public static BackgroundUpdater getBackgroundUpdater(ServletContext ctx) {
        return (BackgroundUpdater)ctx.getAttribute(UPDATER);
    }

    @Override public void contextInitialized(ServletContextEvent sctxEvt) {
        final Ignite ignite = TcHelperDb.start();
        final ServletContext ctx = sctxEvt.getServletContext();
        ctx.setAttribute(IGNITE, ignite);

        TcHelper tcHelper = new TcHelper(ignite);

        BackgroundUpdater backgroundUpdater = new BackgroundUpdater(tcHelper);

        ctx.setAttribute(UPDATER, backgroundUpdater);

        ctx.setAttribute(TC_HELPER, tcHelper);
        ctx.setAttribute(POOL, tcHelper.getService());
    }

    public static ExecutorService getPool(ServletContext context) {
        return (ExecutorService)context.getAttribute(POOL);
    }

    @Override public void contextDestroyed(ServletContextEvent sctxEvt) {
        final ServletContext ctx = sctxEvt.getServletContext();

        TcHelperDb.stop(getIgnite(ctx));

        getBackgroundUpdater(ctx).stop();

        TcHelper helper = (TcHelper)getTcHelper(ctx);
        helper.close();
    }
}

