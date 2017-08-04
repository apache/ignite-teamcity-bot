package org.apache.ignite.ci.db;

import java.io.File;
import java.io.IOException;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.PersistentStoreConfiguration;
import org.apache.ignite.logger.java.JavaLogger;

/**
 * Created by dpavlov on 04.08.2017
 */
public class TcHelperDb {

    public static Ignite start() {
        final IgniteConfiguration cfg = new IgniteConfiguration();
        setWork(cfg, HelperConfig.resolveWorkDir());
        cfg.setConsistentId("TcHelper");
        cfg.setGridLogger(new JavaLogger());

        PersistentStoreConfiguration psCfg = new PersistentStoreConfiguration();
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
}
