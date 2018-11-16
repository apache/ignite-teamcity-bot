package org.apache.ignite.ci.teamcity.ignited.buildtype;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.tcmodel.conf.BuildTypeRef;
import org.apache.ignite.ci.tcmodel.conf.bt.BuildType;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;

import static org.apache.ignite.ci.teamcity.ignited.IgniteStringCompactor.getCache8PartsConfig;

public class BuildTypeDao {
    /** Cache name*/
    public static final String BUILD_TYPES_CACHE_NAME = "buildTypes";

    /** Ignite provider. */
    @Inject private Provider<Ignite> igniteProvider;

    /** BuildTypes cache. */
    private IgniteCache<Integer, BuildTypeRefCompacted> buildTypesCache;

    /** Compactor. */
    @Inject private IStringCompactor compactor;

    /**
     * Initialize
     */
    public void init () {
        Ignite ignite = igniteProvider.get();
        buildTypesCache = ignite.getOrCreateCache(getCache8PartsConfig(BUILD_TYPES_CACHE_NAME));
    }

    public List<String> buildTypesName() {
        return Collections.emptyList();
    }

    public List<BuildTypeRef> buildTypes() {
        return Collections.emptyList();
    }

    public List<BuildType> fullBuildTypes() {
        return Collections.emptyList();
    }

    public List<BuildTypeRef> compositeBuildTypes() {
        return Collections.emptyList();
    }

    public List<BuildTypeRef> sortedBySnDepCountBuildTypes() {
        //todo: sort by snapshot-dependecies count and contains type in other types
        return Collections.emptyList();
    }
}
