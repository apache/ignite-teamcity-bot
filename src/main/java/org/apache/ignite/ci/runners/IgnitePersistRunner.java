package org.apache.ignite.ci.runners;

import java.io.File;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.PersistentStoreConfiguration;
import org.apache.ignite.logger.java.JavaLogger;

/**
 * Created by dpavlov on 03.08.2017.
 */
public class IgnitePersistRunner {
    public static void main(String[] args) {
        final IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteHome(new File(".").getAbsolutePath());
        cfg.setConsistentId("TcHelper");
        cfg.setGridLogger(new JavaLogger());

        PersistentStoreConfiguration psCfg = new PersistentStoreConfiguration();
        cfg.setPersistentStoreConfiguration(psCfg);

        try (Ignite ignite = Ignition.start(cfg)){
            ignite.active(true);

            IgniteCache<String, String> cache = ignite.getOrCreateCache("test");
            Object test1 = cache.get("test");
            System.out.println("prev value " + test1 + " Prev size " + cache.size());

            cache.put("test", test1 + ".1");
            for (int i = 0; i < 1000; i++) {
                if (i % 100 == 0)
                    System.out.println("Total " + i + " keys saved");
                cache.put("Key-" + i, "Value" + i);
            }

            //Ignition.stop(ignite.name(), false);
        }
    }
}
