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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.List;

/**
 * Scheduler is a way to run background syncs between Ignite DB and REST services.
 */
public interface IScheduler {
    /**
     * Creates and executes a one-shot action that becomes enabled after the given delay.
     *
     * @param cmd the task to execute.
     * @param delay the time from now to delay execution.
     * @param unit the time unit of the delay parameter.
     * @throws RejectedExecutionException if the task cannot be scheduled for execution
     * @throws NullPointerException if command is null
     */
    public void invokeLater(Runnable cmd, long delay, TimeUnit unit);

    public void sheduleNamed(String fullName, Runnable cmd, long queitPeriod, TimeUnit unit);

    /**
     * Runs named action as soon as possible using scheduler executor.
     *
     * @param fullName Named task name.
     * @param cmd Task body.
     * @return {@code true} if the action was accepted, {@code false} if the same named task is already running or
     * queued.
     */
    public default boolean runNamedNow(String fullName, Runnable cmd) {
        return runNamedNow(fullName, cmd, null);
    }

    /**
     * Runs named action as soon as possible using scheduler executor.
     *
     * @param fullName Named task name.
     * @param cmd Task body.
     * @param processId Optional process monitor id associated with this manual run.
     * @return {@code true} if the action was accepted, {@code false} if the same named task is already running or
     * queued.
     */
    public default boolean runNamedNow(String fullName, Runnable cmd, Long processId) {
        sheduleNamed(fullName, cmd, 0, TimeUnit.MILLISECONDS);

        return true;
    }

    /**
     * @return User-visible named task state.
     */
    public default List<ScheduledTaskInfo> scheduledTasks() {
        return Collections.emptyList();
    }

    public void stop();
}
