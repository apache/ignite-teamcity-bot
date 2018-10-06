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
package org.apache.ignite.ci.di.scheduler;

import com.google.common.base.Preconditions;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


class TcBotScheduler implements IScheduler {
    /** Initial guard. */
    private AtomicBoolean initGuard = new AtomicBoolean();

    /** Executor service. */
    private volatile ScheduledExecutorService executorSvc;

    @Override public void invokeLater(Runnable cmd, long delay, TimeUnit unit) {
        service().schedule(cmd, delay, unit);
    }

    /** {@inheritDoc} */
    @Override public void stop() {
        if(executorSvc!=null)
            executorSvc.shutdown();
    }

    private ScheduledExecutorService service() {
        if (executorSvc == null && initGuard.compareAndSet(false, true))
            executorSvc = Executors.newScheduledThreadPool(3);

        Preconditions.checkNotNull(executorSvc, "Service should be created");

        return executorSvc;
    }
}
