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
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.LatestRebuildMode;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;

import static java.util.Collections.singletonList;

/**
 * Created by dpavlov on 03.08.2017
 */
public class CheckBuildChainResults {

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

        Ignite ignite = TcHelperDb.start();
        BuildMetricsHistory history;
        try {
            history = new BuildMetricsHistory();


            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
                collectHistory(history, teamcity, "Ignite20Tests_RunAll", "pull/2296/head");
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
                collectHistory(history, teamcity, "id8xIgniteGridGainTests_RunAll", "ignite-2.1.3");
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
                collectHistory(history, teamcity, "Ignite20Tests_RunAll", "refs/heads/master");
            }
            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
                collectHistory(history, teamcity, "id8xIgniteGridGainTests_RunAll", "refs/heads/master");
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
                collectHistory(history, teamcity, "Ignite20Tests_RunAll", "pull/2400/head");
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
                collectHistory(history, teamcity, "Ignite20Tests_RunAll", "ignite-2.2");
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
                collectHistory(history, teamcity, "Ignite20Tests_RunAll", "pull/2380/head");
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
                collectHistory(history, teamcity, "id8xIgniteGridGainTests_RunAll", "ignite-2.1.4");
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
                collectHistory(history, teamcity, "Ignite20Tests_RunAll", "pull/2508/head");
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
                collectHistory(history, teamcity, "id8xIgniteGridGainTests_RunAll", "ignite-2.1.5");
            }
        }
        finally {
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
            .map(b -> teamcity.getBuildResults(b.href))
            .filter(Objects::nonNull)
            .filter(b -> b.getId() != null)
            .collect(Collectors.toList());

        for (Build next : fullBuildInfoList) {
            Date parse = next.getFinishDate();
            String dateForMap = new SimpleDateFormat("yyyyMMdd").format(parse);
            suiteHist.map.computeIfAbsent(dateForMap, k -> {
                FullChainRunCtx ctx = BuildChainProcessor.loadChainsContext(teamcity,
                    singletonList(next),
                    LatestRebuildMode.NONE, false, null, false);
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
