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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.cache.Cache;
import javax.xml.bind.JAXBException;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.issue.Issue;
import org.apache.ignite.ci.issue.IssueKey;
import org.apache.ignite.tcbot.engine.issue.IssueType;
import org.apache.ignite.tcbot.engine.issue.IssuesStorage;
import org.apache.ignite.tcbot.engine.user.UserAndSessionsStorage;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.tcbot.persistence.IgniteStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.build.FatBuildDao;
import org.apache.ignite.tcignited.buildref.BuildRefDao;
import org.apache.ignite.tcservice.model.hist.BuildRef;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcservice.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.tcignited.history.BuildStartTimeStorage.BUILD_START_TIME_CACHE_NAME;

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
        mainSetUserAsAdmin(args);

        //mainDumpAllUsers(args);
        // mainExport(args);
        // mainDropInvalidIssues(args);
        System.err.println("Please insert option of main");
    }


    /**
     * @param args Args.
     */
    public static void mainSetUserAsAdmin(String[] args) {
        try (Ignite ignite = tcbotServerConnectedClient()) {
            IgniteCache<String, TcHelperUser> users = ignite.cache(UserAndSessionsStorage.USERS);
            TcHelperUser user = users.get("dpavlov");
            user.setAdmin(true);
            users.put(user.username, user);
        }
    }


    public static void mainDropInvalidIssues(String[] args) {
        try (Ignite ignite = tcbotServerConnectedClient()) {
            IgniteCache<IssueKey, Issue>  bst = ignite.cache(IssuesStorage.BOT_DETECTED_ISSUES);
            Iterator<Cache.Entry<IssueKey, Issue>> iter = bst.iterator();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dumpsDir(),
                "Issues_dropped.txt")))) {
                while (iter.hasNext()) {
                    Cache.Entry<IssueKey, Issue> next = iter.next();

                    boolean rmv = false;
                    Issue val = next.getValue();
                    if (val.type == null)
                        rmv = true;

                    //don't touch it
                    if (Objects.equals(val.type, IssueType.newContributedTestFailure.code()))
                        continue;

                    long ageDays = -1;
                    if (val != null && val.buildStartTs == null) {
                        if (val.detectedTs == null)
                            rmv = true;
                        else
                            ageDays = Duration.ofMillis(System.currentTimeMillis() - val.detectedTs).toDays();


                        rmv = true;
                    }

                    if(rmv) {
                        bst.remove(next.getKey());

                        String str = "Removing issue " + next.getKey() + " " + val.type + " detected " +
                            ageDays + " days ago\n";
                        writer.write(str);
                        System.err.println(str);
                    }
                }

                writer.flush();
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            dumpDictionary(ignite);
        }
    }


    public static void mainDumpIssues(String[] args) {
        try (Ignite ignite = tcbotServerConnectedClient()) {
            IgniteCache<IssueKey, Issue>  bst = ignite.cache(IssuesStorage.BOT_DETECTED_ISSUES);
            Iterator<Cache.Entry<IssueKey, Issue>> iter = bst.iterator();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dumpsDir(),
                "Issues.txt")))) {
                while (iter.hasNext()) {
                    Cache.Entry<IssueKey, Issue> next = iter.next();

                    Issue val = next.getValue();
                    long ageDays = -1;
                    if (val != null && val.buildStartTs != null)
                        ageDays = Duration.ofMillis(System.currentTimeMillis() - val.buildStartTs).toDays();

                    writer.write(next.getKey() + " " + val + " " +
                        ageDays + "\n");
                }

                writer.flush();
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            dumpDictionary(ignite);
        }
    }


    public static void mainDumpFatBuildStartTime(String[] args) {
        try (Ignite ignite = tcbotServerConnectedClient()) {
            IgniteCache<Long, FatBuildCompacted> bst = ignite.cache(FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME);
            Iterator<Cache.Entry<Long, FatBuildCompacted>> iterator = bst.iterator();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dumpsDir(),
                    "fatBuildStartTime.txt")))) {
                while (iterator.hasNext()) {
                    Cache.Entry<Long, FatBuildCompacted> next = iterator.next();

                    FatBuildCompacted val = next.getValue();
                    long ageDays = -1;
                    long startDateTs = -2;

                    if (val != null) {
                        startDateTs = val.getStartDateTs();
                        ageDays = Duration.ofMillis(System.currentTimeMillis() - startDateTs).toDays();
                    }

                    writer.write(next.getKey() + " " + startDateTs + " " +
                            ageDays + "\n");
                }

            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }


    public static void mainDumpBuildStartTime(String[] args) {
        try (Ignite ignite = tcbotServerConnectedClient()) {
            IgniteCache<Long, Long> bst = ignite.cache(BUILD_START_TIME_CACHE_NAME);
            Iterator<Cache.Entry<Long, Long>> iterator = bst.iterator();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dumpsDir(),
                    "BuildStartTime.txt")))) {
            while (iterator.hasNext()) {
                Cache.Entry<Long, Long> next = iterator.next();

                Long val = next.getValue();
                long ageDays = -1;
                if(val!=null)
                ageDays = Duration.ofMillis(System.currentTimeMillis() - val).toDays();

                writer.write(next.getKey() + " " + val + " " +
                    ageDays +"\n");
            }

            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }


    /**
     * @param args Args.
     */
    public static void mainResetUser(String[] args) {
        try (Ignite ignite = tcbotServerConnectedClient()) {
            IgniteCache<String, TcHelperUser> users = ignite.cache(UserAndSessionsStorage.USERS);
            TcHelperUser user = users.get("user");
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
        int buildStartTimes;

        try (Ignite ignite = tcbotServerConnectedClient()) {

            IgniteCache<Object, Object> cacheStartTimes = ignite.cache(BUILD_START_TIME_CACHE_NAME);

            buildStartTimes = cacheStartTimes.size();

            System.err.println("Start destroy operation for build start times flag");
            cacheStartTimes.destroy();
            System.err.println("Finish destroy operation");
        }

        System.err.println("Test build start times destroyed [" + buildStartTimes + "] builds");
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

        IgniteCache<String, org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity> strings = ignite.cache(IgniteStringCompactor.STRINGS_CACHE);
        org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.CompactorEntity queuedEnt = strings.get(BuildRef.STATE_QUEUED);
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

    public static void mainExport(String[] args) {
        final Ignite ignite = tcbotServerConnectedClient();

        if (dumpDict)
            dumpDictionary(ignite);

        if (true) {
            IgniteCache<Long, FatBuildCompacted> cache1 = ignite.cache(FatBuildDao.TEAMCITY_FAT_BUILD_CACHE_NAME);

            int apache = ITeamcityIgnited.serverIdToInt("apache");

            int id = 4466392;
            int id1 = 4465532;
            dumpFatBuild(cache1, apache, id);
            dumpFatBuild(cache1, apache, id1);

            IgniteCache<Long, BuildRefCompacted> cache2 = ignite.cache(BuildRefDao.TEAMCITY_BUILD_CACHE_NAME);
            dumpBuildRef(cache2, apache, id);
            dumpBuildRef(cache2, apache, id1);
        }

        ignite.close();
    }

    public static void dumpDictionary(Ignite ignite) {
        IgniteCache<String, Object> strings = ignite.cache(IgniteStringCompactor.STRINGS_CACHE);
        try {
            try (FileWriter writer = new FileWriter(new File(dumpsDir(), "Dictionary.txt"))) {
                for (Cache.Entry<String, Object> next1 : strings) {
                    writer.write(next1.getValue().toString()
                        + "\n");
                }
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
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


    public static void mainDumpAllUsers(String[] args) {
        try (Ignite ignite = tcbotServerConnectedClient()) {
            IgniteCache<String, TcHelperUser>users = ignite.cache(UserAndSessionsStorage.USERS);

            Iterator<Cache.Entry<String, TcHelperUser>> iterator = users.iterator();
            while (iterator.hasNext()) {
                Cache.Entry<String, TcHelperUser> next = iterator.next();
                dumpUser(next.getValue());
            }
        }
    }

    private static void dumpUser(TcHelperUser value) {
        try (FileWriter writer = new FileWriter(new File(dumpsDir(), "Build " + value.username() + ".txt"))) {
            writer.write(value.toString());
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
