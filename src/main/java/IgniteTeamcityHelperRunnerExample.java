import com.google.common.base.Throwables;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.apache.ignite.ci.model.BuildType;
import org.apache.ignite.ci.model.Project;
import org.apache.ignite.ci.util.HttpUtil;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.util.XmlUtil;

/**
 * Created by Дмитрий on 20.07.2017
 */
public class IgniteTeamcityHelperRunnerExample {
    public static void main(String[] args) throws Exception {
        final IgniteTeamcityHelper helper = new IgniteTeamcityHelper("private"); //public_auth_properties

        for (int i = 0; i < 0; i++) {
            //branch example: "pull/2335/head"
            String branchName = "refs/heads/master";
            helper.triggerBuild("Ignite20Tests_IgniteCache5", branchName);
        }

        int j = 0;
        if (j > 0) {
            List<CompletableFuture<File>> fileFutList = helper.standardProcessLogs(742081);
            List<File> collect = getFuturesResults(fileFutList);
            for (File next : collect) {
                System.out.println("Cached locally: [" + next.getCanonicalPath()
                    + "], " + next.toURI().toURL());
            }

        }

        sendGet(helper.host(), helper.basicAuthToken());

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

        String projects = host + "app/rest/latest/projects/id8xIgniteGridGainTests";

        String response = HttpUtil.sendGetAsString(basicAuthTok, projects);

        System.out.println(response);
        Project load = XmlUtil.load(response, Project.class);

        //print result
        System.out.println(load);

        for(BuildType bt: load.getBuildTypes()) {
            System.err.println(bt.getName());
        }

    }

}
