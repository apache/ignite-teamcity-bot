/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci.util;

import com.google.common.base.Stopwatch;
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
import java.util.concurrent.TimeUnit;
import org.apache.ignite.ci.web.rest.login.ServiceUnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Methods for sending HTTP requests
 */
public class HttpUtil {
    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);

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

    /**
     * Send GET request to the TeamCity url.
     *
     * @param basicAuthToken Authorization token.
     * @param url URL.
     * @return Input stream from connection.
     * @throws IOException If failed.
     */
    public static InputStream sendGetWithBasicAuth(String basicAuthToken, String url) throws IOException {
        final Stopwatch started = Stopwatch.createStarted();
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();

        con.setRequestProperty("Authorization", "Basic " + basicAuthToken);
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty("Keep-Alive", "header");
        con.setRequestProperty("accept-charset", StandardCharsets.UTF_8.toString());

        logger.info(Thread.currentThread().getName() + ": Required: " + started.elapsed(TimeUnit.MILLISECONDS)
            + "ms : Sending 'GET' request to : " + url);

        return getInputStream(con);
    }

    /**
     * Send GET request to the GitHub url.
     *
     * @param githubAuthToken Authorization token.
     * @param url URL.
     * @return Input stream from connection.
     * @throws IOException If failed.
     */
    public static InputStream sendGetToGit(String githubAuthToken, String url) throws IOException {
        Stopwatch started = Stopwatch.createStarted();
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();

        con.setRequestProperty("accept-charset", StandardCharsets.UTF_8.toString());
        con.setRequestProperty("Authorization", "token " + githubAuthToken);
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty("Keep-Alive", "header");

        logger.info(Thread.currentThread().getName() + ": Required: " + started.elapsed(TimeUnit.MILLISECONDS)
            + "ms : Sending 'GET' request to : " + url);

        return getInputStream(con);
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
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty("Keep-Alive", "header");
        Charset charset = StandardCharsets.UTF_8;

        con.setRequestProperty("accept-charset", charset.toString());
        con.setRequestProperty("content-type", "application/xml");

        con.setDoOutput(true);
        try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream(), charset)){
            writer.write(body); // Write POST query string (if any needed).
        }

        logger.info("\nSending 'POST' request to URL : " + url + "\n" + body);

        return getInputStream(con);
    }

    /**
     * Get input stream for successful connection. Throws exception if connection response wasn't successful.
     *
     * @param con Http connection.
     * @return Input stream from connection.
     * @throws IOException If failed.
     */
    private static InputStream getInputStream(HttpURLConnection con) throws IOException {
        int resCode = con.getResponseCode();

        // Successful responses (with code 200+).
        if (resCode / 100 == 2)
            return con.getInputStream();

        if (resCode == 401)
            throw new ServiceUnauthorizedException("Service " + con.getURL() + " returned forbidden error.");

        throw new IllegalStateException("Invalid Response Code : " + resCode + ":\n"
                + readIsToString(con.getErrorStream()));
    }

    /**
     * Send POST request to the GitHub url.
     *
     * @param githubAuthToken Authorization token.
     * @param url URL.
     * @param body Request POST params.
     * @return Response body from given url.
     * @throws IOException If failed.
     */
    public static String sendPostAsStringToGit(String githubAuthToken, String url, String body) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();
        Charset charset = StandardCharsets.UTF_8;

        con.setRequestProperty("accept-charset", charset.toString());
        con.setRequestProperty("Authorization", "token " + githubAuthToken);
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty("Keep-Alive", "header");
        con.setRequestProperty("content-type", "application/json");

        con.setRequestMethod("POST");

        con.setDoOutput(true);

        try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream(), charset)){
            writer.write(body); // Write POST query string (if any needed).
        }

        logger.info("\nSending 'POST' request to URL : " + url + "\n" + body);

        try (InputStream inputStream = getInputStream(con)){
            return readIsToString(inputStream);
        }
    }
}
