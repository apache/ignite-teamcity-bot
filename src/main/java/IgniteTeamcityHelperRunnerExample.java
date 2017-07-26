import com.google.common.base.Throwables;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.apache.ignite.ci.util.HttpUtil;
import org.apache.ignite.ci.IgniteTeamcityHelper;

/**
 * Created by Дмитрий on 20.07.2017
 */
public class IgniteTeamcityHelperRunnerExample {
    public static void main(String[] args) throws Exception {
        final IgniteTeamcityHelper helper = new IgniteTeamcityHelper("public"); //public_auth_properties

        for (int i = 0; i < 20; i++) {
           //helper.triggerBuild("Ignite20Tests_IgniteCache5", "pull/2335/head");
        }
        //737831, 738387

        List<CompletableFuture<File>> fileFutList = helper.standardProcessLogs(740686);

        List<File> collect = getFuturesResults(fileFutList);

        for (File next : collect) {
            System.out.println("Cached locally: [" + next.getCanonicalPath()
                + "], " + next.toURI().toURL());
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

    // HTTP GET request
    private static void sendGet(String basicAuthTok) throws Exception {
        //&archived=true
        //https://confluence.jetbrains.com/display/TCD10/REST+API
        String url = "http://ci.ignite.apache.org/downloadBuildLogZip.html?buildId=735562";
        String url1;
        url1 = "http://ci.ignite.apache.org/app/rest/testOccurrences?locator=build:735392";
        url1 = "http://ci.ignite.apache.org/app/rest/problemOccurrences?locator=build:735562";

        String allInvocations = "http://ci.ignite.apache.org/app/rest/testOccurrences?locator=test:(name:org.apache.ignite.internal.processors.cache.distributed.IgniteCache150ClientsTest.test150Clients),expandInvocations:true";

        String particularInvocation = "http://ci.ignite.apache.org/app/rest/testOccurrences/id:108126,build:(id:735392)";
        String searchTest = "http://ci.ignite.apache.org/app/rest/tests/id:586327933473387239";

        String response = HttpUtil.sendGetAsString(basicAuthTok, url);

        //print result
        System.out.println(response);

    }

}
