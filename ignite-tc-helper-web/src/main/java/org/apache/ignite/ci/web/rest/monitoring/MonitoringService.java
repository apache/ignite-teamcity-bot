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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.security.RolesAllowed;
import javax.cache.Cache;
import javax.servlet.ServletContext;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMetrics;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.auth.AuthenticationFilter;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.apache.ignite.tcbot.common.monitoring.MonitoredTasks;
import org.apache.ignite.tcbot.common.monitoring.ProfilingMonitor;
import org.apache.ignite.tcbot.engine.build.AiPromptRequestMonitor;
import org.apache.ignite.tcbot.engine.conf.INotificationChannel;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.NotificationsConfig;
import org.apache.ignite.tcbot.engine.process.BotProcessMonitor;
import org.apache.ignite.tcbot.engine.process.BotProcessStatus;
import org.apache.ignite.tcbot.engine.process.ProgressReporter;
import org.apache.ignite.tcbot.notify.IEmailSender;
import org.apache.ignite.tcbot.notify.ISendEmailConfig;
import org.apache.ignite.tcbot.notify.ISlackSender;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcbot.persistence.scheduler.MaintenanceActionInfo;
import org.apache.ignite.tcbot.persistence.scheduler.MaintenanceActionRegistry;
import org.apache.ignite.tcbot.persistence.scheduler.ScheduledTaskInfo;

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

    /** Secret-like values in log text. */
    private static final Pattern SECRET_VALUE = Pattern.compile(
        "(?i)(authorization:\\s*(?:basic|bearer|token)\\s+|(?:access_token|auth_token|token|password|passwd|pwd|secret)=)" +
            "([^\\s&\"'<>]+)");

    /** JSON secret-like values in log text. */
    private static final Pattern JSON_SECRET_VALUE = Pattern.compile(
        "(?i)(\"(?:access_token|auth_token|token|password|passwd|pwd|secret)\"\\s*:\\s*\")([^\"]+)(\")");

    /** Max summary length. */
    private static final int SUMMARY_LIMIT = 240;

    /** Default number of cache entries to preview. */
    private static final int DFLT_CACHE_PEEK_LIMIT = 100;

    /** Hard cache preview cap. */
    private static final int MAX_CACHE_PEEK_LIMIT = 100;

    /** System property with comma-separated exact cache names allowed for raw preview. */
    private static final String CACHE_PEEK_ALLOWED_CACHES = "tcbot.monitoring.cachePeek.allowedCaches";

    /** Built-in exact cache names allowed for raw preview. */
    private static final Set<String> DFLT_CACHE_PEEK_ALLOWED_CACHES = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(
            "botDetectedDefects",
            "botDetectedIssues",
            "buildLogCheckResult",
            "buildsConditions",
            "compactVisasHistoryCacheV2",
            "gitHubBranch",
            "gitHubPr",
            "mutedIssues",
            "newTestsCache",
            "teamcityBuildRef",
            "teamcityBuildStartTime",
            "teamcityBuildTypeRef",
            "teamcityChange",
            "teamcityFatBuild",
            "teamcityFatBuildType",
            "teamcityMute",
            "teamcitySuiteHistory"
        )));

    /** JSON mapper for raw cache entry values. */
    private static final ObjectMapper CACHE_PEEK_MAPPER = new ObjectMapper()
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    /** Log timestamp format. */
    private static final DateTimeFormatter LOG_TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** Context. */
    @Context
    private ServletContext ctx;

    @GET
    @Path("tasks")
    public List<TaskResult> getTaskMonitoring() {
        MonitoredTasks instance = instance(MonitoredTasks.class);

        final Collection<? extends MonitoredTasks.Invocation> list = instance.getList();

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
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("scheduledTasks")
    public List<ScheduledTaskInfo> getScheduledTasks() {
        MaintenanceActionRegistry actions = instance(MaintenanceActionRegistry.class);
        BotProcessMonitor process = instance(BotProcessMonitor.class);

        return instance(IScheduler.class).scheduledTasks().stream()
            .peek(task -> enrichScheduledTask(task, actions, process))
            .collect(Collectors.toList());
    }

    @GET
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("maintenanceActions")
    public List<MaintenanceActionInfo> getMaintenanceActions() {
        IScheduler scheduler = instance(IScheduler.class);
        MaintenanceActionRegistry actions = instance(MaintenanceActionRegistry.class);
        BotProcessMonitor process = instance(BotProcessMonitor.class);
        Map<String, ScheduledTaskInfo> scheduled = scheduler.scheduledTasks().stream()
            .peek(task -> enrichScheduledTask(task, actions, process))
            .collect(Collectors.toMap(task -> task.name, task -> task, (first, second) -> first));

        return actions.actions().stream()
            .peek(action -> {
                ScheduledTaskInfo task = scheduled.get(action.name);

                if (task == null)
                    return;

                action.status = task.status;
                action.processStatus = task.processStatus;
                action.processKind = task.processKind;
                action.processState = task.processState;
                action.processRunning = task.processRunning;
                action.processId = task.processId;
                action.canStartNow = task.canStartNow;
            })
            .collect(Collectors.toList());
    }

    @POST
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("maintenanceActions/start")
    public SimpleResult startMaintenanceAction(@FormParam("name") String name,
        @QueryParam("processId") Long processId) {
        if (Strings.isNullOrEmpty(name))
            throw new BadRequestException("Action name is required");

        MaintenanceActionRegistry actions = instance(MaintenanceActionRegistry.class);
        IScheduler scheduler = instance(IScheduler.class);
        BotProcessMonitor process = instance(BotProcessMonitor.class);
        ProgressReporter progress = instance(ProgressReporter.class);

        if (!actions.hasAction(name)) {
            process.fail(processId, "Maintenance action is not registered: " + name);

            throw new NotFoundException("Maintenance action is not registered: " + name);
        }

        process.start(processId, "maintenanceAction", "Maintenance action request accepted: " + name);

        boolean accepted = scheduler.runNamedNow(name, () -> {
            try {
                progress.run(processId, "maintenanceAction", "Maintenance action request accepted: " + name,
                    () -> actions.run(name));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, processId);

        if (!accepted) {
            process.fail(processId, "Maintenance action is already queued or running: " + name);

            throw new ClientErrorException("Maintenance action is already queued or running: " + name,
                Response.Status.CONFLICT);
        }

        return new SimpleResult("Maintenance action start requested: " + name);
    }

    /**
     * @param task Scheduled task.
     * @param actions Maintenance actions.
     * @param process Bot process monitor.
     */
    private void enrichScheduledTask(ScheduledTaskInfo task, MaintenanceActionRegistry actions,
        BotProcessMonitor process) {
        if (task.processId != null) {
            BotProcessStatus status = process.status(task.processId);

            if (status.id != null && !Strings.isNullOrEmpty(status.status)) {
                task.processStatus = status.status;
                task.processKind = status.kind;
                task.processState = status.isRunning() ? "RUNNING" : "FINISHED";
                task.processRunning = status.isRunning();

                if (status.isRunning())
                    task.status = status.status;
            }
        }

        task.canStartNow = actions.hasAction(task.name) && task.canStartNow;
    }

    @GET
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("appLogSummaryLink")
    public AppLogSummaryLink getAppLogSummaryLink() {
        MonitoredTasks instance = instance(MonitoredTasks.class);

        AppLogSummaryLink res = new AppLogSummaryLink();
        res.startTs = instance.startedTs();
        res.name = "Application log since startup";

        return res;
    }

    @GET
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
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

        entry.text = sanitizeLogText(entry.text);
        entry.serviceUrl = serviceUrl(entry.text);
        entry.serviceHost = serviceHost(entry.text, entry.serviceUrl);
        entry.responseCode = responseCode(entry.text);
        entry.summary = summary(entry);
    }

    /**
     * @param text Log text.
     */
    private String sanitizeLogText(String text) {
        String sanitized = SECRET_VALUE.matcher(text).replaceAll("$1<redacted>");

        return JSON_SECRET_VALUE.matcher(sanitized).replaceAll("$1<redacted>$3");
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
    @Path("profiling")
    public List<HotSpot> getHotMethods() {
        ProfilingMonitor instance = instance(ProfilingMonitor.class);

        Collection<? extends ProfilingMonitor.Invocation> profile = instance.getInvocations();

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
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("resetProfiling")
    public SimpleResult resetProfiling() {
        ProfilingMonitor instance = instance(ProfilingMonitor.class);

        instance.reset();

        return new SimpleResult("Ok");
    }

    @POST
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("testSlackNotification")
    public SimpleResult testSlackNotification() {
        ISlackSender slackSender = instance(ISlackSender.class);
        ITcBotConfig tcBotConfig = instance(ITcBotConfig.class);

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
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("testEmailNotification")
    public SimpleResult testEmailNotification(@FormParam("address") String address) {
        IEmailSender emailSender = instance(IEmailSender.class);
        ITcBotConfig tcBotConfig = instance(ITcBotConfig.class);

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
    @Path("cacheMetrics")
    public List<CacheMetricsUi> getCacheStat() {
        Ignite ignite = instance(Ignite.class);
        Set<String> resettableCaches = resettableCaches();

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

            res.add(new CacheMetricsUi(next, size, affinity.partitions(), resettableCaches.contains(next)));
        }
        return res;
    }

    @GET
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Produces(MediaType.TEXT_PLAIN)
    @Path("cachePeek")
    public String cachePeek(@QueryParam("name") String name, @QueryParam("limit") Integer limit) {
        if (Strings.isNullOrEmpty(name))
            throw new BadRequestException("Cache name is required");

        ensureCanPeekCache(name);

        Ignite ignite = instance(Ignite.class);
        IgniteCache<?, ?> cache = ignite.cache(name);

        if (cache == null)
            throw new NotFoundException("Cache not found: " + name);

        int actualLimit = normalizeCachePeekLimit(limit);
        StringBuilder res = new StringBuilder();
        int shown = 0;
        boolean truncated = false;

        res.append("Cache: ").append(name).append('\n');
        res.append("Size: not calculated by cachePeek").append('\n');
        res.append("Limit: ").append(actualLimit).append("\n\n");

        for (Cache.Entry<?, ?> entry : cache) {
            if (shown >= actualLimit) {
                truncated = true;

                break;
            }

            shown++;

            res.append("Entry #").append(shown).append('\n');
            res.append("Key class: ").append(className(entry.getKey())).append('\n');
            res.append(toJsonNode(entry.getKey()).toPrettyString()).append('\n');
            res.append("Value class: ").append(className(entry.getValue())).append('\n');
            res.append(toJsonNode(entry.getValue()).toPrettyString()).append("\n\n");
        }

        res.append("Entries shown: ").append(shown).append('\n');
        res.append("Truncated: ").append(truncated).append('\n');

        return res.toString();
    }

    /**
     * @param limit Requested limit.
     */
    private static int normalizeCachePeekLimit(Integer limit) {
        if (limit == null || limit <= 0)
            return DFLT_CACHE_PEEK_LIMIT;

        return Math.min(limit, MAX_CACHE_PEEK_LIMIT);
    }

    /**
     * @param obj Object.
     */
    private static String className(Object obj) {
        return obj == null ? "null" : obj.getClass().getName();
    }

    /**
     * @param name Cache name.
     */
    private void ensureCanPeekCache(String name) {
        if (cachePeekAllowedCaches().contains(name))
            return;

        throw new ForbiddenException("Cache peek is not allowed for cache: " + name +
            ". Add the exact cache name to " + CACHE_PEEK_ALLOWED_CACHES + " to enable it.");
    }

    /**
     * @return Exact cache names allowed for raw preview.
     */
    private static Set<String> cachePeekAllowedCaches() {
        Set<String> res = new HashSet<>(DFLT_CACHE_PEEK_ALLOWED_CACHES);

        Arrays.stream(Strings.nullToEmpty(System.getProperty(CACHE_PEEK_ALLOWED_CACHES)).split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(res::add);

        return res;
    }

    /**
     * @param obj Object.
     */
    private static JsonNode toJsonNode(Object obj) {
        try {
            return CACHE_PEEK_MAPPER.valueToTree(obj);
        }
        catch (RuntimeException e) {
            return CACHE_PEEK_MAPPER.createObjectNode()
                .put("serializationError", e.getClass().getSimpleName() + ": " + e.getMessage())
                .put("class", className(obj))
                .put("toString", String.valueOf(obj));
        }
    }

    @POST
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("resetCache")
    public SimpleResult resetCache(@FormParam("name") String name, @QueryParam("processId") Long processId) {
        BotProcessMonitor process = instance(BotProcessMonitor.class);
        Set<String> resettableCaches = resettableCaches();

        process.start(processId, "resetCache", "Cache reset request accepted: " + name);

        if (!resettableCaches.contains(name)) {
            process.fail(processId, "Cache reset is not allowed for: " + name);

            throw new BadRequestException("Cache reset is not allowed for: " + name);
        }

        Ignite ignite = instance(Ignite.class);
        IgniteCache<?, ?> cache = ignite.cache(name);

        if (cache == null) {
            process.fail(processId, "Cache not found: " + name);

            throw new NotFoundException("Cache not found: " + name);
        }

        int size = cache.size();

        process.status(processId, "Clearing cache: " + name);

        cache.clear();

        String result = "Cache reset: " + name + ", cleared entries: " + size;

        process.finish(processId, result);

        return new SimpleResult(result);
    }

    /**
     * @return Cache names admins may reset from monitoring UI.
     */
    private Set<String> resettableCaches() {
        return Set.copyOf(instance(ITcBotConfig.class).resettableCaches());
    }

    @GET
    @Path("requests")
    public List<RequestStat> getRequestStats() {
        return RestRequestTimingStorage.stats();
    }

    @GET
    @Path("recentRequests")
    public List<RequestTiming> getRecentRequests() {
        return RestRequestTimingStorage.recent();
    }

    @POST
    @RolesAllowed(AuthenticationFilter.ADMIN_ROLE)
    @Path("resetRequests")
    public SimpleResult resetRequestStats() {
        RestRequestTimingStorage.reset();

        return new SimpleResult("Ok");
    }

    @GET
    @Path("aiPrompts")
    public List<AiPromptRequestMonitor.Request> getAiPromptRequests() {
        AiPromptRequestMonitor monitor = instance(AiPromptRequestMonitor.class);

        return monitor.getRequests();
    }

    private <T> T instance(Class<T> type) {
        return CtxListener.getApplicationContext(ctx).getInstance(type);
    }
}
