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

package org.apache.ignite.tcbot.integration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Shared black-box integration environment for the whole Gradle integrationTest JVM.
 */
public class IntegrationTestEnvironment {
    /** */
    private static IntegrationTestEnvironment env;

    /** */
    public final int githubPort;

    /** */
    public final int jiraPort;

    /** */
    public final int teamcityPort;

    /** */
    public final int botPort;

    /** */
    public final String githubUrl;

    /** */
    public final String jiraUrl;

    /** */
    public final String teamcityUrl;

    /** */
    public final String botUrl;

    /** */
    private final List<Process> emulators = new ArrayList<>();

    /** */
    private Process bot;

    /** */
    private IntegrationTestEnvironment(int githubPort, int jiraPort, int teamcityPort, int botPort) {
        this.githubPort = githubPort;
        this.jiraPort = jiraPort;
        this.teamcityPort = teamcityPort;
        this.botPort = botPort;
        githubUrl = "http://127.0.0.1:" + githubPort;
        jiraUrl = "http://127.0.0.1:" + jiraPort;
        teamcityUrl = "http://127.0.0.1:" + teamcityPort;
        botUrl = "http://127.0.0.1:" + botPort;
    }

    /** */
    public static synchronized IntegrationTestEnvironment get() throws Exception {
        if (env == null) {
            env = new IntegrationTestEnvironment(
                configuredPort("tcbot.integration.github.port"),
                configuredPort("tcbot.integration.jira.port"),
                configuredPort("tcbot.integration.teamcity.port"),
                configuredPort("tcbot.integration.bot.port")
            );

            env.start();
            Runtime.getRuntime().addShutdownHook(new Thread(env::stop, "integration-test-environment-stop"));
        }

        return env;
    }

    /** */
    public void resetEmulators() throws IOException {
        request("POST", githubUrl + "/__test__/github/reset", null, "application/json", "{}");
        request("POST", jiraUrl + "/__test__/jira/reset", null, "application/json", "{}");
        request("POST", teamcityUrl + "/__test__/teamcity/reset", null, "application/json", "{}");
    }

    /** */
    public String login() throws IOException {
        return login("ignite.tester", "ignite-password");
    }

    /** */
    public String login(String username, String password) throws IOException {
        HttpResponse login = request("POST", botUrl + "/rest/login/login", null,
            "application/x-www-form-urlencoded", "uname=" + username + "&psw=" + password);

        if (login.status != 200 || !login.body.contains("fullToken"))
            throw new IllegalStateException("Failed to login into emulated bot: " + login.status + " " + login.body);

        return jsonField(login.body, "fullToken");
    }

    /** */
    public String basicAuth() {
        return basicAuth("ignite.tester", "ignite-password");
    }

    /** */
    public String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    /** */
    public static HttpResponse request(String method, String url, String authorization, String contentType, String body)
        throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();

        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept", "*/*");

        if (authorization != null)
            conn.setRequestProperty("Authorization", authorization);

        if (body != null) {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);

            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setFixedLengthStreamingMode(payload.length);
            conn.getOutputStream().write(payload);
        }

        int status = conn.getResponseCode();
        byte[] response = status >= 400
            ? (conn.getErrorStream() == null ? new byte[0] : conn.getErrorStream().readAllBytes())
            : conn.getInputStream().readAllBytes();

        return new HttpResponse(status, new String(response, StandardCharsets.UTF_8));
    }

    /** */
    private void start() throws Exception {
        Path root = projectRoot();
        Path pythonDir = root.resolve("tcbot-integration-tests/src/integrationTest/python");
        Path launcherHome = root.resolve("jetty-launcher/build/install/jetty-launcher");
        Path workDir = root.resolve("tcbot-integration-tests/build/e2e-shared-bot/" + UUID.randomUUID());

        emulators.add(startEmulator("github-it", pythonDir.resolve("github_emulator.py"), githubPort));
        emulators.add(startEmulator("jira-it", pythonDir.resolve("jira_emulator.py"), jiraPort));
        emulators.add(startEmulator("teamcity-it", pythonDir.resolve("teamcity_emulator.py"), teamcityPort));

        startBot(root, launcherHome, workDir);
        waitForBotReady();
    }

    /** */
    private Process startEmulator(String name, Path script, int port) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("python", script.toString(), "--port", String.valueOf(port));

        pb.redirectErrorStream(true);

