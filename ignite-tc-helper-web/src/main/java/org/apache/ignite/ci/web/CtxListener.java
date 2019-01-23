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

import com.google.common.base.Preconditions;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.IgniteTcBotModule;
import org.apache.ignite.ci.di.scheduler.IScheduler;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.tcbot.issue.IssueDetector;
import org.apache.ignite.ci.teamcity.pure.TeamcityRecorder;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;

/**
 */
public class CtxListener implements ServletContextListener {
    /** Javax.Injector property code for servlet context. */
    public static final String INJECTOR = "injector";

    public static Injector getInjector(ServletContext ctx) {
        return (Injector)ctx.getAttribute(INJECTOR);
    }

    public static BackgroundUpdater getBackgroundUpdater(ServletContext ctx) {
        return getInjector(ctx).getInstance(BackgroundUpdater.class);
    }

    /** {@inheritDoc} */
    @Override public void contextInitialized(ServletContextEvent sctxEvt) {
        initLoggerBridge();
        IgniteTcBotModule igniteTcBotModule = new IgniteTcBotModule();
        Injector injectorPreCreated = Guice.createInjector(igniteTcBotModule);

        Injector injector = igniteTcBotModule.startIgniteInit(injectorPreCreated);

        ITcServerProvider instance = injector.getInstance(ITcServerProvider.class);
        Preconditions.checkState(instance == injector.getInstance(ITcServerProvider.class));

        final ServletContext ctx = sctxEvt.getServletContext();

        ctx.setAttribute(INJECTOR, injector);
    }

    /**
     * initializes logger bridgle for jul->Slf4j redirection for Jersey.
     */
    private void initLoggerBridge() {
        java.util.logging.Logger rootLog = java.util.logging.LogManager.getLogManager().getLogger("");
        java.util.logging.Handler[] handlers = rootLog.getHandlers();

        for (int i = 0; i < handlers.length; i++)
            rootLog.removeHandler(handlers[i]);

        org.slf4j.bridge.SLF4JBridgeHandler.install();
    }

    /** {@inheritDoc} */
    @Override public void contextDestroyed(ServletContextEvent sctxEvt) {
        final ServletContext ctx = sctxEvt.getServletContext();

        Injector injector = getInjector(ctx);

        getBackgroundUpdater(ctx).stop();

        try {

            injector.getInstance(IssueDetector.class).stop();
            injector.getInstance(TcUpdatePool.class).stop();
            injector.getInstance(BuildObserver.class).stop();

            injector.getInstance(IScheduler.class).stop();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try {
            injector.getInstance(TeamcityRecorder.class).stop();
        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            TcHelperDb.stop(injector.getInstance(Ignite.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

