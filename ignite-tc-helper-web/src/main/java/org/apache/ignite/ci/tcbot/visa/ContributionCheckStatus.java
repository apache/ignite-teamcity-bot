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
 * Status of contribution check details returned from server. UI model for displaying detailed {@link
 * ContributionToCheck} status in relation to particular Run Configuration.
 */
@SuppressWarnings("PublicField") public class ContributionCheckStatus {
    /** Queued builds. */
    public int queuedBuilds;

    /** Running builds. */
    public int runningBuilds;

    /** Suite id. */
    public String suiteId;

    /** Branch with finished/cancelled suite results, null if suite is running or in case there was no run suite at all. */
    public String branchWithFinishedSuite;

    /** Finished suite commit hash short. */
    public String finishedSuiteCommit;

    /** Suite finished for brach {@link #branchWithFinishedSuite}. Determines trigger button color. */
    public Boolean suiteIsFinished;

    /** Resolved suite branch: Some branch probably with finished or queued builds in in, or default pull/nnnn/head. */
    public String resolvedBranch;

    /** Observations status: Filled if build observer has something sheduled related to {@link #resolvedBranch} */
    public String observationsStatus;

    /** Web links to queued suites. */
    public List<String> webLinksQueuedSuites = new LinkedList<>();

    /** Default build type. */
    public boolean defaultBuildType = false;

    /**
     * @param suiteId Suite id.
     */
    public ContributionCheckStatus(String suiteId) {
        this.suiteId = suiteId;
    }

    /**
     * @param suiteId Suite id.
     * @param resolvedBranch Resolved branch.
     */
    public ContributionCheckStatus(String suiteId, String resolvedBranch) {
        this.suiteId = suiteId;
        this.resolvedBranch = resolvedBranch;
    }
}
