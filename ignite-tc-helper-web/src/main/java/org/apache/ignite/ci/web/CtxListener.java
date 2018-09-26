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
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.QueryParam;

import com.google.common.base.Preconditions;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcServerProvider;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.TcHelper;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.IgniteTcBotModule;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.jetbrains.annotations.Nullable;

/**
 */
public class CtxListener implements ServletContextListener {
    private static final String TC_HELPER = "tcHelper";


    public static final String UPDATER = "updater";

    public static final String INJECTOR = "injector";

    private static final String POOL = "pool";

    public static Ignite getIgnite(ServletContext ctx) {
        return getInjector(ctx).getInstance(Ignite.class);
    }

    public static ITcHelper getTcHelper(ServletContext ctx) {
        return (ITcHelper)ctx.getAttribute(TC_HELPER);
    }

    public static Injector getInjector(ServletContext ctx) {
        return (Injector)ctx.getAttribute(INJECTOR);
    }

    public static BackgroundUpdater getBackgroundUpdater(ServletContext ctx) {
        return (BackgroundUpdater)ctx.getAttribute(UPDATER);
    }

    public static IAnalyticsEnabledTeamcity server(@QueryParam("serverId") @Nullable String srvId,
                                                   ServletContext ctx,
                                                   HttpServletRequest req) {
        ITcHelper tcHelper = getTcHelper(ctx);
        final ICredentialsProv creds = ICredentialsProv.get(req);
        return tcHelper.server(srvId, creds);
    }

    @Override public void contextInitialized(ServletContextEvent sctxEvt) {
        IgniteTcBotModule igniteTcBotModule = new IgniteTcBotModule();
        Injector injectorPreCreated = Guice.createInjector(igniteTcBotModule);

        Injector injector = igniteTcBotModule.startIgniteInit(injectorPreCreated);

        ITcServerProvider instance = injector.getInstance(ITcServerProvider.class);
        Preconditions.checkState(instance == injector.getInstance(ITcServerProvider.class));

        final ServletContext ctx = sctxEvt.getServletContext();

        ctx.setAttribute(INJECTOR, injector);

        final TcHelper tcHelper = injector.getInstance(TcHelper.class);

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

        Injector injector = getInjector(ctx);

        try {
            TcHelperDb.stop(injector.getInstance(Ignite.class));
        } catch (Exception e) {
            e.printStackTrace();
        }

        getBackgroundUpdater(ctx).stop();

        TcHelper helper = (TcHelper)getTcHelper(ctx);
        helper.close();

        try {
            injector.getInstance(BuildObserver.class).stop();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

