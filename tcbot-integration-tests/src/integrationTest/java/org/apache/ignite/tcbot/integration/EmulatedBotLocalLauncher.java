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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.ignite.ci.web.Launcher;

/**
 * Local launcher for IDEA: starts the Python service emulators and either an in-process bot or a WAR bot.
 */
public class EmulatedBotLocalLauncher {
    /** */
    private static final int BOT_PORT = Integer.getInteger("tcbot.integration.bot.port", 5555);

    /** */
    private static final int GITHUB_PORT = Integer.getInteger("tcbot.integration.github.port", 8011);

    /** */
    private static final int JIRA_PORT = Integer.getInteger("tcbot.integration.jira.port", 8012);

    /** */
    private static final int TEAMCITY_PORT = Integer.getInteger("tcbot.integration.teamcity.port", 8013);

    /** */
    private static final int CONTROL_PORT = Integer.getInteger("tcbot.integration.control.port", 8010);

    /** */
    private static final int IGNITE_DISCOVERY_PORT =
        Integer.getInteger("tcbot.integration.ignite.discovery.port", 55433);

    /** */
    private static final boolean LIVE_STATIC = Boolean.getBoolean("tcbot.integration.liveStatic");

    /** */
    private static final boolean IN_PROCESS_BOT = Boolean.getBoolean("tcbot.integration.inProcessBot");

