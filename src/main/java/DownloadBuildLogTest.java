import java.io.File;
import java.util.Properties;
import org.apache.ignite.ci.DownloadBuildLog;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.HttpUtil;

import static org.apache.ignite.ci.HelperConfig.ensureDirExist;

/**
 * Created by Дмитрий on 20.07.2017
 */
public class DownloadBuildLogTest {
    public static void main(String[] args) throws Exception {
        File workDir = HelperConfig.resolveWorkDir();
        Properties properties = HelperConfig.loadAuthProperties(workDir);

        String basicAuthToken = HelperConfig.prepareBasicHttpAuthToken(properties);

        File logsDir = ensureDirExist(new File(workDir, "logs"));

        int buildId = 736822;
        DownloadBuildLog buildLog = new DownloadBuildLog(buildId, basicAuthToken, logsDir);
        File run = buildLog.run();
        System.out.println("Cached locally: [" + run.getCanonicalPath()
            + "], " + run.toURI());

    }

    // HTTP GET request
    private static void sendGet(String basicAuthToken) throws Exception {
        //&archived=true
        //https://confluence.jetbrains.com/display/TCD10/REST+API
        String url = "http://ci.ignite.apache.org/downloadBuildLog.html?buildId=735562";
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
