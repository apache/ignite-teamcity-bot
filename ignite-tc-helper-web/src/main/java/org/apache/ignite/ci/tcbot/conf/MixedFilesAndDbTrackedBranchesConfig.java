package org.apache.ignite.ci.tcbot.conf;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.tcbot.common.interceptor.GuavaCached;
import org.apache.ignite.tcbot.engine.conf.BranchTrackedPersisted;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranchesConfig;
import org.apache.ignite.tcbot.persistence.CacheConfigs;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class MixedFilesAndDbTrackedBranchesConfig implements ITrackedBranchesConfig {
    public static final String TRACKED_BRANCHES = "trackedBranches";
    @Inject
    private LocalFilesBasedConfig filesBasedCfg;

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;


    @Override
    public Stream<ITrackedBranch> branchesStream() {
        //todo internal cached version,
       // @GuavaCached(softValues = true, expireAfterWriteSecs = 3 * 60)
        IgniteCache<String, BranchTrackedPersisted> cache = igniteProvider.get().getOrCreateCache(CacheConfigs.getCache8PartsConfig(TRACKED_BRANCHES));

        Map<String, BranchTrackedPersisted> res = new HashMap<>();
        filesBasedCfg.getConfig()
                .getBranches()
                .stream()
                .map(BranchTrackedPersisted::initFrom).forEach(btp -> {
            res.put(btp.name(), btp);
        });

        Map<String, BranchTrackedPersisted> dbValues = new HashMap<>();
        cache.forEach((entry) -> {
            String key = entry.getKey();
            dbValues.put(key, entry.getValue());
        });

        res.putAll(dbValues); // override config all values by values from DB, enforcing soft del as a priority

        return res.values().stream().filter(BranchTrackedPersisted::isAlive).map(v -> v);
    }
}
