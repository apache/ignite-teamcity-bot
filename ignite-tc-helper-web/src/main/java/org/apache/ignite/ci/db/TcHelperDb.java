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

package org.apache.ignite.ci.db;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;

import ch.qos.logback.classic.LoggerContext;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheInterceptorAdapter;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.util.ObjectInterner;
import org.apache.ignite.configuration.*;
import org.apache.ignite.logger.java.JavaLogger;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.IgniteSpiContext;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.ci.web.Launcher.waitStopSignal;

/**
 * Created by dpavlov on 04.08.2017
 */
public class TcHelperDb {

    public static void main(String[] args) {
        Ignite ignite = start();

        System.out.println("Starting Ignite DB only");

        Runnable r = () -> {
            boolean stop = waitStopSignal();

            if (stop) {
                try {
                    TcHelperDb.stop(ignite);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

        };
        new Thread(r).start();
    }

    public static Ignite start() {

        final File workDir = HelperConfig.resolveWorkDir();
        configLogger(workDir);

        final IgniteConfiguration cfg = new IgniteConfiguration();
        setWork(cfg, workDir);

        setupDisco(cfg);
        cfg.setConsistentId("TcHelper");
        cfg.setGridLogger(new Slf4jLogger());


        final DataRegionConfiguration regConf = new DataRegionConfiguration()
            .setMaxSize(12L * 1024 * 1024 * 1024)
            .setPersistenceEnabled(true);

        final DataStorageConfiguration dsCfg = new DataStorageConfiguration()
            .setWalMode(WALMode.LOG_ONLY)
            .setWalHistorySize(1)
            .setCheckpointFrequency(60 * 1000)
            .setWriteThrottlingEnabled(true)
            .setDefaultDataRegionConfiguration(regConf);

        cfg.setDataStorageConfiguration(dsCfg);

        System.out.println("Starting Ignite Server Node");

        final Ignite ignite = Ignition.start(cfg);

        System.out.println("Activating Ignite Server Node");

        ignite.cluster().active(true);


        System.out.println("Activate completed");

        return ignite;
    }

    private static void configLogger(File workDir) {
        LoggerContext logCtx = (LoggerContext) LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder logEncoder  = new PatternLayoutEncoder();
        logEncoder.setContext(logCtx);
        logEncoder.setPattern("%-12date{YYYY-MM-dd HH:mm:ss.SSS} %-5level [%t] - %msg%n");
        logEncoder.start();

        RollingFileAppender rollingFa = new RollingFileAppender();
        rollingFa.setContext(logCtx);
        rollingFa.setName("logFile");
        rollingFa.setEncoder(logEncoder);
        rollingFa.setAppend(true);

        final File logs = new File(workDir, "tchelper_logs");
        HelperConfig.ensureDirExist(logs);

        rollingFa.setFile(new File(logs, "logfile.log").getAbsolutePath());

        TimeBasedRollingPolicy logFilePolicy = new TimeBasedRollingPolicy();
        logFilePolicy.setContext(logCtx);
        logFilePolicy.setParent(rollingFa);
        logFilePolicy.setFileNamePattern(new File(logs, "logfile-%d{yyyy-MM-dd_HH}.log").getAbsolutePath());
        logFilePolicy.setMaxHistory(7);
        logFilePolicy.start();

        rollingFa.setRollingPolicy(logFilePolicy);
        rollingFa.start();

        Logger log = logCtx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        log.setAdditive(false);
        log.setLevel(Level.INFO);
        log.detachAndStopAllAppenders();

        log.addAppender(rollingFa);
    }

    public static Ignite startClient() {
        final IgniteConfiguration cfg = new IgniteConfiguration();

        setupDisco(cfg);

        cfg.setGridLogger(new Slf4jLogger());

        cfg.setClientMode(true);

        final Ignite ignite = Ignition.start(cfg);
        ignite.cluster().active(true);
        return ignite;
    }

    private static void setupDisco(IgniteConfiguration cfg) {
        final TcpDiscoverySpi spi = new TcpDiscoverySpi();
        final int locPort = 54433;
        spi.setLocalPort(locPort);
        spi.setLocalPortRange(1);
        spi.setIpFinder(new LocalOnlyTcpDiscoveryIpFinder(locPort));

        cfg.setDiscoverySpi(spi);
    }

    private static void setWork(IgniteConfiguration cfg, File workDir) {
        try {
            cfg.setIgniteHome(workDir.getCanonicalPath());
        }
        catch (IOException e) {
            e.printStackTrace();
            cfg.setIgniteHome(workDir.getAbsolutePath());
        }
    }

    public static void stop(Ignite ignite) {
        Ignition.stop(ignite.name(), false);
    }

    @NotNull
    public static <K, V> CacheConfiguration<K, V> getCacheV2Config(String name) {
        CacheConfiguration<K, V> ccfg = new CacheConfiguration<>(name);

        ccfg.setAffinity(new RendezvousAffinityFunction(false, 32));

        /*
        ccfg.setInterceptor(new CacheInterceptorAdapter<K, V>(){
            @Nullable @Override public V onGet(K key, V val) {
                V v = super.onGet(key, val);

                int i = ObjectInterner.internFields(v);

                if(i>0)
                    System.out.println("cache.get: Strings saved: " + i);

                return v;
            }
        });*/

        return ccfg;
    }

    private static class LocalOnlyTcpDiscoveryIpFinder implements TcpDiscoveryIpFinder {
        private int port;

        public LocalOnlyTcpDiscoveryIpFinder(int port) {
            this.port = port;
        }

        @Override public void onSpiContextInitialized(IgniteSpiContext spiCtx) throws IgniteSpiException {

        }

        @Override public void onSpiContextDestroyed() {

        }

        @Override
        public void initializeLocalAddresses(Collection<InetSocketAddress> addrs) throws IgniteSpiException {

        }

        @Override public Collection<InetSocketAddress> getRegisteredAddresses() throws IgniteSpiException {
            return Collections.singletonList(new InetSocketAddress("localhost", port));
        }

        @Override public boolean isShared() {
            return false;
        }

        @Override public void registerAddresses(Collection<InetSocketAddress> addrs) throws IgniteSpiException {

        }

        @Override public void unregisterAddresses(Collection<InetSocketAddress> addrs) throws IgniteSpiException {

        }

        @Override public void close() {

        }
    }
}