        Process emulator = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(emulator.getInputStream(),
            StandardCharsets.UTF_8));
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();

        while (System.nanoTime() < deadline) {
            if (!emulator.isAlive())
                throw new IllegalStateException("Emulator exited with code " + emulator.exitValue());

            if (reader.ready()) {
                String line = reader.readLine();

                System.out.println("[" + name + "] " + line);

                if (line.startsWith("READY ")) {
                    stream(name, reader);

                    return emulator;
                }
            }

            Thread.sleep(50);
        }

        throw new IllegalStateException("Timed out waiting for " + name);
    }

    /** */
    private void startBot(Path root, Path launcherHome, Path workDir) throws IOException {
        Files.createDirectories(workDir);
        Files.createDirectories(workDir.resolve("diagnostic"));
        Files.createDirectories(workDir.resolve("tcbot_logs"));
        writeBranches(root, workDir);

        Path java = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        Path binDir = launcherHome.resolve("bin");
        List<String> cmd = new ArrayList<>();

        cmd.add(java.toString());
        cmd.addAll(botJvmArgs(workDir));
        cmd.add("-cp");
        cmd.add(launcherHome.resolve("lib").toString() + File.separator + "*");
        cmd.add("org.apache.ignite.ci.TcHelperJettyLauncher");

        ProcessBuilder pb = new ProcessBuilder(cmd);

        pb.directory(binDir.toFile());
        pb.redirectErrorStream(true);

        bot = pb.start();

        stream("bot-it", new BufferedReader(new InputStreamReader(bot.getInputStream(), StandardCharsets.UTF_8)));
    }

    /** */
    private void writeBranches(Path root, Path workDir) throws IOException {
        Path src = root.resolve("tcbot-integration-tests/src/integrationTest/resources/branches.json");
        String branches = Files.readString(src, StandardCharsets.UTF_8)
            .replace("127.0.0.1:8011", "127.0.0.1:" + githubPort)
            .replace("127.0.0.1:8012", "127.0.0.1:" + jiraPort)
            .replace("127.0.0.1:8013", "127.0.0.1:" + teamcityPort)
            .replace("@GITHUB_PORT@", Integer.toString(githubPort))
            .replace("@JIRA_PORT@", Integer.toString(jiraPort))
            .replace("@TEAMCITY_PORT@", Integer.toString(teamcityPort));

        Files.writeString(workDir.resolve("branches.json"), branches, StandardCharsets.UTF_8);
    }

    /** */
    private List<String> botJvmArgs(Path workDir) throws IOException {
        return Arrays.asList(
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-Xmx768m",
            "-Dfile.encoding=UTF-8",
            "-Djava.net.preferIPv4Stack=true",
            "-Dteamcity.bot.regionsize=1",
            "-Dhttp.maxConnections=30",
            "-Dteamcity.helper.home=" + workDir,
            "-Dtcbot.http.port=" + botPort,
            "-Dtcbot.profile=integration-test",
            "-Dtcbot.ignite.discovery.port=" + freePort(),
            "-Dtcbot.ignite.inMemory=true",
            "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
            "--add-exports=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED",
            "--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
            "--add-exports=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/java.time=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
        );
    }

    /** */
    private void waitForBotReady() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();

        while (System.nanoTime() < deadline) {
            try {
                HttpResponse response = request("GET", botUrl + "/rest/branches/ready", null, null, null);

                if (response.status == 200 && response.body.contains("true"))
                    return;
            }
            catch (IOException ignored) {
                // Bot is still opening the port.
            }

            if (!bot.isAlive())
                throw new IllegalStateException("Bot exited with code " + bot.exitValue());

            Thread.sleep(1000);
        }

        throw new IllegalStateException("Timed out waiting for bot readiness at " + botUrl);
    }

    /** */
    private void stop() {
        stop(bot);
        emulators.forEach(IntegrationTestEnvironment::stop);
    }

    /** */
    private static String jsonField(String json, String field) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"")
            .matcher(json);

        return matcher.find() ? matcher.group(1) : "";
    }

    /** */
    private static void stream(String name, BufferedReader reader) {
        Thread thread = new Thread(() -> {
            try {
                String line;

                while ((line = reader.readLine()) != null)
                    System.out.println("[" + name + "] " + line);
            }
            catch (IOException ignored) {
                // Process output is expected to close on shutdown.
            }
        }, name + "-output");

        thread.setDaemon(true);
        thread.start();
    }

    /** */
    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();

        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle")))
                return dir;

            dir = dir.getParent();
        }

        throw new IllegalStateException("Unable to locate project root");
    }

    /** */
    private static int configuredPort(String property) throws IOException {
        int port = Integer.getInteger(property, 0);

        return port > 0 ? port : freePort();
    }

    /** */
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** */
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /** */
    private static void stop(Process proc) {
        if (proc == null || !proc.isAlive())
            return;

        proc.destroy();

        try {
            if (!proc.waitFor(5, TimeUnit.SECONDS))
                proc.destroyForcibly();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
        }
    }

    /** */
    public static class HttpResponse {
        /** */
        public final int status;

        /** */
        public final String body;

        /** */
        private HttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
