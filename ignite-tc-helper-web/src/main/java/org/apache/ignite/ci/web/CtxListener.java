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

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.util.logging.Handler;
import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.tcbot.TcBotWebAppModule;
import org.apache.ignite.ci.tcbot.issue.IssueDetector;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTaskInterceptor;
import org.apache.ignite.tcbot.engine.cleaner.Cleaner;
import org.apache.ignite.tcbot.engine.conf.INotificationChannel;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.NotificationsConfig;
import org.apache.ignite.tcbot.engine.pool.TcUpdatePool;
import org.apache.ignite.tcbot.notify.ISlackSender;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcservice.http.TeamcityRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class CtxListener implements ServletContextListener {
    /** Javax.Injector property code for servlet context. */
    private static final String INJECTOR = "injector";

    @Nullable private static volatile Logger logger;

    public static Injector getInjector(ServletContext ctx) {
        return (Injector)ctx.getAttribute(INJECTOR);
    }

    /** {@inheritDoc} */
    @Override public void contextInitialized(ServletContextEvent sctxEvt) {
        initLoggerBridge();
        TcBotWebAppModule igniteTcBotModule = new TcBotWebAppModule();
        Injector injectorPreCreated = Guice.createInjector(igniteTcBotModule);

        Injector injector = igniteTcBotModule.startIgniteInit(injectorPreCreated);

        final ServletContext ctx = sctxEvt.getServletContext();

        ctx.setAttribute(INJECTOR, injector);

        sendMessageToSlackChannel("TeamCity Bot is started!", ctx);
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
        final ServletContext ctx = sctxEvt.getServletContext();

        sendMessageToSlackChannel("TeamCity Bot is stopped!", ctx);

        Injector injector = getInjector(ctx);

        try {
            injector.getInstance(IssueDetector.class).stop();
            injector.getInstance(TcUpdatePool.class).stop();
            injector.getInstance(BuildObserver.class).stop();
            injector.getInstance(IScheduler.class).stop();
            injector.getInstance(Cleaner.class).stop();
        }
        catch (Exception e) {
            e.printStackTrace();

            if (logger != null)
                logger.error("Exception during shutdown: " + e.getMessage(), e);
        }

        try {
            injector.getInstance(TeamcityRecorder.class).stop();
        }
        catch (IOException e) {
            e.printStackTrace();

            if (logger != null)
                logger.error("Exception during shutdown: " + e.getMessage(), e);
        }

        try {
            injector.getInstance(MonitoredTaskInterceptor.class).close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try {
            TcHelperDb.stop(injector.getInstance(Ignite.class));
        }
        catch (Exception e) {
            e.printStackTrace();

            if (logger != null)
                logger.error("Exception during shutdown: " + e.getMessage(), e);
        }
    }

    private void sendMessageToSlackChannel(String msg, ServletContext ctx) {
        try {
            ISlackSender slackSender = CtxListener.getInjector(ctx).getInstance(ISlackSender.class);

            ITcBotConfig tcBotConfig = CtxListener.getInjector(ctx).getInstance(ITcBotConfig.class);

            NotificationsConfig notifications = tcBotConfig.notifications();

            for (INotificationChannel channel : notifications.channels()) {
                if (channel.slack() != null && channel.slack().startsWith("#"))
                    slackSender.sendMessage(channel.slack(), msg, notifications);
            }
        }
        catch (Exception e) {
            e.printStackTrace();

            if (logger != null)
                logger.error("Exception during sending message to the slack channel: " + e.getMessage(), e);
        }
    }
}
