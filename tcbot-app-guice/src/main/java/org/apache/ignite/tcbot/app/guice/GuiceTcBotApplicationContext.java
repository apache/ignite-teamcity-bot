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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.ProvisionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final LifecycleTracker lifecycleTracker = new LifecycleTracker();

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    /** {@inheritDoc} */
    @Override public synchronized void start() {
        if (started.get())
            return;

        if (closed.get())
            throw new IllegalStateException("TC Bot application context is already closed");

        if (!started.compareAndSet(false, true))
            return;

        try {
            TcBotWebAppModule module = new TcBotWebAppModule();
            injector = Guice.createInjector(new LifecycleTrackingModule(lifecycleTracker), module);

            Future<Ignite> igniteFuture = module.startIgniteInit(injector);
            startBackgroundServicesWhenReady(igniteFuture);
        }
        catch (RuntimeException | Error e) {
            started.set(false);

            throw e;
        }

    }

    private void startBackgroundServicesWhenReady(Future<Ignite> igniteFuture) {
        Thread thread = new Thread(() -> {
            try {
                igniteFuture.get();

                if (!started.get() || closed.get())
                    return;

                getInstance(BuildObserver.class);
                ready.set(true);
            }
            catch (Exception e) {
                logger.error("Exception during background services start: " + e.getMessage(), e);
            }
        }, "tc-bot-background-services-start");

        thread.setDaemon(true);
        thread.start();
    }

    /** {@inheritDoc} */
    @Override public boolean isReady() {
        return ready.get() && !closed.get();
    }

    /** {@inheritDoc} */
    @Override public <T> T getInstance(Class<T> type) {
        if (injector == null)
            throw new IllegalStateException("TC Bot application context is not started");

        return injector.getInstance(type);
    }

    /** {@inheritDoc} */
    @Override public synchronized void close() {
        if (!closed.compareAndSet(false, true))
            return;

        ready.set(false);

        Injector injector = this.injector;
        if (injector == null)
            return;

        sendMessageToSlackChannel("TeamCity Bot is stopped!");

        shutdown("shutdown", () -> {
            shutdownIfProvisioned(IssueDetector.class, IssueDetector::stop);
            shutdownIfProvisioned(TcUpdatePool.class, TcUpdatePool::stop);
            shutdownIfProvisioned(BuildObserver.class, BuildObserver::stop);
            shutdownIfProvisioned(IScheduler.class, IScheduler::stop);
            shutdownIfProvisioned(Cleaner.class, Cleaner::stop);
        });

        shutdown("TeamCity recorder shutdown", () -> {
            shutdownIfProvisioned(TeamcityRecorder.class, TeamcityRecorder::stop);
        });

        shutdown("monitoring shutdown", () -> {
            shutdownIfProvisioned(MonitoredTasks.class, MonitoredTasks::close);
        });

        shutdown("Ignite shutdown", () -> {
            TcHelperDb.stop(getInstance(Ignite.class));
        });

        this.injector = null;
    }

    private <T> void shutdownIfProvisioned(Class<T> type, ThrowingConsumer<T> action) throws Exception {
        T instance = lifecycleTracker.getInstance(type);

        if (instance != null)
            action.accept(instance);
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

    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    private static class LifecycleTrackingModule extends AbstractModule {
        private final ProvisionListener listener;

        private LifecycleTrackingModule(ProvisionListener listener) {
            this.listener = listener;
        }

        /** {@inheritDoc} */
        @Override protected void configure() {
            bindListener(Matchers.any(), listener);
        }
    }

    private static class LifecycleTracker implements ProvisionListener {
        private final List<Object> instances = new ArrayList<>();

        /** {@inheritDoc} */
        @Override public <T> void onProvision(ProvisionInvocation<T> provision) {
            T instance = provision.provision();

            if (instance == null)
                return;

            synchronized (instances) {
                for (Object existing : instances) {
                    if (existing == instance)
                        return;
                }

                instances.add(instance);
            }
        }

        private <T> T getInstance(Class<T> type) {
            synchronized (instances) {
                for (Object instance : instances) {
                    if (type.isInstance(instance))
                        return type.cast(instance);
                }
            }

            return null;
        }
    }
}
