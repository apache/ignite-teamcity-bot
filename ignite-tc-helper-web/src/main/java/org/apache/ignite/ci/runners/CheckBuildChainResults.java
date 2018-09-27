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

package org.apache.ignite.ci.runners;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.util.concurrent.MoreExecutors;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.mode.LatestRebuildMode;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.mode.ProcessLogsMode;
import org.apache.ignite.ci.db.Ignite1Init;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;

import static java.util.Collections.singletonList;

/**
 *
 */
@Deprecated
public class CheckBuildChainResults {
    /**
     *
     */
    private static class BuildHistory {
        Map<String, FullChainRunCtx> map = new TreeMap<>();
    }

    private static class FailuresHistory {
        int success = 0;
        int totalRun = 0;

        public void addRun(boolean ok) {
            totalRun++;
            if (ok)
                success++;
        }

        public String passRateStr() {
            return String.format("%.2f", passRate());
        }

        private double passRate() {
            return (double)(success) / totalRun;
        }
    }

    public static class BuildMetricsHistory {
        private Map<SuiteInBranch, BuildHistory> map = new TreeMap<>();
        private LinkedHashSet<SuiteInBranch> keys = new LinkedHashSet<>();
        private Map<String, FailuresHistory> failuresHistoryMap = new TreeMap<>();

        public BuildHistory history(SuiteInBranch id) {
            return map.computeIfAbsent(id, k -> {
                keys.add(k);
                return new BuildHistory();
            });
        }

        public Set<SuiteInBranch> builds() {
            return keys;
        }

        public TreeSet<String> dates() {
            Stream<String> stream = map.values().stream().flatMap(v -> v.map.keySet().stream());
            TreeSet<String> dates = new TreeSet<>();
            stream.forEach(dates::add);
            return dates;
        }

        public FullChainRunCtx build(SuiteInBranch next, String date) {
            BuildHistory hist = map.get(next);
            if (hist == null)
                return null;
            return hist.map.get(date);
        }

        public void addSuiteResult(String suiteName, boolean ok) {
            failuresHistoryMap.computeIfAbsent(suiteName, k -> new FailuresHistory())
                .addRun(ok);
        }
    }

    public static void main(String[] args) throws Exception {
        Ignite ignite = new Ignite1Init().startIgnite();
        BuildMetricsHistory history;
        try {
            history = new BuildMetricsHistory();


            ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "apache");
            collectHistory(history, teamcity, "Ignite20Tests_RunAll", "pull/2296/head");

            collectHistory(history, teamcity, "Ignite20Tests_RunAll", "ignite-2.2");

        } finally {
            TcHelperDb.stop(ignite);
        }

        printTable(history);

        history.failuresHistoryMap.forEach(
            (k, v) -> {
                if (v.passRate() < 0.2)
                    System.out.println(k + " " + v.passRateStr());
            }
        );
    }

    private static void printTable(BuildMetricsHistory history) throws ParseException {
        System.out.print("Date\t");
        for (SuiteInBranch next : history.builds()) {
            System.out.print(next.id + "\t" + next.branch + "\t \t \t \t");
        }
        System.out.print("\n");

        for (String date : history.dates()) {
            Date mddd = new SimpleDateFormat("yyyyMMdd").parse(date);
            String dispDate = new SimpleDateFormat("dd.MM.yyyy").format(mddd);
            System.out.print(dispDate + "\t");
            for (SuiteInBranch next : history.builds()) {
                FullChainRunCtx suiteCtx = history.build(next, date);

                System.out.print(
                    (suiteCtx == null ? " " : suiteCtx.buildProblems()) + "\t"
                        + (suiteCtx == null ? " " : suiteCtx.failedTests()) + "\t"
                        + (suiteCtx == null ? " " : suiteCtx.mutedTests()) + "\t"
                        + (suiteCtx == null ? " " : suiteCtx.totalTests()) + "\t"
                        + " \t");
            }

            System.out.print("\n");
        }
        System.out.println();
    }

    public static void collectHistory(BuildMetricsHistory history,
        ITeamcity teamcity, String id, String branch)  {
        final SuiteInBranch branchId = new SuiteInBranch(id, branch);
        final BuildHistory suiteHist = history.history(branchId);
        final List<BuildRef> all = teamcity.getFinishedBuildsIncludeSnDepFailed(id, branch);
        final List<Build> fullBuildInfoList = all.stream()
            .map(b -> teamcity.getBuild(b.href))
            .filter(Objects::nonNull)
            .filter(b -> b.getId() != null)
            .collect(Collectors.toList());

        for (Build next : fullBuildInfoList) {
            Date parse = next.getFinishDate();
            String dateForMap = new SimpleDateFormat("yyyyMMdd").format(parse);
            suiteHist.map.computeIfAbsent(dateForMap, k -> {
                FullChainRunCtx ctx = BuildChainProcessor.loadFullChainContext(teamcity,
                    singletonList(next),
                    LatestRebuildMode.NONE,
                    ProcessLogsMode.DISABLED, false, null,
                        ITeamcity.DEFAULT, MoreExecutors.newDirectExecutorService());
                if (ctx == null)
                    return null;

                for (MultBuildRunCtx suite : ctx.suites()) {
                    boolean suiteOk = suite.failedTests() == 0 && !suite.hasNontestBuildProblem();
                    history.addSuiteResult(teamcity.serverId() + "\t" + suite.suiteName(), suiteOk);
                }
                return ctx;
            });
        }
    }

}
