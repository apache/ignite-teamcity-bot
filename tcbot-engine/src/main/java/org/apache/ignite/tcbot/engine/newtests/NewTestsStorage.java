package org.apache.ignite.tcbot.engine.newtests;

import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.tcbot.persistence.CacheConfigs;

/**
 * The storage contains tests which were identified as new tests in the tcbot visa
 */
public class NewTestsStorage {
    /** */
    @Inject
    private Provider<Ignite> igniteProvider;

    /** */
    private IgniteCache<String, NewTestInfo> cache() {
        return botNewTestsCache(getIgnite());
    }

    /** */
    private Ignite getIgnite() {
        return igniteProvider.get();
    }

    /** */
    public static IgniteCache<String, NewTestInfo> botNewTestsCache(Ignite ignite) {
        CacheConfiguration<String, NewTestInfo> ccfg = CacheConfigs.getCache8PartsConfig("newTestsCache");

        return ignite.getOrCreateCache(ccfg);
    }

    /** */
    public boolean isNewTest(String branch, String testId, String srvId) {
        NewTestInfo savedTest = cache().get(testId + srvId);

        if (savedTest == null)
            return true;
        else
            return savedTest.branch().equals(branch);
    }

    /** */
    public boolean isNewTestAndPut(String branch, String testId, String srvId) {
        NewTestInfo savedTest = cache().get(testId + srvId);

        if (savedTest == null) {
            cache().put(testId + srvId, new NewTestInfo(branch, System.currentTimeMillis()));
            return true;
        }
        else
            return savedTest.branch().equals(branch);
    }

    /** */
    public void removeOldTests(long thresholdDate) {
        ScanQuery<String, NewTestInfo> scan =
            new ScanQuery<>((key, testInfo) -> testInfo.timestamp() < thresholdDate);

        for (Cache.Entry<String, NewTestInfo> entry : cache().query(scan))
            cache().remove(entry.getKey());
    }
}
