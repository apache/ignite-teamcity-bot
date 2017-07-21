import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Properties;

/**
 * Created by Дмитрий on 20.07.2017
 */
public class DownloadBuildLogTest {
    public static void main(String[] args) throws Exception {
        String s = ".ignite-teamcity-helper";
        String property = System.getProperty("user.home");
        File workDir = new File(property, s);
        File file = new File(workDir, "auth.properties");
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(file)){
            properties.load(reader);
        }


        sendGet(properties);
    }

    // HTTP GET request
    private static void sendGet(Properties properties) throws Exception {
        //&archived=true
        //https://confluence.jetbrains.com/display/TCD10/REST+API
        String url = "http://ci.ignite.apache.org/downloadBuildLog.html?buildId=735562";
        String url1;
        url1 = "http://ci.ignite.apache.org/app/rest/testOccurrences?locator=build:735392";
        url1 = "http://ci.ignite.apache.org/app/rest/problemOccurrences?locator=build:735562";

        String allInvocations = "http://ci.ignite.apache.org/app/rest/testOccurrences?locator=test:(name:org.apache.ignite.internal.processors.cache.distributed.IgniteCache150ClientsTest.test150Clients),expandInvocations:true";

        String particularInvocation = "http://ci.ignite.apache.org/app/rest/testOccurrences/id:108126,build:(id:735392)";
        String searchTest = "http://ci.ignite.apache.org/app/rest/tests/id:586327933473387239";

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");


        String user = properties.getProperty("username");
        String password = properties.getProperty("password");
        String encoding =
            new String(Base64.getEncoder().encode ((user +
                ":" +
                password).getBytes()));

        con.setRequestProperty("Authorization", "Basic " + encoding);

        //add request header
       // con.setRequestProperty("User-Agent", USER_AGENT);

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + url);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
            new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
            response.append("\n");
        }
        in.close();

        //print result
        System.out.println(response.toString());

    }
}
