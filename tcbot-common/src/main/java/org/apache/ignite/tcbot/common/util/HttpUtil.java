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

package org.apache.ignite.tcbot.common.util;

import com.google.common.base.Stopwatch;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
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
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.ignite.tcbot.common.exeption.ServiceUnauthorizedException;
import org.apache.ignite.tcbot.common.exeption.ServiceBadRequestException;
import org.apache.ignite.tcbot.common.exeption.ServiceConflictException;
import org.apache.ignite.tcbot.common.exeption.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Methods for sending HTTP requests
 */
public class HttpUtil {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);

    /**
     * @param inputStream Input stream.
     */
    private static String readIsToString(InputStream inputStream) throws IOException {
        if (inputStream == null)
            return "<null>";

        BufferedReader in = new BufferedReader(
            new InputStreamReader(inputStream));
        String inputLine;
        StringBuilder res = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            res.append(inputLine);
            res.append("\n");
        }
        return res.toString();
    }

    /**
     * Send GET request to the TeamCity url.
     *
     * @param basicAuthTok Authorization token.
     * @param url URL.
     * @return Input stream from connection.
     * @throws IOException If communication failed.
     * @throws FileNotFoundException If not found (404) was returned from service.
     * @throws ServiceConflictException If conflict (409) was returned from service.
     * @throws IllegalStateException if some unexpected HTTP error returned.
     */
    public static InputStream sendGetWithBasicAuth(String basicAuthTok, String url) throws IOException {
        final Stopwatch started = Stopwatch.createStarted();
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();
        con.setConnectTimeout(60000); //todo make configurable
        con.setReadTimeout(60000);

        con.setRequestProperty("Authorization", "Basic " + basicAuthTok);
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty("Keep-Alive", "header");
        con.setRequestProperty("accept-charset", StandardCharsets.UTF_8.toString());

        int resCode;

        try {
            resCode = con.getResponseCode();
        }
        catch (IOException e) {
            throw new IOException("Failed GET request [host=" + obj.getHost() + ", url=" + url + ']', e);
        }

        logger.info(Thread.currentThread().getName() + ": Required: " + started.elapsed(TimeUnit.MILLISECONDS)
            + "ms : Sending 'GET' request to : " + url + " Response: " + resCode);

        return getInputStream(con);
    }

    /**
     * Send GET request to the GitHub url.
     *
     * @param githubAuthTok Authorization OAuth token.
     * @param url URL.
     * @param rspHeaders [IN] - required codes name->null, [OUT] required codes: name->value.
     * @return Input stream from connection.
     * @throws IOException If failed.
     */
    public static InputStream sendGetToGit(String githubAuthTok, String url, @Nullable Map<String, String> rspHeaders) throws IOException {
        Stopwatch started = Stopwatch.createStarted();
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();

        if (githubAuthTok != null)
            con.setRequestProperty("Authorization", "token " + githubAuthTok);

        con.setRequestProperty("accept-charset", StandardCharsets.UTF_8.toString());
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty("Keep-Alive", "header");

        int resCode = con.getResponseCode();

        if(rspHeaders != null) {
            rspHeaders.keySet().forEach((k) -> {
                String link = con.getHeaderField(k);

                rspHeaders.put(k, link);
            });
        }
        logger.info(Thread.currentThread().getName() + ": Required: " + started.elapsed(TimeUnit.MILLISECONDS)
            + "ms : Sending 'GET' request to : " + url + " Response: " + resCode);

        return getInputStream(con, gitHubAuthDiagnostic(githubAuthTok));
    }

    /**
     * @param tok token
     * @param url full URL
     * @param file destination file to save data.

     * @throws IOException If communication failed.
     * @throws FileNotFoundException If not found (404) was returned from service.
     * @throws ServiceConflictException If conflict (409) was returned from service.
     * @throws IllegalStateException if some unexpected HTTP error returned.
     */
    public static void sendGetCopyToFile(String tok, String url, File file) throws IOException {
        try (InputStream inputStream = sendGetWithBasicAuth(tok, url)){
            Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String sendPostAsString(String basicAuthTok, String url, String body) throws IOException {
        try (InputStream inputStream = sendPostWithBasicAuth(basicAuthTok, url, body)){
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
     * @throws IOException If communication failed.
     * @throws FileNotFoundException If not found (404) was returned from service.
     * @throws ServiceConflictException If conflict (409) was returned from service.
     * @throws IllegalStateException if some unexpected HTTP error returned.
     */
    private static InputStream getInputStream(HttpURLConnection con) throws IOException {
        return getInputStream(con, null);
    }

    /**
     * Get input stream for successful connection. Throws exception if connection response wasn't successful.
     *
     * @param con Http connection.
     * @param authDiagnostic Optional safe authentication diagnostic, without secrets.
     * @return Input stream from connection.
     * @throws IOException If communication failed.
     * @throws FileNotFoundException If not found (404) was returned from service.
     * @throws ServiceConflictException If conflict (409) was returned from service.
     * @throws IllegalStateException if some unexpected HTTP error returned.
     */
    private static InputStream getInputStream(HttpURLConnection con, @Nullable String authDiagnostic) throws IOException {
        int resCode = con.getResponseCode();

        // Successful responses (with code 200+).
        if (resCode / 100 == 2)
            return con.getInputStream();

        String detailsFromResponeText = readIsToString(con.getErrorStream());
        String diagnostic = responseDiagnostic(con, authDiagnostic, detailsFromResponeText);

        if (resCode == 400)
            throw new ServiceBadRequestException(diagnostic);

        if (resCode == 401)
            throw new ServiceUnauthorizedException("Service " + con.getURL() + " returned unauthorized error:\n"
                + diagnostic);

        if (resCode == 404)
            throw new FileNotFoundException("Service " + con.getURL() + " returned not found error:\n" + diagnostic);

        if (resCode == 409)
            throw new ServiceConflictException("Service " + con.getURL() + " returned Conflict Response Code:\n"
                + diagnostic);

        if (resCode == 503)
            throw new ServiceUnavailableException("Service " + con.getURL() + " (host=" + con.getURL().getHost()
                + ") returned Service Unavailable Response Code : " + resCode + ":\n" + diagnostic,
                con.getURL().toString(), resCode, retryAfterMs(con.getHeaderField("Retry-After")));

        throw new IllegalStateException("Service " + con.getURL() + " returned Invalid Response Code : " + resCode + ":\n"
                + diagnostic);
    }

    /**
     * @param githubAuthTok GitHub token, may be {@code null}.
     */
    private static String gitHubAuthDiagnostic(@Nullable String githubAuthTok) {
        return "GitHub auth token present: " + (githubAuthTok != null && !githubAuthTok.isEmpty());
    }

    /**
     * @param con HTTP connection.
     * @param authDiagnostic Optional safe authentication diagnostic, without secrets.
     * @param responseText Error response text.
     */
    private static String responseDiagnostic(HttpURLConnection con, @Nullable String authDiagnostic,
        String responseText) {
        StringBuilder res = new StringBuilder();

        if (authDiagnostic != null)
            res.append(authDiagnostic).append('\n');

        res.append("Response URL: ").append(con.getURL()).append('\n');
        res.append("Response host: ").append(con.getURL().getHost()).append('\n');

        appendHeaderIfPresent(res, con, "WWW-Authenticate");
        appendHeaderIfPresent(res, con, "X-GitHub-Request-Id");
        appendHeaderIfPresent(res, con, "X-RateLimit-Limit");
        appendHeaderIfPresent(res, con, "X-RateLimit-Remaining");
        appendHeaderIfPresent(res, con, "X-RateLimit-Reset");
        appendHeaderIfPresent(res, con, "X-RateLimit-Used");
        appendHeaderIfPresent(res, con, "X-RateLimit-Resource");
        appendHeaderIfPresent(res, con, "Retry-After");
        appendHeaderIfPresent(res, con, "X-OAuth-Scopes");
        appendHeaderIfPresent(res, con, "X-Accepted-OAuth-Scopes");
        appendHeaderIfPresent(res, con, "X-Accepted-GitHub-Permissions");

        res.append("Response body:\n").append(responseText);

        return res.toString();
    }

    /**
     * @param res Destination.
     * @param con HTTP connection.
     * @param name Header name.
     */
    private static void appendHeaderIfPresent(StringBuilder res, HttpURLConnection con, String name) {
        String val = con.getHeaderField(name);

        if (val != null)
            res.append(name).append(": ").append(val).append('\n');
    }

    /**
     * @param retryAfter Retry-After header value.
     */
    private static long retryAfterMs(@Nullable String retryAfter) {
        if (retryAfter == null || retryAfter.trim().isEmpty())
            return -1;

        String trimmed = retryAfter.trim();

        try {
            return TimeUnit.SECONDS.toMillis(Long.parseLong(trimmed));
        }
        catch (NumberFormatException ignored) {
            // Retry-After also allows HTTP-date.
        }

        try {
            Duration delay = Duration.between(ZonedDateTime.now(),
                ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME));

            return Math.max(0, delay.toMillis());
        }
        catch (DateTimeParseException ignored) {
            return -1;
        }
    }

    /**
     * Send POST request to the GitHub url.
     *
     * @param githubAuthTok Authorization token.
     * @param url URL.
     * @param body Request POST params.
     * @return Response body from given url.
     * @throws IOException If failed.
     */
    public static String sendPostAsStringToGit(String githubAuthTok, String url, String body) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();
        Charset charset = StandardCharsets.UTF_8;

        con.setRequestProperty("accept-charset", charset.toString());
        con.setRequestProperty("Authorization", "token " + githubAuthTok);
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty("Keep-Alive", "header");
        con.setRequestProperty("content-type", "application/json");

        con.setRequestMethod("POST");

        con.setDoOutput(true);

        try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream(), charset)){
            writer.write(body); // Write POST query string (if any needed).
        }

        logger.info("\nSending 'POST' request to URL : " + url + "\n" + body);

        try (InputStream inputStream = getInputStream(con, gitHubAuthDiagnostic(githubAuthTok))){
            return readIsToString(inputStream);
        }
    }

    /**
     * Send POST request to the JIRA url.
     *
     * @param jiraAuthTok Authorization Base64 token.
     * @param url URL.
     * @param body Request POST params.
     * @return Response body from given url.
     * @throws IOException If failed.
     */
    public static String sendPostAsStringToJira(String jiraAuthTok, String url, String body) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();
        Charset charset = StandardCharsets.UTF_8;

        con.setRequestProperty("accept-charset", charset.toString());
        con.setRequestProperty("Authorization", "Basic " + jiraAuthTok);
        con.setRequestProperty("content-type", "application/json");
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty("Keep-Alive", "header");

        con.setRequestMethod("POST");

        con.setDoOutput(true);

        try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream(), charset)){
            writer.write(body); // Write POST query string (if any needed).
        }

        logger.info("\nSending 'POST' request to URL : " + url + "\n" + body);

        try (InputStream inputStream = getInputStream(con)) {
            return readIsToString(inputStream);
        }
    }

    /**
     * Send GET request to the JIRA url.
     *
     * @param jiraAuthTok Jira auth token.
     * @param url Url.
     */
    public static String sendGetToJira(String jiraAuthTok, String url) throws IOException {
        Stopwatch started = Stopwatch.createStarted();
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)obj.openConnection();
        Charset charset = StandardCharsets.UTF_8;

        con.setRequestProperty("accept-charset", charset.toString());
        con.setRequestProperty("Authorization", "Basic " + jiraAuthTok);
        con.setRequestProperty("content-type", "application/json");
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty("Keep-Alive", "header");

        con.setRequestMethod("GET");

        int resCode = con.getResponseCode();

        logger.info(Thread.currentThread().getName() + ": Required: " + started.elapsed(TimeUnit.MILLISECONDS)
            + "ms : Sending 'GET' request to : " + url + " Response: " + resCode);

        try (InputStream inputStream = getInputStream(con)) {
            return readIsToString(inputStream);
        }
    }
}
