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

package org.apache.ignite.tcbot.engine.buildtime;

import java.time.Duration;
import java.util.Collection;
import javax.cache.Cache;
import javax.inject.Inject;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.ui.BuildTimeSummaryUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.apache.ignite.tcignited.history.HistoryCollector;
import org.apache.ignite.tcignited.history.RunHistCompactedDao;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.stat.Statistics;

public class BuildTimeService {

    @Inject ITeamcityIgnitedProvider tcProv;

    /** Config. */
    @Inject ITcBotConfig cfg;

    @Inject FatBuildDao fatBuildDao;

    @Inject BuildRefDao buildRefDao;

    @Inject RunHistCompactedDao runHistCompactedDao;

    @Inject IStringCompactor compactor;

    @Inject HistoryCollector historyCollector;

    public BuildTimeSummaryUi analytics(ICredentialsProv prov) {
        String serverCode = cfg.primaryServerCode();

        ITeamcityIgnited server = tcProv.server(serverCode, prov);

        // fatBuildDao.forEachFatBuild();

        Collection<String> allServers = cfg.getServerIds();

        forEachBuildRef(1, allServers);

        fatBuildDao.forEachFatBuild();

        return null;
    }

    public void forEachBuildRef(int days, Collection<String> allServers) {
        IgniteCache<Long, BinaryObject> cacheBin = buildRefDao.buildRefsCache().withKeepBinary();

        // Ignite ignite = igniteProvider.get();

        //IgniteCompute serversCompute = ignite.compute(ignite.cluster().forServers());

        int stateRunning = compactor.getStringId(BuildRef.STATE_RUNNING);
        final int stateQueued = compactor.getStringId(BuildRef.STATE_QUEUED);
        Integer buildDurationId = compactor.getStringIdIfPresent(Statistics.BUILD_DURATION);

        long minTs = System.currentTimeMillis() - Duration.ofDays(days).toMillis();
        QueryCursor<Cache.Entry<Long, BinaryObject>> query = cacheBin.query(
            new ScanQuery<Long, BinaryObject>()
                .setFilter((k, v) -> {

                    int state = v.field("state");

                    return stateQueued != state;
                }));

        int cnt = 0;

        try (QueryCursor<Cache.Entry<Long, BinaryObject>> cursor = query) {
            for (Cache.Entry<Long, BinaryObject> next : cursor) {
                Long key = next.getKey();
                int srvId = BuildRefDao.cacheKeyToSrvId(key);

                int buildId = BuildRefDao.cacheKeyToBuildId(key);

                Integer borderBuildId = runHistCompactedDao.getBorderForAgeForBuildId(srvId, days);

                boolean passesDate = borderBuildId == null || buildId >= borderBuildId;

                if (!passesDate)
                    continue;

                Long startTs = historyCollector.getBuildStartTime(srvId, buildId);
                if (startTs != null && startTs < minTs)
                    continue; //time not saved in the DB, skip

                BinaryObject buildBinary = next.getValue();
                long runningTime = 0l;// getBuildRunningTime(stateRunning, buildDurationId, buildBinary);

                System.err.println("Found build at srv [" + srvId + "]: [" + buildId + "] to analyze, ts="+ startTs);

                cnt++;
            }
        }

        System.err.println("Total builds to load " + cnt);

        // serversCompute.call(new BuildTimeIgniteCallable(cacheBin, stateRunning, buildDurationId));
    }
}
