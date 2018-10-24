package org.apache.ignite.ci.tcbot.condition;

import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;

public class BuildConditionDao {
    /** Cache name*/
    public static final String BUILD_CONDITIONS_CACHE_NAME = "buildConditions";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** Builds cache. */
    private IgniteCache<Long, BuildConditionCompacted> buildsCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * Initialize
     */
    public void init () {
        Ignite ignite = igniteProvider.get();
        buildsCache = ignite.getOrCreateCache(TcHelperDb.getCacheV2Config(BUILD_CONDITIONS_CACHE_NAME));
    }

    /**
     * @param srvIdMaskHigh Server id mask high.
     * @param buildId Build id.
     */
    private long buildIdToCacheKey(long srvIdMaskHigh, int buildId) {

        return (long)buildId | srvIdMaskHigh << 32;
    }

    public BuildCondition getBuildCondition(long srvIdMaskHigh, int buildId) {
        long key = buildIdToCacheKey(srvIdMaskHigh, buildId);

        return buildsCache.containsKey(key) ? buildsCache.get(key).toBuildCondition(compactor) : null;
    }

    public boolean setBuildCondition(long srvIdMaskHigh, BuildCondition cond) {
        long key = buildIdToCacheKey(srvIdMaskHigh, cond.buildId);

        if (cond.isValid)
            return buildsCache.remove(key);
        else {
            if (!buildsCache.containsKey(key)) {
                buildsCache.put(key, new BuildConditionCompacted(compactor, cond));

                return true;
            }
        }

        return false;
    }
}
