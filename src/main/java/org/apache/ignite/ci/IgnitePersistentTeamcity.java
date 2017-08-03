package org.apache.ignite.ci;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.ignite.ci.model.conf.BuildType;

/**
 * Created by dpavlov on 03.08.2017
 */
public class IgnitePersistentTeamcity implements ITeamcity {

    @Override public CompletableFuture<List<BuildType>> getProjectSuites(String projectId) {
        return null;
    }

    @Override public void close() throws Exception {

    }
}
