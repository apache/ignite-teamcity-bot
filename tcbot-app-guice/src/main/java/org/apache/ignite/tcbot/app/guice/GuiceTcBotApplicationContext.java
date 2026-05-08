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

package org.apache.ignite.tcbot.app.guice;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.observer.BuildObserver;
import org.apache.ignite.ci.tcbot.issue.IssueDetector;
import org.apache.ignite.tcbot.common.application.TcBotApplicationContext;
import org.apache.ignite.tcbot.common.monitoring.MonitoredTasks;
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

class GuiceTcBotApplicationContext implements TcBotApplicationContext {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(GuiceTcBotApplicationContext.class);

    private Injector injector;

    /** {@inheritDoc} */
    @Override public void start() {
        TcBotWebAppModule module = new TcBotWebAppModule();
        Injector preCreated = Guice.createInjector(module);

        injector = module.startIgniteInit(preCreated);

        sendMessageToSlackChannel("TeamCity Bot is started!");
    }

    /** {@inheritDoc} */
    @Override public <T> T getInstance(Class<T> type) {
        if (injector == null)
            throw new IllegalStateException("TC Bot application context is not started");

        return injector.getInstance(type);
    }

    /** {@inheritDoc} */
    @Override public void close() {
        sendMessageToSlackChannel("TeamCity Bot is stopped!");

        shutdown("shutdown", () -> {
            getInstance(IssueDetector.class).stop();
            getInstance(TcUpdatePool.class).stop();
            getInstance(BuildObserver.class).stop();
            getInstance(IScheduler.class).stop();
            getInstance(Cleaner.class).stop();
        });

        shutdown("TeamCity recorder shutdown", () -> {
            getInstance(TeamcityRecorder.class).stop();
        });

        shutdown("monitoring shutdown", () -> {
            getInstance(MonitoredTasks.class).close();
        });

        shutdown("Ignite shutdown", () -> {
            TcHelperDb.stop(getInstance(Ignite.class));
        });
    }

    private void shutdown(String action, ThrowingRunnable actionToRun) {
        try {
            actionToRun.run();
        }
        catch (Exception e) {
            e.printStackTrace();

            logger.error("Exception during " + action + ": " + e.getMessage(), e);
        }
    }

    private void sendMessageToSlackChannel(String msg) {
        try {
            ISlackSender slackSender = getInstance(ISlackSender.class);
            ITcBotConfig tcBotConfig = getInstance(ITcBotConfig.class);
            NotificationsConfig notifications = tcBotConfig.notifications();

            for (INotificationChannel channel : notifications.channels()) {
                if (channel.slack() != null && channel.slack().startsWith("#"))
                    slackSender.sendMessage(channel.slack(), msg, notifications);
            }
        }
        catch (Exception e) {
            e.printStackTrace();

            logger.error("Exception during sending message to the slack channel: " + e.getMessage(), e);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
