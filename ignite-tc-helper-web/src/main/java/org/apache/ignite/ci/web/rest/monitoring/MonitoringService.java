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
import org.apache.ignite.ci.di.ProfilingInterceptor;
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
    @Path("profiling")
    public List<String> getHotMethods() {
        ProfilingInterceptor instance = CtxListener.getInjector(ctx).getInstance(ProfilingInterceptor.class);
        Map<String, ProfilingInterceptor.Invocation> map = instance.getMap();

        Stream<HotSpot> hotSpotStream = map.entrySet().stream().map(entry -> {
            HotSpot hotSpot = new HotSpot();
            hotSpot.setNanos(entry.getValue().getNanos());
            hotSpot.setCount(entry.getValue().getCount());
            hotSpot.method = entry.getKey();
            return hotSpot;
        });

        return hotSpotStream.sorted(Comparator.comparing(HotSpot::getNanos).reversed())
                .limit(100)
                .map(HotSpot::toString).collect(Collectors.toList());
    }

    @GET
    @PermitAll
    @Path("caches")
    public List<String> getCacheStat() {
        Ignite ignite = CtxListener.getInjector(ctx).getInstance(Ignite.class);


        List<String> res = new ArrayList<>();
        for (Iterator<String> iterator = ignite.cacheNames().iterator(); iterator.hasNext(); ) {
            String next = iterator.next();

            IgniteCache<?, ?> cache = ignite.cache(next);

            if (cache == null)
                continue;
            CacheMetrics metrics = cache.metrics();

            int size = cache.size();
            //float averageGetTime = metrics.getAverageGetTime();
            // float averagePutTime = metrics.getAveragePutTime();

            // res.add(next + ": " + size + " get " + averageGetTime + " put " + averagePutTime);

            res.add(next + ": " + size);
        }
        return res;
    }
}
