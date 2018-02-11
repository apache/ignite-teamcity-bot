package org.apache.ignite.ci.web.rest;

import com.google.common.base.Strings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.analysis.FullChainRunCtx;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.model.current.ChainAtServerCurrentStatus;
import org.apache.ignite.ci.web.rest.model.current.TestFailuresSummary;
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
    public TestFailuresSummary getTestFails(@Nullable @QueryParam("branch") String branchOrNull) {
        final String key = Strings.nullToEmpty(branchOrNull);
        final BackgroundUpdater updater = (BackgroundUpdater)context.getAttribute(CtxListener.UPDATER);
        return updater.get("AllTestFailuresSummary", key, this::getAllTestFailsNoCache);
    }


    @GET
    @Path("failuresNoCache")
    @NotNull public TestFailuresSummary getAllTestFailsNoCache(@Nullable @QueryParam("branch") String branchOpt ) {
        Ignite ignite = (Ignite)context.getAttribute(CtxListener.IGNITE);
        final TestFailuresSummary res = new TestFailuresSummary();
        final String branch = isNullOrEmpty(branchOpt) ? "master" : branchOpt;
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branch);
        for (ChainAtServerTracked chainAtServerTracked : tracked.chains) {
            try (IgnitePersistentTeamcity teamcity = new IgnitePersistentTeamcity(ignite, chainAtServerTracked.serverId)) {
                final String projectId = chainAtServerTracked.getSuiteIdMandatory();
                final List<BuildRef> builds = teamcity.getFinishedBuildsIncludeSnDepFailed(
                    projectId,
                    chainAtServerTracked.getBranchForRestMandatory());
                Stream<Optional<FullChainRunCtx>> stream
                    = builds.stream().parallel()
                    .filter(b -> b.getId() != null)
                    .map(build -> BuildChainProcessor.processChainByRef(teamcity, false, build,
                        false, false, true));

                final Function<String, RunStat> map = teamcity.getTestRunStatProvider();
                final Map<String, RunStat> suiteMap = teamcity.runSuiteAnalysis();
                stream.forEach(
                    chainCtxOpt -> {
                        final ChainAtServerCurrentStatus chainStatus = new ChainAtServerCurrentStatus();
                        chainStatus.serverName = teamcity.serverId();
                        chainCtxOpt.ifPresent(chainCtx -> chainStatus.initFromContext(teamcity, chainCtx, map, suiteMap));
                        res.addChainOnServer(chainStatus);
                    }
                );

            }
        }
        return res;
    }

}
