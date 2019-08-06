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

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.ci.web.model.Version;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.IgniteSpiContext;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.tcbot.persistence.CacheConfigs;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.ci.web.Launcher.waitStopSignal;

/**
 *
 */
public class TcHelperDb {

    public static void main(String[] args) {
        Ignite ignite = new Ignite1Init().startIgnite();

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

    public static Ignite startClient() {
        final IgniteConfiguration cfg = new IgniteConfiguration();

        setupDisco(cfg);

        cfg.setGridLogger(new Slf4jLogger());

        cfg.setClientMode(true);

        final Ignite ignite = Ignition.start(cfg);
        ignite.cluster().active(true);
        return ignite;
    }

    public static void setupDisco(IgniteConfiguration cfg) {
        setupSinglePortDisco(cfg, 54433);
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

    /** */
    @NotNull
    public static <K, V> CacheConfiguration<K, V> getCacheV3Config(String name) {
        return new CacheConfiguration<K, V>(name)
            .setAffinity(new RendezvousAffinityFunction(false, 8));
    }

    /** */
    public static <K, V> CacheConfiguration<K, V> getCacheV3TxConfig(String name) {
        return TcHelperDb.<K, V>getCacheV3Config(name).setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
    }

    public static class LocalOnlyTcpDiscoveryIpFinder implements TcpDiscoveryIpFinder {
        /** Port. */
        private int port;

        /**
         * @param port Port.
         */
        public LocalOnlyTcpDiscoveryIpFinder(int port) {
            this.port = port;
        }

        /** {@inheritDoc} */
        @Override public void onSpiContextInitialized(IgniteSpiContext spiCtx) throws IgniteSpiException {

        }

        /** {@inheritDoc} */
        @Override public void onSpiContextDestroyed() {

        }

        /** {@inheritDoc} */
        @Override public void initializeLocalAddresses(Collection<InetSocketAddress> addrs) throws IgniteSpiException {

        }

        /** {@inheritDoc} */
        @Override public Collection<InetSocketAddress> getRegisteredAddresses() throws IgniteSpiException {
            return Collections.singletonList(new InetSocketAddress("localhost", port));
        }

        /** {@inheritDoc} */
        @Override public boolean isShared() {
            return false;
        }

        /** {@inheritDoc} */
        @Override public void registerAddresses(Collection<InetSocketAddress> addrs) throws IgniteSpiException {

        }

        /** {@inheritDoc} */
        @Override public void unregisterAddresses(Collection<InetSocketAddress> addrs) throws IgniteSpiException {

        }

        /** {@inheritDoc} */
        @Override public void close() {

        }
    }
}
