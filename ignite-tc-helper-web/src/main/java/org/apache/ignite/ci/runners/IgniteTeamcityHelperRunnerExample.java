package org.apache.ignite.ci.runners;

import com.google.common.base.Throwables;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.tcmodel.conf.BuildType;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildTypeFull;
import org.apache.ignite.ci.tcmodel.conf.bt.SnapshotDependency;

/**
 * Created by Дмитрий on 20.07.2017
 *
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public class IgniteTeamcityHelperRunnerExample {
    public static void main(String[] args) throws Exception {
        String serverIdPriv = "private";
        String serverIdPub = "public";
        final IgniteTeamcityHelper helper = new IgniteTeamcityHelper(serverIdPub); //public_auth_properties

        int k = 0;
        if (k > 0) {
            //branch example: "pull/2335/head"
            String branchNameForHist = "pull/2296/head";
            List<BuildType> buildTypes = helper.getProjectSuites("Ignite20Tests").get();
            for (BuildType bt : buildTypes) {
                System.err.println(bt.getId());

                if (bt.getName().toLowerCase().contains("pds")
                    // || bt.getName().toLowerCase().contains("cache")
                    ) {
                    int[] ints = helper.getBuildNumbersFromHistory(bt.getName(), branchNameForHist);

                    List<CompletableFuture<File>> fileFutList = helper.standardProcessLogs(ints);
                    List<File> collect = getFuturesResults(fileFutList);
                    for (File logfile : collect) {
                        System.out.println("Cached locally: [" + logfile.getCanonicalPath()
                            + "], " + logfile.toURI().toURL());
                    }
                }
            }
        }

        int b = 0;
        if (b > 0)
            checkBuildTypes(helper);

        for (int i = 0; i < 0; i++) {
            //branch example:
            final String branchName = "<default>";
            // String branchName = "refs/heads/master";
            // final String branchName = "pull/3554/head";
            String buildTypeIdAll = "IgniteTests24Java8_RunAll";
            String buildTypeIdAllP = "id8xIgniteGridGainTestsJava8_RunAll";
            String buildTypeIdRe = "IgniteTests24Java8_IgniteReproducingSuite";
            String buildTypeId = "IgniteTests24Java8_LicensesJavadoc";
            String dotNetLongRun = "IgniteTests24Java8_IgnitePlatformNetLongRunning";
            helper.triggerBuild(buildTypeIdAll, branchName, true);
        }

        int j = 1;
        if (j > 0) {
            List<CompletableFuture<File>> fileFutList = helper.standardProcessLogs(1155133);
            List<File> collect = getFuturesResults(fileFutList);
            for (File next : collect) {
                System.out.println("Cached locally: [" + next.getCanonicalPath() + "], " + next.toURI().toURL());
            }
        }

        int h = 0;
        if (h > 0) {
            String branchName1 = "<default>";
            final String branchName = "pull/3475/head";
            List<CompletableFuture<File>> futures = helper.standardProcessAllBuildHistory(
                "IgniteTests24Java8_IgnitePds2DirectIo",
                branchName);

            List<File> collect = getFuturesResults(futures);
            for (File next : collect) {
                System.out.println("Cached locally: [" + next.getCanonicalPath() + "], " + next.toURI().toURL());
            }
        }

        //sendGet(helper.host(), helper.basicAuthToken());

    }

    private static void checkBuildTypes(IgniteTeamcityHelper helper) throws InterruptedException, ExecutionException {
        Map<String, Set<String>> duplicates = new TreeMap<>();
        Map<String, String> suiteToBt = new TreeMap<>();
        List<BuildType> buildTypes = helper.getProjectSuites("Ignite20Tests").get();
        for (BuildType bt : buildTypes) {
            final BuildTypeFull type = helper.getBuildType(bt.getId());
            if ("Ignite20Tests_RunAll".equals(type.getId())
                || "IgniteTests_RunAllPds".equals(type.getId())
                || "Ignite20Tests_RunBasicTests".equals(type.getId())) {
                checkRunAll(type);
                continue;
            }
            checkSuite(duplicates, suiteToBt, bt, type);
        }

        suiteToBt.forEach((key, v) -> {
            System.out.println(key + "\t" + v);
        });

        if (!duplicates.isEmpty()) {
            System.err.println("********************* Duplicates **************************");
            duplicates.forEach((k, v) -> {
                System.err.println(k + "\t" + v);
            });
            ;
        }
    }

    private static void checkSuite(Map<String, Set<String>> duplicates, Map<String, String> suiteToBt, BuildType bt,
        BuildTypeFull type) {
        final String suite = type.getParameter("TEST_SUITE");

        if (suite == null)
            return;

        for (StringTokenizer strTokenizer = new StringTokenizer(suite, ",;"); strTokenizer.hasMoreTokens(); ) {
            String s = strTokenizer.nextToken();
            final String suiteJava = s.trim();

            final String btName = bt.getName();
            final String oldBtName = suiteToBt.put(suiteJava, btName);
            if (oldBtName != null) {
                System.err.println(suite + " for " + btName
                    + " and for " + oldBtName);

                final Set<String> duplicatesSet = duplicates.computeIfAbsent(suiteJava, k -> new TreeSet<>());
                duplicatesSet.add(btName);
                duplicatesSet.add(oldBtName);
            }
        }
    }

    private static void checkRunAll(BuildTypeFull type) {
        System.err.println(type);
        final List<SnapshotDependency> dependencies = type.dependencies();
        for (Iterator<SnapshotDependency> iterator = dependencies.iterator(); iterator.hasNext(); ) {
            SnapshotDependency next = iterator.next();
            final String runBuild = next.getProperty("run-build-if-dependency-failed");
            if (!"RUN_ADD_PROBLEM".equals(runBuild)) {
                System.err.println("Incorrect configuration for dependency from [" + next.bt().getName() + "]");
            }
        }
    }

    private static <T> List<T> getFuturesResults(List<? extends Future<T>> fileFutList) {
        return fileFutList.stream().map(IgniteTeamcityHelperRunnerExample::getFutureResult).collect(Collectors.toList());
    }

    private static <T> T getFutureResult(Future<T> fut) {
        try {
            return fut.get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
        catch (ExecutionException e) {
            throw Throwables.propagate(e.getCause());
        }
    }

}
