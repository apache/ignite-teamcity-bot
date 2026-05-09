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

import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class VisaStatus {
    /** */
    @Nullable public String userName;

    /** Requester profile URL. */
    @Nullable public String userUrl;

    /** Requester avatar URL. */
    @Nullable public String userAvatarUrl;

    /** Branch name. */
    @Nullable public String branchName;

    /** JIRA ticket full name. */
    @Nullable public String ticket;

    /** JIRA ticket URL. */
    @Nullable public String ticketUrl;

    /** Pull request number selected for commenting. */
    @Nullable public Integer prNum;

    /** Pull request URL. */
    @Nullable public String prUrl;

    /** Pull request author GitHub login. */
    @Nullable public String prAuthor;

    /** Pull request author GitHub profile URL. */
    @Nullable public String prAuthorUrl;

    /** Pull request author avatar URL. */
    @Nullable public String prAuthorAvatarUrl;

    /** */
    @Nullable public String status;

    /** User-visible final comment status. */
    @Nullable public String commentStatus;

    /** Requested comment targets. */
    @Nullable public String commentTargets;

    /** Comment only if no blockers were found. */
    public boolean commentOnlyIfNoBlockers;

    /** Human-readable requested analysis slice. */
    @Nullable public String analysisSlice;

    /** Ordered rerun or observed build ids. */
    @Nullable public String buildIds;

    /** Whether this request was tied to explicitly triggered/rerun builds. */
    public boolean rerun;

    /** Whether this request has ever been scheduled for observation. */
    public boolean wasEverObserved;

    /** */
    @Nullable public String commentUrl;

    /** */
    @Nullable public String date;

    /** How long ago visa was requested. */
    @Nullable public String requestedAgo;

    /** Visa request age, milliseconds. */
    public long requestedAgeMs;

    /** Current bot report URL for this branch and suite. */
    @Nullable public String reportUrl;

    /** Current observed builds progress. */
    @Nullable public String runningProgress;

    /** TeamCity URL for current active observed build. */
    @Nullable public String runningBuildUrl;

    /** Estimated completion based on TeamCity running-info. */
    @Nullable public String estimatedCompletion;

    /** */
    @Nullable public String cancelUrl;

    /** Build type ID, for which visa was ordered. */
    @Nullable public String buildTypeId;

    /** */
    @Nullable public String buildTypeName;

    /** */
    @Nullable public String baseBranchForTc;

    /** */
    public int blockers;
}
