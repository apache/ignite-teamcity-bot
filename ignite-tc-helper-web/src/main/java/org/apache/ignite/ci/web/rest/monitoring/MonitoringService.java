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
package org.apache.ignite.ci.web.rest.monitoring;

import com.google.common.base.Strings;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMetrics;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.tcbot.common.interceptor.AutoProfilingInterceptor;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTaskInterceptor;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.apache.ignite.tcbot.engine.conf.INotificationChannel;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.NotificationsConfig;
import org.apache.ignite.tcbot.notify.IEmailSender;
import org.apache.ignite.tcbot.notify.ISendEmailConfig;
import org.apache.ignite.tcbot.notify.ISlackSender;

import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("monitoring")
@Produces(MediaType.APPLICATION_JSON)
public class MonitoringService {
    /** Log line start. */
    private static final Pattern LOG_ENTRY_START = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\S+)\\s+.*");

    /** Service URL in log text. */
    private static final Pattern SERVICE_URL = Pattern.compile("(?:Service |Response URL: |url=)(https?://[^\\s,\\]]+)");

    /** Service host in log text. */
    private static final Pattern SERVICE_HOST = Pattern.compile("(?:host=|Response host: )([^\\s,\\]\\)]+)");

    /** HTTP response code in log text. */
    private static final Pattern RESPONSE_CODE = Pattern.compile(
        "(?:Invalid Response Code|Service Unavailable Response Code)\\s*:\\s*(\\d{3})|HTTP\\s+(\\d{3})|Response:\\s*(\\d{3})");

    /** Exception summary in log text. */
    private static final Pattern EXCEPTION_SUMMARY = Pattern.compile(
        "(?m)^(?:Caused by: )?([\\w.$]+(?:Exception|Error): .+)$");

    /** Max summary length. */
    private static final int SUMMARY_LIMIT = 240;

    /** Log timestamp format. */
    private static final DateTimeFormatter LOG_TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** Context. */
    @Context
    private ServletContext ctx;

    @GET
    @PermitAll
    @Path("tasks")
    public List<TaskResult> getTaskMonitoring() {
        MonitoredTaskInterceptor instance = CtxListener.getInjector(ctx).getInstance(MonitoredTaskInterceptor.class);

        final Collection<MonitoredTaskInterceptor.Invocation> list = instance.getList();

        return list.stream().map(invocation -> {
            final TaskResult res = new TaskResult();
            res.name = invocation.name();
            res.start = invocation.start();
            res.startTs = invocation.startTs();
            res.end = invocation.end();
            res.endTs = invocation.endTs();
            res.result = invocation.result();
            res.count = invocation.count();
            return res;
        }).collect(Collectors.toList());
    }

    @GET
    @PermitAll
    @Path("appLogSummaryLink")
    public AppLogSummaryLink getAppLogSummaryLink() {
        MonitoredTaskInterceptor instance = CtxListener.getInjector(ctx).getInstance(MonitoredTaskInterceptor.class);

        AppLogSummaryLink res = new AppLogSummaryLink();
        res.startTs = instance.startedTs();
        res.name = "Application log since startup";

        return res;
    }

    @GET
    @PermitAll
    @Path("taskLog")
    public List<AppLogEntry> getTaskLog(@QueryParam("startTs") long startTs, @QueryParam("endTs") long endTs) {
        if (startTs <= 0)
            return new ArrayList<>();

        long actualEndTs = endTs > 0 ? endTs : System.currentTimeMillis();

        if (actualEndTs < startTs)
            actualEndTs = startTs;

        File[] files = appLogFiles(startTs);

        List<AppLogEntry> res = new ArrayList<>();

        for (File file : files)
            readLogEntries(file, startTs, actualEndTs, res);

        return res;
    }

    /**
     * @param startTs Task start timestamp.
     */
    private File[] appLogFiles(long startTs) {
        File tcbotLogs = new File(TcBotWorkDir.resolveWorkDir(), "tcbot_logs");

        File[] files = tcbotLogs.listFiles(file -> file.isFile()
            && file.getName().endsWith(".log")
            && !file.getName().startsWith("monitoring")
            && file.lastModified() >= startTs - 60 * 60 * 1000L);

        if (files == null)
            return new File[0];

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        return files;
    }

    /**
     * @param file Log file.
     * @param startTs Start timestamp.
     * @param endTs End timestamp.
     * @param res Result.
     */
    private void readLogEntries(File file, long startTs, long endTs, List<AppLogEntry> res) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            AppLogEntry cur = null;
            boolean collect = false;

            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                Matcher matcher = LOG_ENTRY_START.matcher(line);

                if (matcher.matches()) {
                    enrichLogEntry(cur);

                    cur = null;
                    collect = false;

                    long ts = logTimestamp(matcher.group(1));
                    String level = matcher.group(2);

                    if (ts >= startTs && ts <= endTs && isWarningOrError(level)) {
                        cur = new AppLogEntry();
                        cur.timestamp = matcher.group(1);
                        cur.level = level;
                        cur.file = file.getName();
                        cur.text = line;

                        res.add(cur);
                        collect = true;
                    }
                }
                else if (collect)
                    cur.text += System.lineSeparator() + line;
            }

            enrichLogEntry(cur);
        }
        catch (IOException ignored) {
            // Monitoring page must remain available even if a log file is being rotated.
        }
    }

    /**
     * @param entry Log entry.
     */
    private void enrichLogEntry(AppLogEntry entry) {
        if (entry == null || entry.text == null)
            return;

        entry.serviceUrl = serviceUrl(entry.text);
        entry.serviceHost = serviceHost(entry.text, entry.serviceUrl);
        entry.responseCode = responseCode(entry.text);
        entry.summary = summary(entry);
    }

    /**
     * @param text Log text.
     */
    private String serviceUrl(String text) {
        Matcher matcher = SERVICE_URL.matcher(text);

        if (!matcher.find())
            return null;

        return trimUrl(matcher.group(1));
    }

    /**
     * @param text Log text.
     * @param serviceUrl Service URL.
     */
    private String serviceHost(String text, String serviceUrl) {
        if (!Strings.isNullOrEmpty(serviceUrl)) {
            try {
                return new URL(serviceUrl).getHost();
            }
            catch (MalformedURLException ignored) {
                // Try explicit host fields below.
            }
        }

        Matcher matcher = SERVICE_HOST.matcher(text);

        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * @param text Log text.
     */
    private Integer responseCode(String text) {
        Matcher matcher = RESPONSE_CODE.matcher(text);

        if (!matcher.find())
            return null;

        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null)
                return Integer.valueOf(matcher.group(i));
        }

        return null;
    }

    /**
     * @param entry Log entry.
     */
    private String summary(AppLogEntry entry) {
        String msg = exceptionSummary(entry.text);

        if (Strings.isNullOrEmpty(msg))
            msg = firstLine(entry.text);

        if (entry.responseCode != null && !Strings.isNullOrEmpty(entry.serviceHost))
            msg = "HTTP " + entry.responseCode + " from " + entry.serviceHost + ": " + msg;
        else if (!Strings.isNullOrEmpty(entry.serviceHost))
            msg = "Host " + entry.serviceHost + ": " + msg;

        return limit(msg);
    }

    /**
     * @param text Log text.
     */
    private String exceptionSummary(String text) {
        Matcher matcher = EXCEPTION_SUMMARY.matcher(text);
        String res = null;

        while (matcher.find())
            res = matcher.group(1);

        return res;
    }

    /**
     * @param text Text.
     */
    private String firstLine(String text) {
        int end = text.indexOf(System.lineSeparator());

        return end >= 0 ? text.substring(0, end) : text;
    }

    /**
     * @param url URL.
     */
    private String trimUrl(String url) {
        while (url.endsWith(":") || url.endsWith(".") || url.endsWith(";"))
            url = url.substring(0, url.length() - 1);

        return url;
    }

    /**
     * @param text Text.
     */
    private String limit(String text) {
        if (text == null || text.length() <= SUMMARY_LIMIT)
            return text;

        return text.substring(0, SUMMARY_LIMIT - 3) + "...";
    }

    /**
     * @param timestamp Timestamp.
     */
    private long logTimestamp(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp, LOG_TS_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        }
        catch (DateTimeParseException e) {
            return 0;
        }
    }

    /**
     * @param level Log level.
     */
    private boolean isWarningOrError(String level) {
        return "WARN".equals(level) || "ERROR".equals(level);
    }


    @GET
    @PermitAll
    @Path("profiling")
    public List<HotSpot> getHotMethods() {
        AutoProfilingInterceptor instance = CtxListener.getInjector(ctx).getInstance(AutoProfilingInterceptor.class);

        Collection<AutoProfilingInterceptor.Invocation> profile = instance.getInvocations();

        Stream<HotSpot> hotSpotStream = profile.stream().map(inv -> {
            HotSpot hotSpot = new HotSpot();

            hotSpot.setTiming(inv.getNanos(), inv.getCount());
            hotSpot.method = inv.getName();

            return hotSpot;
        });

        return hotSpotStream.sorted(Comparator.comparing(HotSpot::getNanos).reversed())
                .limit(100)
                .collect(Collectors.toList());
    }

    @POST
    @Path("resetProfiling")
    public SimpleResult resetProfiling() {
        AutoProfilingInterceptor instance = CtxListener.getInjector(ctx).getInstance(AutoProfilingInterceptor.class);

        instance.reset();

        return new SimpleResult("Ok");
    }

    @POST
    @PermitAll
    @Path("testSlackNotification")
    public SimpleResult testSlackNotification() {
        ISlackSender slackSender = CtxListener.getInjector(ctx).getInstance(ISlackSender.class);

        ITcBotConfig tcBotConfig = CtxListener.getInjector(ctx).getInstance(ITcBotConfig.class);

        try {
            NotificationsConfig notifications = tcBotConfig.notifications();

            for (INotificationChannel channel : notifications.channels()) {
                if (channel.slack() != null)
                    slackSender.sendMessage(channel.slack(), "Test Slack notification message!", notifications);
            }
        }
        catch (Exception e) {
            return new SimpleResult("Failed to send test Slack message: " + e.getMessage());
        }

        return new SimpleResult("Ok");
    }

    @POST
    @Path("testEmailNotification")
    public SimpleResult testEmailNotification(@FormParam("address") String address) {
        IEmailSender emailSender = CtxListener.getInjector(ctx).getInstance(IEmailSender.class);

        ITcBotConfig tcBotConfig = CtxListener.getInjector(ctx).getInstance(ITcBotConfig.class);

        try {
            NotificationsConfig notifications = tcBotConfig.notifications();
            String subj = "[MTCGA]: test email notification";

            ISendEmailConfig email = notifications.email();
            String plainText = "Test Email notification message!";
            String addressUnescaped = Strings.nullToEmpty(address).replace("%40", "@");
            emailSender.sendEmail(addressUnescaped, subj, plainText, plainText, email);
        } catch (Exception e) {
            return new SimpleResult("Failed to send test Email message: " + e.getMessage());
        }

        return new SimpleResult("Ok");
    }


    @GET
    @PermitAll
    @Path("cacheMetrics")
    public List<CacheMetricsUi> getCacheStat() {
        Ignite ignite = CtxListener.getInjector(ctx).getInstance(Ignite.class);

        final Collection<String> strings = ignite.cacheNames();

        final ArrayList<String> cacheNames = new ArrayList<>(strings);
        cacheNames.sort(String::compareTo);

        final List<CacheMetricsUi> res = new ArrayList<>();

        for (String next : cacheNames) {
            IgniteCache<?, ?> cache = ignite.cache(next);

            if (cache == null)
                continue;
            CacheMetrics metrics = cache.metrics();

            int size = cache.size();
            float averageGetTime = metrics.getAverageGetTime();
            float averagePutTime = metrics.getAveragePutTime();

            //System.out.println(next + ": " + size + " get " + averageGetTime + " put " + averagePutTime);

            Affinity<Object> affinity = ignite.affinity(next);

            res.add(new CacheMetricsUi(next, size, affinity.partitions()));
        }
        return res;
    }
}
