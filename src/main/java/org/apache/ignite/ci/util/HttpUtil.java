package org.apache.ignite.ci.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Methods for sending HTTP requests
 */
public class HttpUtil {
    public static String sendGetAsString(String basicAuthToken, String url) throws IOException {
        try (InputStream inputStream = sendGetWithBasicAuth(basicAuthToken, url)){
            return readIsToString(inputStream);
        }
    }

    private static String readIsToString(InputStream inputStream) throws IOException {
        BufferedReader in = new BufferedReader(
            new InputStreamReader(inputStream));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
            response.append("\n");
        }
        return response.toString();
    }

    private static InputStream sendGetWithBasicAuth(String basicAuthToken, String url) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();

        con.setRequestProperty("Authorization", "Basic " + basicAuthToken);
        int resCode = con.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + url);
        boolean ok = 200 == (resCode);
        if (!ok) {
            throw new IllegalStateException("Invalid Response Code : " + resCode + ":\n"
                + readIsToString(con.getInputStream()));
        }

        return con.getInputStream();
    }

    public static void sendGetCopyToFile(String tok, String url, File file) throws IOException {
        try (InputStream inputStream = sendGetWithBasicAuth(tok, url)){
            Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String sendPostAsString(String basicAuthToken, String url, String body) throws IOException {
        try (InputStream inputStream = sendPostWithBasicAuth(basicAuthToken, url, body)){
            return readIsToString(inputStream);
        }
    }

    private static InputStream sendPostWithBasicAuth(String tok, String url,
        String body) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("Authorization", "Basic " + tok);
        Charset charset = StandardCharsets.UTF_8;

        con.setRequestProperty("accept-charset", charset.toString());
        con.setRequestProperty("content-type", "application/xml");

        con.setDoOutput(true);
        try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream(), charset)){
            writer.write(body); // Write POST query string (if any needed).
        }

        int resCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url + "\n" + body);
        boolean ok = 200 == (resCode);
        if (!ok) {
            throw new IllegalStateException("Invalid Response Code : " + resCode + ":\n"
                + readIsToString(con.getInputStream()));
        }

        return con.getInputStream();
    }
}
