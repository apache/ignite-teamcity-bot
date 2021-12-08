package org.apache.ignite.ci.tcbot.conf;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
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

        Stream<ITrackedBranch> fileBasedBranches = filesBasedCfg.getConfig().branchesStream();

        IgniteCache<String, BranchTrackedPersisted> cache = igniteProvider.get().getOrCreateCache(CacheConfigs.getCacheV2Config(TRACKED_BRANCHES));

        Map<String, BranchTrackedPersisted> res = new HashMap<>();
        fileBasedBranches.map(b -> BranchTrackedPersisted.initFrom(b)).forEach(btp -> {
            res.put(btp.name(), btp);
        });


        return res.values().stream().map(v -> v);
    }

    @Override
    public Collection<String> getServerIds() {
        return filesBasedCfg.getConfig().getServerIds();
    }
}
