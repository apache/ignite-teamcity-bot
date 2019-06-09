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
package org.apache.ignite.tcbot.engine.digest;

import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.tracked.TrackedBranchChainsProcessor;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.creds.ICredentialsProv;

import javax.inject.Inject;

public class DigestService {
    @Inject
    ITcBotConfig cfg;

    @Inject
    TrackedBranchChainsProcessor tbProc;

    public WeeklyFailuresDigest generate(String trBrName, ICredentialsProv creds) {
        WeeklyFailuresDigest res = new WeeklyFailuresDigest();

        DsSummaryUi failures = tbProc.getTrackedBranchTestFailures(trBrName, false,
                1, creds, SyncMode.RELOAD_QUEUED, true);
        res.failedTests = failures.failedTests;
        res.trustedTests = failures.servers.stream().mapToInt(s->s.trustedTests).sum();

        return res;
    }
}
