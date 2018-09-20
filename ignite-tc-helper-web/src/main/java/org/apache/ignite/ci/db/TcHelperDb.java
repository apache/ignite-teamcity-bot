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

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.configuration.*;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.IgniteSpiContext;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.jetbrains.annotations.NotNull;

import org.slf4j.LoggerFactory;

import static org.apache.ignite.ci.web.Launcher.waitStopSignal;

/**
 *
 */
public class TcHelperDb {
    /** Logger. */
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IgniteTeamcityHelper.class);

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
            .setPersistenceEnabled(true);

        setupRegSize(regConf);

        final DataStorageConfiguration dsCfg = new DataStorageConfiguration()
            .setWalMode(WALMode.LOG_ONLY)
            .setWalHistorySize(1)
            .setCheckpointFrequency(5 * 60 * 1000)
            .setWriteThrottlingEnabled(true)
            .setDefaultDataRegionConfiguration(regConf);

        cfg.setDataStorageConfiguration(dsCfg);

        System.out.println("Starting Ignite Server Node");

        final Ignite ignite = Ignition.start(cfg);

        System.out.println("Activating Ignite Server Node");

        ignite.cluster().active(true);

        System.out.println("Activate Completed");

        return ignite;
    }

    /**
     * @param regConf Reg conf.
     */
    private static void setupRegSize(DataRegionConfiguration regConf) {
        String regSzGb = System.getProperty(ITcHelper.TEAMCITY_BOT_REGIONSIZE);

        if (regSzGb != null) {
            try {
                int szGb = Integer.parseInt(regSzGb);

                String msg = "Using custom size of region: " + szGb + "Gb";
                logger.info(msg);
                System.out.println(msg);

                regConf.setMaxSize(szGb * 1024L * 1024 * 1024);
            }
            catch (NumberFormatException e) {
                e.printStackTrace();

                logger.error("Unable to setup region", e);
            }
        } else {
            String msg = "Using default size of region.";
            logger.info(msg);
            System.out.println(msg);
        }
    }

    private static void configLogger(File workDir) {
        final String subdir = "tchelper_logs";
        Ignite2Configurer.configLogger(workDir, subdir);
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
        final int locPort = 54433;
        setupSinglePortDisco(cfg, locPort);
    }

    private static void setupSinglePortDisco(IgniteConfiguration cfg, int locPort) {
        final TcpDiscoverySpi spi = new TcpDiscoverySpi();
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

    public static <K, V> CacheConfiguration<K, V> getCacheV2TxConfig(String name) {
        return TcHelperDb.<K, V>getCacheV2Config(name).setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

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
