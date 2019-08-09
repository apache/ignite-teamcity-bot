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

package org.apache.ignite.ci.teamcity.ignited;

import com.google.common.base.Preconditions;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.Invocation;
import org.apache.ignite.tcignited.history.RunHistCompacted;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistKey;
import org.apache.ignite.tcbot.common.TcBotConst;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.SyncMode;
import org.apache.ignite.tcignited.build.TestCompactedV2;
import org.apache.ignite.tcignited.history.ISuiteRunHistory;
import org.apache.ignite.tcservice.model.result.tests.TestOccurrence;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TeamcityIgnitedMock {
    @NotNull
    public static ITeamcityIgnited getMutableMapTeamcityIgnited(Map<Integer, FatBuildCompacted> builds,
                                                                IStringCompactor c) {
        ITeamcityIgnited tcIgnited = Mockito.mock(ITeamcityIgnited.class);
        Map<RunHistKey, RunHistCompacted> histCache = new ConcurrentHashMap<>();
        final int srvId = 0;

        Answer<Object> buildAnswer = inv -> {
            Integer arg = inv.getArgument(0);

            return Preconditions.checkNotNull(builds.get(arg), "Can't find build in map [" + arg + "]");
        };
        when(tcIgnited.getFatBuild(anyInt(), any(SyncMode.class))).thenAnswer(buildAnswer);
        when(tcIgnited.getFatBuild(anyInt())).thenAnswer(buildAnswer);

        when(tcIgnited.getAllBuildsCompacted(anyString(), anyString()))
            .thenAnswer(inv -> {
                String btId = inv.getArgument(0);

                String branch = inv.getArgument(1);

                return builds.values()
                    .stream()
                    .filter(fb -> btId.equals(fb.buildTypeId(c)))
                    .filter(fb -> branch.equals(fb.branchName(c)))
                    .sorted(Comparator.comparing(BuildRefCompacted::id).reversed())
                    .collect(Collectors.toList());
            });

        when(tcIgnited.getLastNBuildsFromHistory(anyString(), anyString(), anyInt()))
            .thenAnswer(inv -> {
                String btId = inv.getArgument(0);

                String branch = inv.getArgument(1);

                Integer cnt = inv.getArgument(2);

                return builds.values()
                    .stream()
                    .filter(fb -> btId.equals(fb.buildTypeId(c)))
                    // .filter(fb -> branch.equals(fb.branchName(c)))
                    .sorted(Comparator.comparing(BuildRefCompacted::id).reversed())
                    .limit(cnt)
                    .map(BuildRefCompacted::id)
                    .collect(Collectors.toList());
            });

        when(tcIgnited.getTestRunHist(anyInt(), anyInt(), anyInt()))
            .thenAnswer((inv) -> {
                final Integer tstName = inv.getArgument(0);
                final Integer branchId = inv.getArgument(2);
                // System.out.println("Search history " + name + " in " + branch + ": " );

                if (histCache.isEmpty()) {
                    synchronized (histCache) {
                        if (histCache.isEmpty())
                            initHistory(c, histCache, builds, srvId);
                    }
                }

                if (tstName == null)
                    return null;

                if (branchId == null)
                    return null;

                final RunHistKey key = new RunHistKey(srvId, tstName, branchId);

                final RunHistCompacted runHistCompacted = histCache.get(key);

                System.out.println("Test history " + c.getStringFromId(tstName) + " in " + c.getStringFromId(branchId) + " => " + runHistCompacted);

                return runHistCompacted;
            });

        when(tcIgnited.getSuiteRunHist(anyInt(), anyInt()))
            .thenAnswer((inv) -> {
                final Integer suiteName = inv.getArgument(0);
                final Integer branchId = inv.getArgument(1);
                // System.out.println("Search history " + name + " in " + branch + ": " );
                if (histCache.isEmpty()) {
                    synchronized (histCache) {
                        if (histCache.isEmpty())
                            initHistory(c, histCache, builds, srvId);
                    }
                }

                ISuiteRunHistory mock = Mockito.mock(ISuiteRunHistory.class);

                when(mock.getTestRunHist(anyInt())).thenAnswer((inv2)-> {
                    final Integer tstName = inv2.getArgument(0);

                    if (tstName == null)
                        return null;

                    if (branchId == null)
                        return null;

                    final RunHistKey key = new RunHistKey(srvId, tstName, branchId);

                    final RunHistCompacted runHistCompacted = histCache.get(key);

                    System.out.println("Test history " + c.getStringFromId(tstName) + " in " + c.getStringFromId(branchId) + " => " + runHistCompacted);

                    return runHistCompacted;
                });

                return mock;
            });

        // when(tcIgnited.gitBranchPrefix()).thenReturn("ignite-");

        ITcServerConfig mock = mock(ITcServerConfig.class);
        when(tcIgnited.config()).thenReturn(mock);


        return tcIgnited;
    }

    public static void initHistory(IStringCompactor c, Map<RunHistKey, RunHistCompacted> resHistCache,
        Map<Integer, FatBuildCompacted> builds, int srvId) {
        Map<RunHistKey, RunHistCompacted> histCache = new ConcurrentHashMap<>();

        long maxBuildAgeMs = Duration.ofDays(TcBotConst.HISTORY_MAX_DAYS).toMillis();
        int successStatusStrId = c.getStringId(TestOccurrence.STATUS_SUCCESS);
        for (FatBuildCompacted build : builds.values()) {
            if (!build.isFinished(c))
                continue;

            if (build.getStartDateTs() > 0
                    && build.getStartDateTs() < System.currentTimeMillis() - maxBuildAgeMs)
                continue;

            build.getAllTests().forEach(testCompacted -> {
                RunHistKey histKey = new RunHistKey(srvId, testCompacted.testName(), build.branchName());

                final RunHistCompacted hist = histCache.computeIfAbsent(histKey, RunHistCompacted::new);

                Invocation inv = TestCompactedV2.toInvocation(testCompacted, build, successStatusStrId);

                hist.addInvocation(inv);
            });
        }

        resHistCache.putAll(histCache);
    }
}
