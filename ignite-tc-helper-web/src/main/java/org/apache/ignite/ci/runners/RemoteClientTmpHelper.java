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
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.cache.Cache;
import javax.xml.bind.JAXBException;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.tcbot.user.UserAndSessionsStorage;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.buildref.BuildRefDao;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildDao;
import org.apache.ignite.ci.teamcity.ignited.runhist.RunHistCompactedDao;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.util.XmlUtil;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.ci.teamcity.ignited.runhist.RunHistCompactedDao.BUILD_START_TIME_CACHE_NAME;

/**
 * Utility class for connecting to a remote server.
 */
public class RemoteClientTmpHelper {
    public static String DUMPS = "dumps";
    private static boolean dumpDict = false;


    /**
     * @param args Args.
     */
    public static void main(String[] args) {
        System.err.println("Please insert option of main");
    }

    /**
     * @param args Args.
     */
    public static void mainResetUser(String[] args) {
        try (Ignite ignite = tcbotServerConnectedClient()) {
            IgniteCache<Object, Object> users = ignite.cache(UserAndSessionsStorage.USERS);
            TcHelperUser user = (TcHelperUser)users.get("user");
            user.resetCredentials();
            users.put(user.username, user);
        }
    }

    /**
     * @param args Args.
     */
    public static void mainDestroyAllFatBuilds(String[] args) {
        int fatBuildsCnt;
        try (Ignite ignite = tcbotServerConnectedClient()) {
            IgniteCache<Object, Object> cacheHistEntries = ignite.cache(    FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME);
            fatBuildsCnt = cacheHistEntries.size();

            System.err.println("Start destroy() operation for fat builds: " + fatBuildsCnt);
            cacheHistEntries.destroy();
            System.err.println("Finish destroy() operation");

        }

        System.err.println("Test hist entries destroyed: [" + fatBuildsCnt + "] builds");
    }


    public static void mainDestroyTestHist(String[] args) {
        int testHist;
        int buildStartTimes;

        try (Ignite ignite = tcbotServerConnectedClient()) {
            IgniteCache<Object, Object> cacheHistEntries = ignite.cache(RunHistCompactedDao.TEST_HIST_CACHE_NAME);
            testHist = cacheHistEntries.size();

            System.err.println("Start destroy() operation for hist entries");
            cacheHistEntries.destroy();
            System.err.println("Finish destroy() operation");

            IgniteCache<Object, Object> cacheStartTimes = ignite.cache(BUILD_START_TIME_CACHE_NAME);

            buildStartTimes = cacheStartTimes.size();

            System.err.println("Start destroy operation for build start times flag");
            cacheStartTimes.destroy();
            System.err.println("Finish destroy operation");
        }

        System.err.println("Test hist entries destroyed [" + testHist + "] for [" + buildStartTimes + "] builds");
    }

    /**
     * @param args Args.
     */
    public static void main2(String[] args) {
        int inconsistent;

        try (Ignite ignite = tcbotServerConnectedClient()) {
            inconsistent = validateBuildIdConsistency(ignite);
        }

        System.err.println("Inconsistent builds in queue found [" + inconsistent + "]");
    }

    public static int validateBuildIdConsistency(Ignite ignite) {
        AtomicInteger inconsistent = new AtomicInteger();
        String apacheSrvName = "apache";
        int apache = ITeamcityIgnited.serverIdToInt(apacheSrvName);

        IgniteCache<String, IgniteStringCompactor.CompactorEntity> strings = ignite.cache(IgniteStringCompactor.STRINGS_CACHE);
        IgniteStringCompactor.CompactorEntity queuedEnt = strings.get(BuildRef.STATE_QUEUED);
        int stateQ = queuedEnt.id();
        IgniteCache<Long, BuildRefCompacted> cacheRef = ignite.cache(BuildRefDao.TEAMCITY_BUILD_CACHE_NAME);
        IgniteCache<Long, FatBuildCompacted> cacheFat = ignite.cache(FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME);

        cacheRef.forEach(
            entry -> {
                BuildRefCompacted buildRef = entry.getValue();

                int buildId = BuildRefDao.cacheKeyToBuildId(entry.getKey());

                if (buildRef.state() == stateQ && BuildRefDao.isKeyForServer(entry.getKey(), apache)) {

                    FatBuildCompacted fat = cacheFat.get(FatBuildDao.buildIdToCacheKey(apache, buildId));
                    if (fat != null && fat.getId() != buildId) {
                        dumpBuildRef(buildId, buildRef);
                        dumpFatBuild(cacheFat, apache, buildId);

                        inconsistent.incrementAndGet();

                        if (!fat.isOutdatedEntityVersion())
                            Preconditions.checkState(false);
                    }
                }
            }
        );

        return inconsistent.get();
    }

    public static void dumpOldBuild(int buildId, String href, Build fatBuild) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dumpsDir(), "BuildOld" + buildId + ".txt")))) {
            writer.write("<!--" + href + "-->\n");
            writer.write(XmlUtil.save(fatBuild));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (JAXBException e) {
            e.printStackTrace();
        }
    }

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

    public static void mainExport(String[] args) throws IOException {
        final Ignite ignite = tcbotServerConnectedClient();

        if (dumpDict) {
            IgniteCache<String, Object> strings = ignite.cache(IgniteStringCompactor.STRINGS_CACHE);
            try (FileWriter writer = new FileWriter("Dictionary.txt")) {
                for (Cache.Entry<String, Object> next1 : strings) {
                    writer.write(next1.getValue().toString()
                        + "\n");
                }
            }
        }
        if (false) {
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
    }

    public static Ignite tcbotServerConnectedClient() {
        final IgniteConfiguration cfg = new IgniteConfiguration();

        setupDisco(cfg);

        cfg.setGridLogger(new Slf4jLogger());

        cfg.setClientMode(true);

        return Ignition.start(cfg);
    }

    public static void dumpBuildRef(IgniteCache<Long, BuildRefCompacted> cache, int apache, int id) {
        long l = BuildRefDao.buildIdToCacheKey(apache, id);
        BuildRefCompacted compacted = cache.get(l);
        dumpBuildRef(id, compacted);
    }

    public static void dumpBuildRef(int id, BuildRefCompacted compacted) {
        Preconditions.checkNotNull(compacted, "Can't find build by ID " + id);

        File dumps = dumpsDir();

        try (FileWriter writer = new FileWriter(new File(dumps, "BuildRef " + id + ".txt"))) {
            writer.write(compacted.toString());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @NotNull public static File dumpsDir() {
        File dumps = new File(DUMPS);
        if (!dumps.exists())
            Preconditions.checkState(dumps.mkdirs());
        return dumps;
    }

    public static FatBuildCompacted dumpFatBuild(IgniteCache<Long, FatBuildCompacted> cache, int apache, int id) {
        long l = FatBuildDao.buildIdToCacheKey(apache, id);
        FatBuildCompacted compacted = cache.get(l);
        Preconditions.checkNotNull(compacted, "Can't find build by ID " + id);

        try (FileWriter writer = new FileWriter(new File(dumpsDir(), "Build " + id + ".txt"))) {
            writer.write(compacted.toString());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return compacted;
    }
}
