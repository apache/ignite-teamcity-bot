package org.apache.ignite.ci.db;

import com.google.common.base.Throwables;
import java.io.FileNotFoundException;
import java.util.function.Consumer;
import javax.cache.Cache;
import javax.cache.CacheException;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.stat.Statistics;
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
        Cache<String, TestOccurrences> testOccurrencesCache, Consumer<TestOccurrences> save) {

        doneMigrations = doneMigrationsCache();

        applyMigration(TESTS + "-to-" + testOccurrencesCache.getName(), () -> {
            String cacheNme = ignCacheNme(TESTS);
            IgniteCache<String, TestOccurrences> tests = ignite.getOrCreateCache(cacheNme);

            int size = tests.size();
            if (size > 0) {
                int i = 0;
                for (Cache.Entry<String, TestOccurrences> entry : tests) {
                    System.out.println("Migrating entry " + i + " from " + size + ": " + entry.getKey());

                    String transformedKey = removeCountFromRef(entry.getKey());
                    TestOccurrences val = entry.getValue();

                    if (testOccurrencesCache.putIfAbsent(transformedKey, val))
                        save.accept(val);
                    
                    i++;
                }

                tests.clear();

                tests.destroy();
            }
        });
        applyMigration("RemoveStatisticsFromBuildCache", ()->{
            final IgniteCache<Object, Object> cache = ignite.getOrCreateCache(ignCacheNme(IgnitePersistentTeamcity.BUILD_RESULTS));
            
            for (Cache.Entry<Object, Object> next : cache) {
                if(next.getValue() instanceof Statistics) {
                    System.err.println("Removed incorrect entity: Statistics from build cache");
                    
                    cache.remove(next.getKey());
                }
            }
        });

        applyMigration("RemoveBuildsWithoutProjectId", () -> {
            final IgniteCache<Object, Build> cache = ignite.getOrCreateCache(ignCacheNme(IgnitePersistentTeamcity.BUILD_RESULTS));

            for (Cache.Entry<Object, Build> next : cache) {
                Build results = next.getValue();
                //non fake builds but without required data
                if (results.getId() != null)
                    if (results.getBuildType() == null || results.getBuildType().getProjectId() == null) {
                        System.err.println("Removed incorrect entity: Build without filled parameters: " + next);

                        cache.remove(next.getKey());
                    }
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