    /** */
    public static void main(String[] args) throws Exception {
        Path root = findProjectRoot();
        Path launcherHome = root.resolve("jetty-launcher/build/install/jetty-launcher");
        Path botWorkDir = root.resolve("tcbot-integration-tests/build/emulated-bot/work");
        Path branches = root.resolve("tcbot-integration-tests/src/integrationTest/resources/branches.json");
        Path pythonDir = root.resolve("tcbot-integration-tests/src/integrationTest/python");

        if (!IN_PROCESS_BOT)
            ensureInstallDistExists(launcherHome);

        prepareWorkDir(botWorkDir, branches);

        Map<String, ManagedEmulator> emulators = startEmulators(pythonDir);
        HttpServer control = startControlServer(emulators);
        Process bot = null;
        Launcher.StartedServer botSrv = null;

        if (IN_PROCESS_BOT)
            botSrv = startBotInProcess(root, botWorkDir);
        else
            bot = startBot(root, launcherHome, botWorkDir);

        Process botProc = bot;
        Launcher.StartedServer botServer = botSrv;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop(botProc);
            stop(botServer);
            control.stop(0);
            emulators.values().forEach(ManagedEmulator::stop);
        }, "emulated-bot-shutdown"));

        System.out.println();
        System.out.println("Emulated TC Bot is starting.");
        System.out.println("UI: http://127.0.0.1:" + BOT_PORT + "/");
        System.out.println("Login: ignite.tester");
        System.out.println("Password: ignite-password");
        System.out.println("Non-admin login: nonadmin");
        System.out.println("Non-admin password: nonadmin");
        System.out.println("Bot REST: http://127.0.0.1:" + BOT_PORT + "/rest/");
        System.out.println("Test-only hooks: http://127.0.0.1:" + BOT_PORT + "/rest/__test__/");
        System.out.println("Bot mode: " + (IN_PROCESS_BOT ? "dev Java classes" : "Gradle-built WAR"));
        System.out.println("GitHub emulator: http://127.0.0.1:" + GITHUB_PORT + "/");
        System.out.println("JIRA emulator: http://127.0.0.1:" + JIRA_PORT + "/");
        System.out.println("TeamCity emulator: http://127.0.0.1:" + TEAMCITY_PORT + "/");
        System.out.println("Emulator control REST: http://127.0.0.1:" + CONTROL_PORT + "/__test__/emulators/");
        System.out.println("Restart Python: POST http://127.0.0.1:" + CONTROL_PORT
            + "/__test__/emulators/restart?service=github|jira|teamcity|all");
        if (LIVE_STATIC) {
            System.out.println("Live static: "
                + root.resolve("ignite-tc-helper-web/src/main/webapp").toAbsolutePath());
        }
        System.out.println("Work dir: " + botWorkDir);
        System.out.println("Press Enter in this console to stop the bot and all emulators.");
        System.out.println();

        waitForStopSignalOrProcessExit(bot, botSrv, control, emulators.values());
    }

    /** */
    private static void prepareWorkDir(Path botWorkDir, Path branches) throws IOException {
        cleanDirectory(botWorkDir);

        Files.createDirectories(botWorkDir);
        Files.createDirectories(botWorkDir.resolve("diagnostic"));
        Files.createDirectories(botWorkDir.resolve("tcbot_logs"));
        String cfg = Files.readString(branches, StandardCharsets.UTF_8)
            .replace("@GITHUB_PORT@", Integer.toString(GITHUB_PORT))
            .replace("@JIRA_PORT@", Integer.toString(JIRA_PORT))
            .replace("@TEAMCITY_PORT@", Integer.toString(TEAMCITY_PORT));

        Files.writeString(botWorkDir.resolve("branches.json"), cfg, StandardCharsets.UTF_8);
    }

    /** */
    private static void cleanDirectory(Path dir) throws IOException {
        if (!Files.exists(dir))
            return;

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            /** {@inheritDoc} */
            @Override public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
                Files.delete(file);

                return java.nio.file.FileVisitResult.CONTINUE;
            }

            /** {@inheritDoc} */
            @Override public java.nio.file.FileVisitResult postVisitDirectory(Path directory, IOException exc)
                throws IOException {
                if (exc != null)
                    throw exc;

                Files.delete(directory);

                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    /** */
    private static Map<String, ManagedEmulator> startEmulators(Path pythonDir) throws IOException {
        Map<String, ManagedEmulator> emulators = new LinkedHashMap<>();

        emulators.put("github", new ManagedEmulator("github", pythonDir.resolve("github_emulator.py"), GITHUB_PORT));
        emulators.put("jira", new ManagedEmulator("jira", pythonDir.resolve("jira_emulator.py"), JIRA_PORT));
        emulators.put("teamcity",
            new ManagedEmulator("teamcity", pythonDir.resolve("teamcity_emulator.py"), TEAMCITY_PORT));

        for (ManagedEmulator emulator : emulators.values())
            emulator.start();

        return emulators;
    }

    /** */
    private static HttpServer startControlServer(Map<String, ManagedEmulator> emulators) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", CONTROL_PORT), 0);

        server.createContext("/__test__/emulators/restart", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"POST required\"}");

                return;
            }

            String service = queryParam(exchange.getRequestURI().getRawQuery(), "service");

            if ("all".equals(service)) {
                try {
                    for (ManagedEmulator emulator : emulators.values())
                        emulator.restart();

                    respond(exchange, 200, "{\"status\":\"restarted\",\"service\":\"all\"}");
                }
                catch (RuntimeException | IOException e) {
                    respond(exchange, 500, "{\"status\":\"failed\",\"service\":\"all\",\"error\":\""
                        + escapeJson(e.getMessage()) + "\"}");
                }

                return;
            }

            ManagedEmulator emulator = emulators.get(service);

            if (emulator == null) {
                respond(exchange, 404, "{\"error\":\"unknown emulator\",\"service\":\"" + escapeJson(service)
                    + "\"}");

                return;
            }

            try {
                emulator.restart();
                respond(exchange, 200, "{\"status\":\"restarted\",\"service\":\"" + service + "\",\"port\":"
                    + emulator.port + "}");
            }
            catch (RuntimeException | IOException e) {
                respond(exchange, 500, "{\"status\":\"failed\",\"service\":\"" + service + "\",\"error\":\""
                    + escapeJson(e.getMessage()) + "\"}");
            }
        });

        server.createContext("/__test__/emulators/status", exchange -> {
            StringBuilder body = new StringBuilder("{\"emulators\":{");
            boolean first = true;

            for (Map.Entry<String, ManagedEmulator> entry : emulators.entrySet()) {
                if (!first)
                    body.append(',');

                ManagedEmulator emulator = entry.getValue();
                body.append('"').append(entry.getKey()).append("\":{\"port\":").append(emulator.port)
                    .append(",\"alive\":").append(emulator.isAlive()).append('}');
                first = false;
            }

            body.append("}}");
            respond(exchange, 200, body.toString());
        });

        server.start();

        return server;
    }

    /** */
    private static Process startBot(Path root, Path launcherHome, Path botWorkDir) throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        Path binDir = launcherHome.resolve("bin");

        List<String> cmd = new ArrayList<>();

        cmd.add(java.toString());
        cmd.addAll(localJvmArgs(root, botWorkDir));
        cmd.add("-cp");
        cmd.add(launcherHome.resolve("lib").toString() + File.separator + "*");
        cmd.add("org.apache.ignite.ci.TcHelperJettyLauncher");

        ProcessBuilder pb = new ProcessBuilder(cmd);

        pb.directory(binDir.toFile());
        pb.redirectErrorStream(true);

        Process proc = pb.start();

        stream("bot", new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8)));

        return proc;
    }

    /** */
    private static Launcher.StartedServer startBotInProcess(Path root, Path botWorkDir) throws Exception {
        applyBotProperties(root, botWorkDir);

        return Launcher.startServer(true, false);
    }

    /** */
    private static List<String> localJvmArgs(Path root, Path botWorkDir) {
        List<String> args = new ArrayList<>(Arrays.asList(
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-Xmx768m",
            "-Dfile.encoding=UTF-8",
            "-Djava.net.preferIPv4Stack=true",
            "-DIGNITE_QUIET=false",
            "-Dteamcity.bot.ignite.metricsLogFrequencyMs=60000",
            "-Dteamcity.bot.log.totalSizeCap=10GB",
            "-Dteamcity.bot.regionsize=1",
            "-Dhttp.maxConnections=30",
            "-Dteamcity.helper.home=" + botWorkDir,
            "-Dtcbot.http.port=" + BOT_PORT,
            "-Dtcbot.profile=integration-test",
            "-Dtcbot.ignite.discovery.port=" + IGNITE_DISCOVERY_PORT,
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
        ));

        if (LIVE_STATIC) {
            args.add("-Dtcbot.static.root=" + root.resolve("ignite-tc-helper-web/src/main/webapp")
                .toAbsolutePath().normalize());
        }

        return args;
    }

    /** */
    private static void applyBotProperties(Path root, Path botWorkDir) {
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("teamcity.bot.regionsize", "1");
        System.setProperty("http.maxConnections", "30");
        System.setProperty("teamcity.helper.home", botWorkDir.toString());
        System.setProperty(Launcher.HTTP_PORT_PROPERTY, Integer.toString(BOT_PORT));
        System.setProperty("tcbot.profile", "integration-test");
        System.setProperty("tcbot.ignite.discovery.port", Integer.toString(IGNITE_DISCOVERY_PORT));
        System.setProperty("tcbot.ignite.inMemory", "true");

        if (LIVE_STATIC) {
            System.setProperty("tcbot.static.root", root.resolve("ignite-tc-helper-web/src/main/webapp")
                .toAbsolutePath().normalize().toString());
        }
    }

    /** */
    private static void waitForStopSignalOrProcessExit(Process bot, Launcher.StartedServer botSrv, HttpServer control,
        Collection<ManagedEmulator> emulators) throws InterruptedException {
        CountDownLatch stopRequested = new CountDownLatch(1);

        Thread stdin = new Thread(() -> {
            try {
                System.in.read();
                System.out.println("Stop requested from standard input.");
            }
            catch (IOException e) {
                System.out.println("Standard input closed: " + e.getMessage());
            }
            finally {
                stopRequested.countDown();
            }
        }, "emulated-bot-stdin-stop");

        stdin.setDaemon(true);
        stdin.start();

        while (isBotAlive(bot, botSrv) && emulators.stream().allMatch(ManagedEmulator::isAlive)) {
            if (stopRequested.await(1, TimeUnit.SECONDS))
                break;
        }

        if (bot != null && !bot.isAlive())
            System.out.println("Bot process exited with code " + bot.exitValue());
        else if (botSrv != null && !botSrv.isRunning())
            System.out.println("Bot server stopped.");

        for (ManagedEmulator emulator : emulators) {
            if (!emulator.isAlive())
                System.out.println("Emulator " + emulator.name + " process exited.");
        }

        stop(bot);
        stop(botSrv);
        control.stop(0);
        emulators.forEach(ManagedEmulator::stop);
    }

    /** */
    private static boolean isBotAlive(Process bot, Launcher.StartedServer botSrv) {
        if (bot != null)
            return bot.isAlive();

        return botSrv == null || botSrv.isRunning();
    }

    /** */
    private static void stream(String name, BufferedReader reader) {
        Thread thread = new Thread(() -> {
            try {
                String line;

                while ((line = reader.readLine()) != null)
                    System.out.println("[" + name + "] " + line);
            }
            catch (IOException e) {
                System.out.println("[" + name + "] output stream closed: " + e.getMessage());
            }
        }, name + "-output");

        thread.setDaemon(true);
        thread.start();
    }

    /** */
    private static void ensureInstallDistExists(Path launcherHome) {
        if (!Files.isDirectory(launcherHome.resolve("lib")) || !Files.isDirectory(launcherHome.resolve("war"))) {
            throw new IllegalStateException("Launcher distribution is not prepared: " + launcherHome
                + ". Run :jetty-launcher:installDist or use the :tcbot-integration-tests:runEmulatedTcBotWar "
                + "Gradle task.");
        }
    }

    /** */
    private static Path findProjectRoot() {
        Path dir = Path.of("").toAbsolutePath();

        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle"))
                && Files.isDirectory(dir.resolve("tcbot-integration-tests"))) {
                return dir;
            }

            dir = dir.getParent();
        }

        throw new IllegalStateException("Unable to find project root from " + Path.of("").toAbsolutePath());
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
    private static void stop(Launcher.StartedServer srv) {
        if (srv != null && srv.isRunning())
            srv.close();
    }

    /** */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** */
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /** */
    private static String queryParam(String query, String name) {
        if (query == null)
            return null;

        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            String key = idx < 0 ? part : part.substring(0, idx);

            if (!name.equals(URLDecoder.decode(key, StandardCharsets.UTF_8)))
                continue;

            return idx < 0 ? "" : URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
        }

        return null;
    }

    /** */
    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** */
    private static String escapeJson(String val) {
        if (val == null)
            return "";

        return val.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** */
    private static class ManagedEmulator {
        /** */
        private final String name;

        /** */
        private final Path script;

        /** */
        private final int port;

        /** */
        private Process proc;

        /** */
        private ManagedEmulator(String name, Path script, int port) {
            this.name = name;
            this.script = script;
            this.port = port;
        }

        /** */
        private synchronized void start() throws IOException {
            String python = System.getProperty("tcbot.integration.python", "python");
            ProcessBuilder pb = new ProcessBuilder(python, script.toString(), "--port", String.valueOf(port));

            pb.redirectErrorStream(true);

            proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(),
                StandardCharsets.UTF_8));
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();

            while (System.nanoTime() < deadline) {
                if (!proc.isAlive())
                    throw new IllegalStateException("Emulator " + name + " exited with code " + proc.exitValue());

                if (reader.ready()) {
                    String line = reader.readLine();
                    System.out.println("[" + name + "] " + line);

                    if (line.startsWith("READY ")) {
                        stream(name, reader);

                        return;
                    }
                }

                sleep(50);
            }

            stop();

            throw new IllegalStateException("Timed out waiting for Python " + name + " emulator to start");
        }

        /** */
        private synchronized void restart() throws IOException {
            System.out.println("[control] Restarting Python emulator: " + name);
            stop();
            start();
        }

        /** */
        private synchronized boolean isAlive() {
            return proc != null && proc.isAlive();
        }

        /** */
        private synchronized void stop() {
            EmulatedBotLocalLauncher.stop(proc);
        }
    }
}
