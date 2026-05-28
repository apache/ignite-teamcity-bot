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

/**
 * User-visible state of a named scheduled task.
 */
@SuppressWarnings("PublicField")
public class ScheduledTaskInfo {
    /** Task name. */
    public String name;

    /** Task status. */
    public String status;

    /** Last finished timestamp. */
    public long lastFinishedTs;

    /** Fresh result validity period. */
    public long quietPeriodMs;

    /** Task has runnable action registered. */
    public boolean runnableAvailable;

    /** Task can be started by admin now. */
    public boolean canStartNow;

    /** Bot process monitor id associated with the currently queued/running manual action. */
    public Long processId;

    /** Current user-visible bot process status, if this task was started through a monitored manual action. */
    public String processStatus;

    /** Process kind, if there is an associated monitored process. */
    public String processKind;

    /** Process state, if there is an associated monitored process. */
    public String processState;

    /** Process running flag, if there is an associated monitored process. */
    public Boolean processRunning;
}
