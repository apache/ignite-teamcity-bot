package org.apache.ignite.ci.web.rest;

import com.google.common.base.Strings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.runners.PrintChainResults;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.FailureDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;

@Path(GetAllTestFailures.ALL)
@Produces(MediaType.APPLICATION_JSON)
public class GetAllTestFailures {
    public static final String ALL = "all";
    @Context
    private ServletContext context;

    @GET
    @Path("failures")
    public FailureDetails getTestFails(@Nullable @QueryParam("branch") String branchOrNull) {
        final String key = Strings.nullToEmpty(branchOrNull);
        final BackgroundUpdater updater = (BackgroundUpdater)context.getAttribute(CtxListener.UPDATER);
        return updater.get(ALL, key, this::getAllTestFailsNoCache);
    }


    @GET
    @Path("failuresNoCache")
    @NotNull public FailureDetails getAllTestFailsNoCache(@Nullable @QueryParam("branch") String branch ) {
        Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);
        final FailureDetails res = new FailureDetails();

        boolean includeLatestRebuild = true;
        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
            String suiteId = "Ignite20Tests_RunAll";
            //todo config branches and its names
            String branchPub =
                (isNullOrEmpty(branch) || "master".equals(branch)) ? "<default>" : "pull/2508/head";
            final List<BuildRef> builds = teamcity.getFinishedBuildsIncludeSnDepFailed(suiteId, branchPub);
            Stream<Optional<FullChainRunCtx>> stream
                = builds.stream().parallel()
                .filter(b->b.getId()!=null)
                .map(build -> PrintChainResults.processChainByRef(teamcity, false, build, false));

            final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
            chainStatus.serverName = teamcity.serverId();

            final Map<String, IgnitePersistentTeamcity.RunStat> map = teamcity.runTestAnalysis();
            stream.forEach(
                pubCtx -> {
                    pubCtx.ifPresent(ctx -> chainStatus.initFromContext(teamcity, ctx, map));
                    res.servers.add(chainStatus);
                }
            );

        }

        try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
            String suiteId = "id8xIgniteGridGainTests_RunAll";
            //todo config
            String branchPriv =
                (isNullOrEmpty(branch) || "master".equals(branch)) ? "<default>" : "ignite-2.1.5";
            final List<BuildRef> builds = teamcity.getFinishedBuildsIncludeSnDepFailed(suiteId, branchPriv);
            Stream<Optional<FullChainRunCtx>> stream
                = builds.stream().parallel()
                .filter(b->b.getId()!=null)
                .map(build -> PrintChainResults.processChainByRef(teamcity, false, build, false));

            final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
            chainStatus.serverName = teamcity.serverId();
            final Map<String, IgnitePersistentTeamcity.RunStat> map = teamcity.runTestAnalysis();
            stream.forEach(
                privCtx -> {
                    privCtx.ifPresent(ctx -> chainStatus.initFromContext(teamcity, ctx, map));
                    res.servers.add(chainStatus);
                }
            );
        }
        return res;
    }

}
