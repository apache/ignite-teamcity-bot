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
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.web.model.Version;
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
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(TcHelperDb.class);

    public static void main(String[] args) {
        Ignite ignite = start();

        System.out.println("Starting Ignite DB only, " + Version.VERSION);

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
        Ignite2Configurer.configLogger(workDir, "tcbot_logs");

        final IgniteConfiguration cfg = new IgniteConfiguration();
        Ignite2Configurer.setIgniteHome(cfg, workDir);

        setupDisco(cfg);
        cfg.setConsistentId("TcHelper");
        cfg.setGridLogger(new Slf4jLogger());

        final DataRegionConfiguration regConf = Ignite2Configurer.getDataRegionConfiguration();

        final DataStorageConfiguration dsCfg = Ignite2Configurer.getDataStorageConfiguration(regConf);

        dsCfg.setPageSize(4 * 1024);

        cfg.setDataStorageConfiguration(dsCfg);

        System.out.println("Starting Ignite Server Node, " + Version.VERSION);

        final Ignite ignite = Ignition.start(cfg);

        System.out.println("Activating Ignite Server Node, " + Version.VERSION);

        ignite.cluster().active(true);

        System.out.println("Activate Completed");

        return ignite;
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
