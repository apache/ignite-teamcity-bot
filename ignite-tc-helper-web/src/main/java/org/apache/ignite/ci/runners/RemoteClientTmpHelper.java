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

package org.apache.ignite.ci.runners;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.io.FileWriter;
import java.io.IOException;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.observer.CompactBuildsInfo;
import org.apache.ignite.ci.observer.ObserverTask;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.BuildRefDao;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

public class RemoteClientTmpHelper {
    private static boolean dumpDict = false;

    private static void setupDisco(IgniteConfiguration cfg) {
        final TcpDiscoverySpi spi = new TcpDiscoverySpi();
        final int locPort = 54433;
        spi.setLocalPort(locPort);
        spi.setLocalPortRange(1);
        TcpDiscoveryVmIpFinder finder = new TcpDiscoveryVmIpFinder();
        finder.setAddresses(Lists.newArrayList("172.25.5.21:" + locPort));

        spi.setIpFinder(finder);

        cfg.setDiscoverySpi(spi);
    }

    public static void main(String[] args) throws IOException {
        final IgniteConfiguration cfg = new IgniteConfiguration();

        setupDisco(cfg);

        cfg.setGridLogger(new Slf4jLogger());

        cfg.setClientMode(true);

        final Ignite ignite = Ignition.start(cfg);

        if(dumpDict) {
            IgniteCache<String, Object> strings = ignite.cache(IgniteStringCompactor.STRINGS_CACHE);
            try (FileWriter writer = new FileWriter("Dictionary.txt")) {
                for (Cache.Entry<String, Object> next1 : strings) {
                    writer.write(next1.getValue().toString()
                        + "\n");
                }
            }
        }
        if(false) {
            IgniteCache<Long, FatBuildCompacted> cache1 = ignite.cache(FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME);

            int apache = ITeamcityIgnited.serverIdToInt("apache");

            int id = 2200135;
            int id1 = 2200209;
            dumpFatBuild(cache1, apache, id);
            dumpFatBuild(cache1, apache, id1);

            IgniteCache<Long, BuildRefCompacted> cache2 = ignite.cache(BuildRefDao.TEAMCITY_BUILD_CACHE_NAME);
            dumpBuildRef(cache2, apache, id);
            dumpBuildRef(cache2, apache, id1);
        }

        IgniteCache<CompactBuildsInfo, Object> cache = ignite.cache(ObserverTask.BUILDS_CACHE_NAME);

        CompactBuildsInfo cbi = new CompactBuildsInfo();

        cbi.userName(62541);
        cbi.srvId(245001);
        cbi.buildTypeId(113);
        cbi.branchForTc(2008);
        cbi.ticket(263594);
        cbi.date(1542263949429L);

        cbi.addBuild(2322291, 2322298, 2322296, 2322294, 2322292, 2322300);

        boolean rmv = cache.remove(cbi);

        try {
            Preconditions.checkState(rmv, "can't remove " + cbi);
        }
        finally {
            ignite.close();
        }

    }

    public static void dumpBuildRef(IgniteCache<Long, BuildRefCompacted> cache, int apache, int id) throws IOException {
        long l = BuildRefDao.buildIdToCacheKey(apache, id);
        BuildRefCompacted compacted = cache.get(l);
        Preconditions.checkNotNull(compacted, "Can't find build by ID " + id);
        try (FileWriter writer = new FileWriter("BuildRef " + id + ".txt")) {
            writer.write(compacted.toString());
        }
    }

    public static void dumpFatBuild(IgniteCache<Long, FatBuildCompacted> cache, int apache, int id) throws IOException {
        long l = FatBuildDao.buildIdToCacheKey(apache, id);
        FatBuildCompacted compacted = cache.get(l);
        Preconditions.checkNotNull(compacted, "Can't find build by ID " + id);
        try (FileWriter writer = new FileWriter("Build " + id + ".txt")) {
            writer.write(compacted.toString());
        }
    }
}
