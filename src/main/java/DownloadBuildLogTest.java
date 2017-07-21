import java.io.File;
import java.util.concurrent.CompletableFuture;
import org.apache.ignite.ci.HttpUtil;
import org.apache.ignite.ci.IgniteTeamcityHelper;

/**
 * Created by Дмитрий on 20.07.2017
 */
public class DownloadBuildLogTest {
    public static void main(String[] args) throws Exception {
        final IgniteTeamcityHelper helper = new IgniteTeamcityHelper();

        for (int i = 0; i < 1; i++) {
            helper.triggerBuild("Ignite20Tests_IgniteCache5", "pull/2335/head");
        }

        final int buildId = 734334;
        final CompletableFuture<File> future = helper.downloadBuildLogZip(buildId);
        final File log = future.get();
        System.out.println("Cached locally: [" + log.getCanonicalPath()
            + "], " + log.toURI().toURL());

    }

    // HTTP GET request
    private static void sendGet(String basicAuthToken) throws Exception {
        //&archived=true
        //https://confluence.jetbrains.com/display/TCD10/REST+API
        String url = "http://ci.ignite.apache.org/downloadBuildLogZip.html?buildId=735562";
        String url1;
        url1 = "http://ci.ignite.apache.org/app/rest/testOccurrences?locator=build:735392";
        url1 = "http://ci.ignite.apache.org/app/rest/problemOccurrences?locator=build:735562";

        String allInvocations = "http://ci.ignite.apache.org/app/rest/testOccurrences?locator=test:(name:org.apache.ignite.internal.processors.cache.distributed.IgniteCache150ClientsTest.test150Clients),expandInvocations:true";

        String particularInvocation = "http://ci.ignite.apache.org/app/rest/testOccurrences/id:108126,build:(id:735392)";
        String searchTest = "http://ci.ignite.apache.org/app/rest/tests/id:586327933473387239";

        String response = HttpUtil.sendGetAsString(basicAuthToken, url);

        //print result
        System.out.println(response);

    }

}
