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

/** Shared names for cross-service maintenance actions. */
public final class MaintenanceActionNames {
    /** Run the build observer immediately. */
    public static final String RUNNING_VISAS_CHECK_RESULTS = "runningVisas.checkResults";

    /** List observed visa branches as srvId|normalizedBranch lines. */
    public static final String RUNNING_VISAS_OBSERVED_BRANCHES = "runningVisas.observedBranches";

    /** */
    private MaintenanceActionNames() {
        // No-op.
    }
}
