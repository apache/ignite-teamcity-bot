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
package org.apache.ignite.tcbot.engine.tracked;

import javax.annotation.Nullable;
import org.apache.ignite.tcbot.engine.chain.SortOption;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.creds.ICredentialsProv;

/**
 * Process failures for some setup tracked branch, which may be triggered/monitored by TC Bot.
 */
public interface IDetailedStatusForTrackedBranch {
    /**
     * @param branch Branch.
     * @param checkAllLogs Check all logs.
     * @param buildResMergeCnt Build results merge count.
     * @param creds Credentials.
     * @param syncMode Sync mode.
     * @param calcTrustedTests Calculate trusted tests count.
     * @param tagSelected Selected tag based filter. If null or empty all data is returned.
     * @param displayMode Suites and tests display mode. Default - failures only.
     * @param sortOption Sort mode
     */
    public DsSummaryUi getTrackedBranchTestFailures(
        @Nullable String branch,
        @Nullable Boolean checkAllLogs,
        int buildResMergeCnt,
        ICredentialsProv creds,
        SyncMode syncMode,
        boolean calcTrustedTests,
        @Nullable String tagSelected,
        @Nullable DisplayMode displayMode,
        @Nullable SortOption sortOption);

    //  * @param baseTrackedBranch Branch tracked branch in Bot, has a priority if both TC & Bot branches (baseBranchForTcParm) present.
}
