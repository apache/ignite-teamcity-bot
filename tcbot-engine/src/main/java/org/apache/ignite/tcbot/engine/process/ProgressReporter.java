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
import java.util.function.Supplier;
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
    public <T> T run(@Nullable Long processId, String kind, String acceptedStatus, Callable<T> action)
        throws Exception {
        return run(processId, kind, acceptedStatus, null, action);
    }

    /**
     * Runs an action with a current user-visible process id.
     *
     * @param processId Process id supplied by UI.
     * @param kind Process kind.
     * @param acceptedStatus Initial status.
     * @param finishStatus Final status. Null means use action result.
     * @param action Action.
     * @return Action result.
     */
    public <T> T run(@Nullable Long processId, String kind, String acceptedStatus, @Nullable String finishStatus,
        Callable<T> action) throws Exception {
        processMonitor.start(processId, kind, acceptedStatus);

        return withProcess(processId, () -> {
            report(acceptedStatus);

            T result = action.call();

            processMonitor.finish(processId, finishStatus == null ? String.valueOf(result) : finishStatus);

            return result;
        }, e -> {
            processMonitor.fail(processId, e);
        });
    }

    /**
     * Runs an action with a current user-visible process id and wraps checked exceptions.
     *
     * @param processId Process id supplied by UI.
     * @param kind Process kind.
     * @param acceptedStatus Initial status.
     * @param action Action.
     * @return Action result.
     */
    public <T> T runUnchecked(@Nullable Long processId, String kind, String acceptedStatus, Callable<T> action) {
        try {
            return run(processId, kind, acceptedStatus, action);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Runs an action with a current user-visible process id and wraps checked exceptions.
     *
     * @param processId Process id supplied by UI.
     * @param kind Process kind.
     * @param acceptedStatus Initial status.
     * @param finishStatus Final status. Null means use action result.
     * @param action Action.
     * @return Action result.
     */
    public <T> T runUnchecked(@Nullable Long processId, String kind, String acceptedStatus,
        @Nullable String finishStatus, Callable<T> action) {
        try {
            return run(processId, kind, acceptedStatus, finishStatus, action);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Captures current process id and restores it around a callable executed later, usually in a pool thread.
     *
     * @param action Action.
     * @return Callable with process reporting context.
     */
    public <T> Callable<T> preserveCallable(Callable<T> action) {
        Long processId = currentProcessId.get();

        return () -> withProcess(processId, action, null);
    }

    /**
     * Captures current process id and restores it around a supplier executed later, usually in a pool thread.
     *
     * @param action Action.
     * @return Supplier with process reporting context.
     */
    public <T> Supplier<T> preserveSupplier(Supplier<T> action) {
        Long processId = currentProcessId.get();

        return () -> {
            try {
                return withProcess(processId, action::get, null);
            }
            catch (RuntimeException e) {
                throw e;
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * Runs an action with a current user-visible process id without starting or finishing the process.
     *
     * @param processId Process id.
     * @param action Action.
     * @param onFail Failure callback.
     * @return Action result.
     */
    private <T> T withProcess(@Nullable Long processId, Callable<T> action, @Nullable FailureCallback onFail)
        throws Exception {
        Long prevProcessId = currentProcessId.get();

        try {
            currentProcessId.set(processId);

            return action.call();
        }
        catch (Exception e) {
            if (onFail != null)
                onFail.onFail(e);

            throw e;
        }
        finally {
            if (prevProcessId == null)
                currentProcessId.remove();
            else
                currentProcessId.set(prevProcessId);
        }
    }

    /** Failure callback. */
    private interface FailureCallback {
        /** */
        void onFail(Exception e);
    }
}
