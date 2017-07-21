package org.apache.ignite.ci;

import com.google.common.base.Preconditions;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Created by Дмитрий on 21.07.2017
 */
public class HttpUtil {

    public static String sendGetAsString(String basicAuthToken, String url) throws IOException {
        InputStream inputStream = sendGetWithBasicAuth(basicAuthToken, url);
        BufferedReader in = new BufferedReader(
            new InputStreamReader(inputStream));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
            response.append("\n");
        }
        in.close();
        return response.toString();
    }

    private static InputStream sendGetWithBasicAuth(String basicAuthToken, String url) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "Basic " + basicAuthToken);
        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + url);
        Preconditions.checkState(200==(responseCode),
            "Invalid Response Code : " + responseCode);

        return con.getInputStream();
    }

    public static void sendGetCopyToFile(String token, String url, File file) throws IOException {
        try (InputStream inputStream = sendGetWithBasicAuth(token, url)){
            Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
