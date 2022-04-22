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
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMetrics;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.apache.ignite.tcbot.common.interceptor.AutoProfilingInterceptor;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTaskInterceptor;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("monitoring")
@Produces(MediaType.APPLICATION_JSON)
public class MonitoringService {
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
            res.end = invocation.end();
            res.result = invocation.result();
            res.count = invocation.count();
            return res;
        }).collect(Collectors.toList());
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
