package org.apache.ignite.ci.db;

import java.util.function.Consumer;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.configuration.CacheConfiguration;

/**
 * Created by Дмитрий on 11.02.2018
 */
public class Migrations {
    public static final String DONE_MIGRATIONS = "doneMigrations";
    @Deprecated
    public static final String TESTS = "tests";
    public static final String TESTS_COUNT_7700 = ",count:7700";

    private final Ignite ignite;
    private final String serverId;
    private IgniteCache<String, Object> doneMigrations;

    public Migrations(Ignite ignite, String serverId) {
        this.ignite = ignite;
        this.serverId = serverId;
    }

    public static String removeCountFromRef(String href) {
        return href.replace(TESTS_COUNT_7700, "")
            .replace(",count:7500", "");
    }

    public void dataMigration(
        IgniteCache<String, TestOccurrences> occurrences, Consumer<TestOccurrences> save) {

        doneMigrations = doneMigrationsCache();

        applyMigration(TESTS + "-to-" + occurrences.getName(), () -> {
            String cacheNme = ignCacheNme(TESTS);
            IgniteCache<String, TestOccurrences> tests = ignite.getOrCreateCache(cacheNme);

            int size = tests.size();
            if (size > 0) {
                int i = 0;
                for (Cache.Entry<String, TestOccurrences> entry : tests) {
                    System.out.println("Migrating entry " + i + " from " + size + ": " + entry.getKey());

                    String s = removeCountFromRef(entry.getKey());
                    TestOccurrences value = entry.getValue();

                    if (occurrences.putIfAbsent(s, value)) {
                        save.accept(value);
                    }
                    i++;
                }

                tests.clear();

                tests.destroy();
            }
        });
    }

    private IgniteCache<String, Object> doneMigrationsCache() {
        String migrations = ignCacheNme(DONE_MIGRATIONS);
        CacheConfiguration<String, Object> ccfg = new CacheConfiguration<>(migrations);
        ccfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
        ccfg.setCacheMode(CacheMode.REPLICATED);

        return ignite.getOrCreateCache(ccfg);
    }

    private void applyMigration(String code, Runnable runnable) {
        if (!doneMigrations.containsKey(code)) {
            synchronized (Migrations.class) {
                System.err.println("Running migration procedure [" + code + "]");
                runnable.run();
                doneMigrations.put(code, true);
            }
        }
    }

    private String ignCacheNme(String tests) {
        return IgnitePersistentTeamcity.ignCacheNme(tests, serverId);
    }
}
