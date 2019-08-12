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

package org.apache.ignite.tcbot.engine.build;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.tcbot.engine.chain.BuildChainProcessor;
import org.apache.ignite.tcbot.engine.chain.FullChainRunCtx;
import org.apache.ignite.tcbot.engine.chain.LatestRebuildMode;
import org.apache.ignite.tcbot.engine.chain.ProcessLogsMode;
import org.apache.ignite.tcbot.engine.ui.DsChainUi;
import org.apache.ignite.tcbot.engine.ui.DsSummaryUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.build.UpdateCountersStorage;
import org.apache.ignite.tcignited.buildref.BranchEquivalence;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.apache.ignite.tcservice.ITeamcity;

/**
 * Displays single build at server by ID.
 */
public class SingleBuildResultsService {
    @Inject BuildChainProcessor buildChainProcessor;
    @Inject ITeamcityIgnitedProvider tcIgnitedProv;
    @Inject BranchEquivalence branchEquivalence;
    @Inject IStringCompactor compactor;
    @Inject UpdateCountersStorage updateCounters;

    @Nonnull public DsSummaryUi getSingleBuildResults(String srvCodeOrAlias, Integer buildId,
        @Nullable Boolean checkAllLogs, SyncMode syncMode, ICredentialsProv prov) {
        DsSummaryUi res = new DsSummaryUi();

        tcIgnitedProv.checkAccess(srvCodeOrAlias, prov);

        ITeamcityIgnited tcIgnited = tcIgnitedProv.server(srvCodeOrAlias, prov);

        String failRateBranch = ITeamcity.DEFAULT;

        ProcessLogsMode procLogs = (checkAllLogs != null && checkAllLogs) ? ProcessLogsMode.ALL : ProcessLogsMode.SUITE_NOT_COMPLETE;

        FullChainRunCtx ctx = buildChainProcessor.loadFullChainContext(
            tcIgnited,
            Collections.singletonList(buildId),
            LatestRebuildMode.NONE,
            procLogs,
            false,
            failRateBranch,
            syncMode,
            null,
            null);

        DsChainUi chainStatus = new DsChainUi(srvCodeOrAlias, tcIgnited.serverCode(), ctx.branchName());

        chainStatus.initFromContext(tcIgnited, ctx, failRateBranch, compactor, false, null, null, -1, null, false, false);

        res.addChainOnServer(chainStatus);

        res.initCounters(getBranchCntrs(srvCodeOrAlias, buildId, prov));
        return res;
    }



    public Map<Integer, Integer> getBranchCntrs(String srvCodeOrAlias,
        Integer buildId, ICredentialsProv creds) {
        tcIgnitedProv.checkAccess(srvCodeOrAlias, creds);

        String tcBranch = tcIgnitedProv.server(srvCodeOrAlias, creds).getFatBuild(buildId, SyncMode.LOAD_NEW).branchName(compactor);

        Set<Integer> allBranchIds = new HashSet<>();

        allBranchIds.addAll(branchEquivalence.branchIdsForQuery(tcBranch, compactor));
        allBranchIds.addAll(branchEquivalence.branchIdsForQuery(ITeamcity.DEFAULT, compactor));

        return updateCounters.getCounters(allBranchIds);
    }
}
