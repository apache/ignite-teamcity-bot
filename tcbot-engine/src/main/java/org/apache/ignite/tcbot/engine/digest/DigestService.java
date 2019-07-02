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

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.tcbot.engine.conf.INotificationChannel;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.tracked.IDetailedStatusForTrackedBranch;
import org.apache.ignite.tcbot.engine.tracked.TrackedBranchChainsProcessor;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.creds.ICredentialsProv;

/**
 *
 */
public class DigestService {
    @Inject
    private ITcBotConfig cfg;

    @Inject
    private IDetailedStatusForTrackedBranch tbProc;

    @Inject
    private WeeklyFailuresDao dao;

    @Inject
    private IScheduler scheduler;

    @Nullable
    private volatile ICredentialsProv bgCreds;

    private final AtomicBoolean init = new AtomicBoolean();

    public WeeklyFailuresDigest generateFromCurrentState(String trBrName, ICredentialsProv creds) {
        WeeklyFailuresDigest res = new WeeklyFailuresDigest(trBrName);

        DsSummaryUi failures = tbProc.getTrackedBranchTestFailures(trBrName, false,
                10, creds, SyncMode.RELOAD_QUEUED, true);
        res.failedTests = failures.failedTests;
        res.failedSuites = failures.failedToFinish;

        res.totalTests = failures.servers.stream().mapToInt(s -> s.totalTests).sum();

        res.trustedTests = failures.servers.stream().mapToInt(s -> s.trustedTests).sum();

        return res;
    }

    public WeeklyFailuresDigest getLastDigest(String branchNn) {
        return dao.get(branchNn);
    }

    public void startBackgroundCheck(ICredentialsProv creds) {
        if (init.compareAndSet(false, true)) {
            this.bgCreds = creds;

            scheduler.sheduleNamed("digestServiceCheckDigest",
                this::digestServiceCheckDigest, 15, TimeUnit.MINUTES);
        }
    }

    private void digestServiceCheckDigest() {
        Collection<? extends INotificationChannel> channels = cfg.notifications().channels();

    }
}
