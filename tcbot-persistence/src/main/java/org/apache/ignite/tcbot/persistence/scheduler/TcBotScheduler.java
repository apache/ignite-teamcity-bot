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
package org.apache.ignite.tcbot.persistence.scheduler;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TcBotScheduler implements IScheduler {
    public static final int POOL_SIZE = 16;
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(TcBotScheduler.class);

    private ThreadFactory threadFactory = Executors.defaultThreadFactory();

    private final ThreadFactory threadFactory1 = r -> {
        Thread thread = threadFactory.newThread(r);

        thread.setName("tc-scheduler-" + thread.getName());

        return thread;
    };

    /** Executor service. */
    private volatile ScheduledExecutorService executorSvc = Executors.newScheduledThreadPool(POOL_SIZE, threadFactory1);

    /** Submit named task checker guard. */
    private AtomicBoolean tickGuard = new AtomicBoolean();

    /** Named tasks. */
    private final ConcurrentMap<String, NamedTask> namedTasks = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override public void invokeLater(Runnable cmd, long delay, TimeUnit unit) {
        service().schedule(cmd, delay, unit);
    }

    /** {@inheritDoc} */
    @Override public void sheduleNamed(String fullName, Runnable cmd, long queitPeriod, TimeUnit unit) {
        NamedTask task = namedTasks.computeIfAbsent(fullName, NamedTask::new);

        task.sheduleWithQuitePeriod(cmd, queitPeriod, unit);

        if (tickGuard.compareAndSet(false, true)) {
            for (int threadId = 0; threadId < POOL_SIZE; threadId++) {
                String threadNme = "Runner " + Strings.padStart(Integer.toString(threadId), 2, '0');
                int period = 15000 + ThreadLocalRandom.current().nextInt(10000);
                service().scheduleAtFixedRate(() -> checkNamedTasks(threadNme), 0, period, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * @param threadNme Runner name to be used in display.
     */
    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    @MonitoredTask(name = "Scheduled", nameExtArgIndex = 0, log = false)
    protected String checkNamedTasks(String threadNme) {
        AtomicInteger run = new AtomicInteger();
        List<Throwable> problems = new ArrayList<>();
        namedTasks.forEach((s, task) -> {
            try {
                Runnable runnable = task.runIfNeeded();
                if (runnable != null)
                    run.incrementAndGet();
            }
            catch (Exception e) {
                logger.error("Background task [" + s + "] execution failure: " + e.getMessage(), e);
                problems.add(e);
            }
        });

        return "Finished " + run.get() + " task(s) " + (problems.isEmpty() ? "" : (", exceptions: " + problems.toString()));
    }

    /** {@inheritDoc} */
    @Override public void stop() {
        if (executorSvc != null) {
            executorSvc.shutdown();
            try {
                if (!executorSvc.awaitTermination(10, TimeUnit.SECONDS))
                    executorSvc.shutdownNow();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     *
     */
    private ScheduledExecutorService service() {
        return Preconditions.checkNotNull(executorSvc, "Service should be created");
    }
}
