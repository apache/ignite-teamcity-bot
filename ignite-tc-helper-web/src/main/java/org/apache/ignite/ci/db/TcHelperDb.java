package org.apache.ignite.ci.db;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.MemoryConfiguration;
import org.apache.ignite.configuration.MemoryPolicyConfiguration;
import org.apache.ignite.configuration.PersistentStoreConfiguration;
import org.apache.ignite.configuration.WALMode;
import org.apache.ignite.logger.java.JavaLogger;
import org.apache.ignite.spi.IgniteSpiContext;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;

/**
 * Created by dpavlov on 04.08.2017
 */
public class TcHelperDb {

    public static Ignite start() {
        final IgniteConfiguration cfg = new IgniteConfiguration();
        setWork(cfg, HelperConfig.resolveWorkDir());

        TcpDiscoverySpi spi = new TcpDiscoverySpi();
        int locPort = 10000;
        spi.setLocalPort(locPort);
        spi.setLocalPortRange(locPort);
        spi.setIpFinder(new LocalOnlyTcpDiscoveryIpFinder(locPort));

        cfg.setDiscoverySpi(spi);
        cfg.setConsistentId("TcHelper");
        cfg.setGridLogger(new JavaLogger());

        final MemoryConfiguration memCfg = new MemoryConfiguration();
        final MemoryPolicyConfiguration configuration = new MemoryPolicyConfiguration();
        configuration.setMaxSize(512 * 1024 * 1024);
        memCfg.setMemoryPolicies(configuration);
        cfg.setMemoryConfiguration(memCfg);

        PersistentStoreConfiguration psCfg = new PersistentStoreConfiguration();
        psCfg.setWalMode(WALMode.LOG_ONLY);
        psCfg.setWalHistorySize(1);
        cfg.setPersistentStoreConfiguration(psCfg);

        Ignite ignite = Ignition.start(cfg);
        ignite.active(true);
        return ignite;
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
