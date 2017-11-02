package org.apache.ignite.ci.web.rest;

import com.google.common.base.Strings;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteScheduler;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.Expirable;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.FailureDetails;
import org.apache.ignite.lang.IgniteFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.runners.PrintChainResults.loadChainContext;
import static org.apache.ignite.ci.runners.PrintChainResults.printChainResults;

@Path(CurrentFailures.CURRENT)
@Produces(MediaType.APPLICATION_JSON)
public class CurrentFailures {
    public static final String CURRENT = "current";
    @Context
    private ServletContext context;

    @GET
    public FailureDetails getTestFails(@Nullable @QueryParam("branch") String branchOrNull) {
        Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);
        final String key = Strings.nullToEmpty(branchOrNull);
        final IgniteCache<String, Expirable<FailureDetails>> currCache = ignite.getOrCreateCache(CURRENT);
        final Expirable<FailureDetails> expirable = currCache.get(key);
        if (expirable != null) {
            final long ts = getAgeTs(expirable);
            if (ts > TimeUnit.MINUTES.toMillis(1)) {
                //need update but can return current
                final IgniteScheduler scheduler = ignite.scheduler();
                //todo some locking and check if it is already scheduled

                final IgniteFuture<FailureDetails> fut = scheduler.callLocal(() -> {
                    System.err.println("Running background upload");
                    final FailureDetails res = getTestFailsNoCache(branchOrNull, ignite);
                    currCache.put(key, new Expirable<>(res));
                    return res;
                });


                final FailureDetails curData = expirable.getData();
                curData.updateRequired = true;
                return curData;
            }
            else {
                final FailureDetails data = expirable.getData();
                data.updateRequired = false;
                return data; // return and do nothing
            }
        }
        final FailureDetails res = getTestFailsNoCache(branchOrNull, ignite);
        currCache.put(key, new Expirable<>(res));
        res.updateRequired = false;
        return res;
    }

    private long getAgeTs(Expirable<FailureDetails> expirable) {
        return System.currentTimeMillis() - expirable.getTs();
    }

    @NotNull private FailureDetails getTestFailsNoCache(@Nullable @QueryParam("branch") String branch, Ignite ignite) {
        final FailureDetails res = new FailureDetails();

        boolean includeLatestRebuild = true;
        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
            String suiteId = "Ignite20Tests_RunAll";
            //todo config branches and its names
            String branchPub =
                (isNullOrEmpty(branch) || "master".equals(branch)) ? "<default>" : "pull/2508/head";
            Optional<FullChainRunCtx> pubCtx = loadChainContext(teamcity, suiteId, branchPub, includeLatestRebuild);

            final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
            chainStatus.serverName = teamcity.serverId();

            final Map<String, IgnitePersistentTeamcity.RunStat> map = teamcity.runTestAnalysis();

            pubCtx.ifPresent(ctx -> chainStatus.initFromContext(teamcity, ctx, map));

            res.servers.add(chainStatus);
        }

        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
            String suiteId = "id8xIgniteGridGainTests_RunAll";
            //todo config
            String branchPriv =
                (isNullOrEmpty(branch) || "master".equals(branch)) ? "<default>" : "ignite-2.1.5";
            Optional<FullChainRunCtx> privCtx = loadChainContext(teamcity, suiteId, branchPriv, includeLatestRebuild);

            final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
            chainStatus.serverName = teamcity.serverId();
            final Map<String, IgnitePersistentTeamcity.RunStat> map = teamcity.runTestAnalysis();
            privCtx.ifPresent(ctx -> chainStatus.initFromContext(teamcity, ctx, map));
            res.servers.add(chainStatus);
        }
        return res;
    }

}
