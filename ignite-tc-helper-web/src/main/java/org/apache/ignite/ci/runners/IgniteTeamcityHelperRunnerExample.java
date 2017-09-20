package org.apache.ignite.ci.runners;

import com.google.common.base.Throwables;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.apache.ignite.ci.model.conf.BuildType;
import org.apache.ignite.ci.model.conf.Project;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.util.HttpUtil;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.util.XmlUtil;

/**
 * Created by Дмитрий on 20.07.2017
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
                    int[] ints = helper.getBuildNumbersFromHistory(bt, branchNameForHist);

                    List<CompletableFuture<File>> fileFutList = helper.standardProcessLogs(ints);
                    List<File> collect = getFuturesResults(fileFutList);
                    for (File logfile : collect) {
                        System.out.println("Cached locally: [" + logfile.getCanonicalPath()
                            + "], " + logfile.toURI().toURL());
                    }
                }
            }
        }

        for (int i = 0; i < 0; i++) {
            //branch example:
            final String branchName = "pull/2704/head";
            //String branchName = "refs/heads/master";
            helper.triggerBuild("Ignite20Tests_IgniteDataStrucutures", branchName);
        }

        int j = 1;
        if (j > 0) {
            List<CompletableFuture<File>> fileFutList = helper.standardProcessLogs(840602);
            List<File> collect = getFuturesResults(fileFutList);
            for (File next : collect) {
                System.out.println("Cached locally: [" + next.getCanonicalPath() + "], " + next.toURI().toURL());
            }
        }

        int h = 0;
        if (h > 0) {
            List<CompletableFuture<File>> futures = helper.standardProcessAllBuildHistory(
                "Ignite20Tests_IgniteDataStructures",
                "<default>");

            List<File> collect = getFuturesResults(futures);
            for (File next : collect) {
                System.out.println("Cached locally: [" + next.getCanonicalPath() + "], " + next.toURI().toURL());
            }
        }

        //sendGet(helper.host(), helper.basicAuthToken());

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

    // HTTP GET request
    private static void sendGet(String host, String basicAuthTok) throws Exception {
        //&archived=true
        //https://confluence.jetbrains.com/display/TCD10/REST+API
        String url1;
        url1 = "http://ci.ignite.apache.org/app/rest/testOccurrences?locator=build:735392";
        url1 = "http://ci.ignite.apache.org/app/rest/problemOccurrences?locator=build:735562";

        String allInvocations = "http://ci.ignite.apache.org/app/rest/testOccurrences?locator=test:(name:org.apache.ignite.internal.processors.cache.distributed.IgniteCache150ClientsTest.test150Clients),expandInvocations:true";

        String particularInvocation = "http://ci.ignite.apache.org/app/rest/testOccurrences/id:108126,build:(id:735392)";
        String searchTest = "http://ci.ignite.apache.org/app/rest/tests/id:586327933473387239";


        // http://ci.ignite.apache.org/app/rest/latest/builds/buildType:(id:Ignite20Tests_IgniteZooKeeper)
        String projectId = "id8xIgniteGridGainTests";
        String projects = host + "app/rest/latest/projects/" + projectId;

        String s = "http://ci.ignite.apache.org/app/rest/latest/builds?locator=buildType:Ignite20Tests_IgniteZooKeeper,branch:pull/2296/head";

        String response = HttpUtil.sendGetAsString(basicAuthTok, projects);

        System.out.println(response);
        Project load = XmlUtil.load(response, Project.class);

        //print result
        System.out.println(load);

        for(BuildType bt: load.getBuildTypesNonNull()) {
            System.err.println(bt.getName());
        }

    }

}
