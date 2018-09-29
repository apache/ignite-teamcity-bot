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

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMetrics;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.ci.di.AutoProfilingInterceptor;
import org.apache.ignite.ci.di.MonitoredTaskInterceptor;
import org.apache.ignite.ci.teamcity.ITeamcityHttpConnection;
import org.apache.ignite.ci.teamcity.TeamcityRecordingConnection;
import org.apache.ignite.ci.web.CtxListener;

import javax.annotation.security.PermitAll;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("monitoring")
@Produces(MediaType.APPLICATION_JSON)
public class MonitoringService {
    @Context
    private ServletContext ctx;

    @GET
    @PermitAll
    @Path("tasks")
    public List<TaskResult> getTaskMonitoring() {
        MonitoredTaskInterceptor instance = CtxListener.getInjector(ctx).getInstance(MonitoredTaskInterceptor.class);

        final Collection<MonitoredTaskInterceptor.Invocation> list = instance.getList();

        return list.stream().map(invocation -> {
            final TaskResult result = new TaskResult();
            result.name = invocation.name();
            result.start = invocation.start();
            result.end = invocation.end();
            result.result = invocation.result();
            result.count = invocation.count();
            return result;
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


    @GET
    @PermitAll
    @Path("caches")
    public List<String> getCacheStat() {
        Ignite ignite = CtxListener.getInjector(ctx).getInstance(Ignite.class);

        List<String> res = new ArrayList<>();
        final Collection<String> strings = ignite.cacheNames();

        final ArrayList<String> cacheNames = new ArrayList<>(strings);
        cacheNames.sort(String::compareTo);

        for (String next : cacheNames) {
            IgniteCache<?, ?> cache = ignite.cache(next);

            if (cache == null)
                continue;
            CacheMetrics metrics = cache.metrics();

            int size = cache.size();
            //float averageGetTime = metrics.getAverageGetTime();
            // float averagePutTime = metrics.getAveragePutTime();

            // res.add(next + ": " + size + " get " + averageGetTime + " put " + averagePutTime);


            Affinity<Object> affinity = ignite.affinity(next);

            res.add(next + ": " + size + " parts " + affinity.partitions());
        }
        return res;
    }


    @GET
    @PermitAll
    @Path("urls")
    public List<UrlUsed> getUrlsUsed() {
        final ITeamcityHttpConnection tcConn = CtxListener.getInjector(ctx).getInstance(ITeamcityHttpConnection.class);

        if (!(tcConn instanceof TeamcityRecordingConnection)) {
            return Collections.emptyList();
        }

        final TeamcityRecordingConnection tcConn1 = (TeamcityRecordingConnection) tcConn;

        final List<String> urls = tcConn1.getUrls();

        return urls.stream().map(s -> {
            final UrlUsed urlRequested = new UrlUsed();
            urlRequested.url = s;
            return urlRequested;
        }).collect(Collectors.toList());


    }
}
