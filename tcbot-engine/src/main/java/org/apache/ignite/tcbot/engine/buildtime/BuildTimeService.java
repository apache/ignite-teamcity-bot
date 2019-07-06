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

import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.common.util.TimeUtil;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.ui.BuildTimeRecordUi;
import org.apache.ignite.tcbot.engine.ui.BuildTimeResultUi;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcignited.buildtime.BuildTimeRecord;
import org.apache.ignite.tcignited.buildtime.BuildTimeResult;
import org.apache.ignite.tcignited.creds.ICredentialsProv;
import org.apache.ignite.tcignited.history.HistoryCollector;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BuildTimeService {
    @Inject private ITeamcityIgnitedProvider tcProv;

    /** Config. */
    @Inject private ITcBotConfig cfg;

    @Inject private FatBuildDao fatBuildDao;

    @Inject private IStringCompactor compactor;

    @Inject private HistoryCollector historyCollector;

    @Inject private IScheduler scheduler;

    private volatile BuildTimeResult lastRes1d = new BuildTimeResult();

    @Inject private BuildRefDao buildRefDao;

    public BuildTimeResultUi analytics(ICredentialsProv prov) {
        if (buildRefDao.buildRefsCache() == null)
            return new BuildTimeResultUi();

        Collection<String> allServers = cfg.getServerIds();

        scheduler.sheduleNamed("BuildTimeService.loadAnalytics",
                this::loadAnalytics, 15, TimeUnit.MINUTES);

        Set<Integer> availableServers = allServers.stream()
                .filter(prov::hasAccess)
                .map(ITeamcityIgnited::serverIdToInt)
                .collect(Collectors.toSet());

        BuildTimeResultUi resultUi = new BuildTimeResultUi();

        long minDuration = Duration.ofMinutes(90).toMillis();
        long minDurationTimeout = Duration.ofMinutes(60).toMillis();
        int cntToInclude = 50;
        BuildTimeResult res = lastRes1d;

        res.topByBuildTypes(availableServers, minDuration, cntToInclude)
                .stream().map(this::convertToUi).forEach(e -> resultUi.byBuildType.add(e));

        res.topTimeoutsByBuildTypes(availableServers, minDurationTimeout, cntToInclude)
                .stream().map(this::convertToUi).forEach(e -> resultUi.timedOutByBuildType.add(e));

        return resultUi;
    }

    public BuildTimeRecordUi convertToUi(Map.Entry<Long, BuildTimeRecord> e) {
        BuildTimeRecordUi buildTimeRecordUi = new BuildTimeRecordUi();
        Long key = e.getKey();
        int btId = BuildTimeResult.cacheKeyToBuildType(key);
        buildTimeRecordUi.buildType = compactor.getStringFromId(btId);

        buildTimeRecordUi.averageDuration = TimeUtil.millisToDurationPrintable(e.getValue().avgDuration());
        buildTimeRecordUi.totalDuration =  TimeUtil.millisToDurationPrintable(e.getValue().totalDuration());
        return buildTimeRecordUi;
    }

    @SuppressWarnings("WeakerAccess")
    @MonitoredTask(name = "Load Build Time Analytics")
    protected void loadAnalytics() {
        int days = 1;

        List<Long> idsToCheck = historyCollector.findAllRecentBuilds(days, cfg.getServerIds());

        BuildTimeResult res = fatBuildDao.loadBuildTimeResult(days, idsToCheck);

        lastRes1d = res;
    }
}
