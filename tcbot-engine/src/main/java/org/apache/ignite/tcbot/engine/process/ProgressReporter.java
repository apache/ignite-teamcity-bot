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
package org.apache.ignite.tcbot.engine.process;

import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.ignite.tcbot.common.monitoring.MonitoredTasks;

/**
 * Shared progress reporter for monitored tasks and user-visible UI processes.
 */
@Singleton
public class ProgressReporter {
    /** Current user-visible process id, when a UI-triggered operation is running. */
    private final ThreadLocal<Long> currentProcessId = new ThreadLocal<>();

    /** User-visible process monitor. */
    @Inject private BotProcessMonitor processMonitor;

    /**
     * Reports progress to every status surface available in the current thread.
     *
     * @param status Status text.
     */
    public void report(String status) {
        MonitoredTasks.reportCurrentTaskStatus(status);
        processMonitor.status(currentProcessId.get(), status);
    }

    /**
     * Runs an action with a current user-visible process id.
     *
     * @param processId Process id supplied by UI.
     * @param kind Process kind.
     * @param acceptedStatus Initial status.
     * @param action Action.
     * @return Action result.
     */
    public String run(@Nullable Long processId, String kind, String acceptedStatus, Callable<String> action)
        throws Exception {
        processMonitor.start(processId, kind, acceptedStatus);

        Long prevProcessId = currentProcessId.get();

        try {
            currentProcessId.set(processId);
            report(acceptedStatus);

            String result = action.call();

            processMonitor.finish(processId, result);

            return result;
        }
        catch (Exception e) {
            processMonitor.fail(processId, e);

            throw e;
        }
        finally {
            if (prevProcessId == null)
                currentProcessId.remove();
            else
                currentProcessId.set(prevProcessId);
        }
    }
}
