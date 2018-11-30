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
package org.apache.ignite.ci.tcbot.visa;

import java.util.LinkedList;
import java.util.List;

/**
 * Status of contribution check details returned from server
 */
@SuppressWarnings("PublicField") public class ContributionCheckStatus {
    public int queuedBuilds;
    public int runningBuilds;

    /** Suite id. */
    public String suiteId;

    /** Branch with finished/cancelled suite results, null if suite is running or in case there was no run suite at all. */
    public String branchWithFinishedSuite;

    /** Suite finished for brach {@link #branchWithFinishedSuite}. Determines trigger button color. */
    public Boolean suiteIsFinished;

    /** Resolved suite branch: Some branch probably with finished or queued builds in in, or default pull/nnnn/head. */
    public String resolvedBranch;

    /** Observations status: Filled if build observer has something sheduled related to {@link #resolvedBranch} */
    public String observationsStatus;

    public List<String> webLinksQueuedSuites = new LinkedList<>();

    public ContributionCheckStatus() {
    }

    public ContributionCheckStatus(String suiteId, String resolvedBranch) {
        this.suiteId = suiteId;
        this.resolvedBranch = resolvedBranch;
    }
}
