package org.apache.ignite.ci.web.model.current;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletContext;
import org.apache.ignite.ci.BuildChainProcessor;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrence;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrences;
import org.apache.ignite.ci.tcmodel.result.tests.TestRef;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.BackgroundUpdater;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.ignite.ci.web.rest.build.GetBuildTestFailures.BUILDS_STATISTICS_SUMMARY_CACHE_NAME;

public class BuildsHistory {
    private String srvId;

    private String buildTypeId;

    private String branchName;

    private Date sinceDateFilter;

    private Date untilDateFilter;

    public List<BuildStatisticsSummary> buildsStatistics = new ArrayList<>();

    public Map<String, List<TestRef>> mergedTestsBySuites = new HashMap<>();

    public void initialize(ICredentialsProv prov, ServletContext context) {
        ITcHelper tcHelper = CtxListener.getTcHelper(context);

        IAnalyticsEnabledTeamcity teamcity = tcHelper.server(srvId, prov);
            int[] finishedBuildsIds = teamcity.getBuildNumbersFromHistory(buildTypeId, branchName,
                sinceDateFilter, untilDateFilter);

            initBuildsStatistics(teamcity, prov, context, finishedBuildsIds);

            initBuildsMergedFailedTests(teamcity, finishedBuildsIds);
    }

    private void initBuildsStatistics(IAnalyticsEnabledTeamcity teamcity, ICredentialsProv prov, ServletContext context, int[] buildIds) {
        for (int buildId : buildIds) {
            FullQueryParams buildParams = new FullQueryParams();

            buildParams.setBuildId(buildId);

            buildParams.setBranch(branchName);

            buildParams.setServerId(srvId);

            BuildStatisticsSummary buildsStatistic = CtxListener.getBackgroundUpdater(context).get(
                BUILDS_STATISTICS_SUMMARY_CACHE_NAME, prov, buildParams,
                (k) -> {
                    BuildStatisticsSummary stat = new BuildStatisticsSummary(buildId);

                    stat.initialize(teamcity);

                    return stat;
                }, false);

            if (buildsStatistic != null && !buildsStatistic.isFakeStub)
                buildsStatistics.add(buildsStatistic);
        }
    }

    private void initBuildsMergedFailedTests(IAnalyticsEnabledTeamcity teamcity, int[] buildIds) {
        Map<String, Map<String, CompletableFuture<TestRef>>> mergedTestsFutures = new HashMap<>();

        for (int buildId : buildIds)
            mergeBuildTestsFuturesWith(mergedTestsFutures, teamcity, buildId);

        mergedTestsFutures.entrySet().forEach(entry ->  {
            List<TestRef> tests =
                mergedTestsBySuites.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());

            entry.getValue().values().forEach( v -> {
                try {
                    tests.add(v.get(1, TimeUnit.MINUTES));
                } catch (TimeoutException e){
                    e.printStackTrace();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private void mergeBuildTestsFuturesWith(Map<String, Map<String, CompletableFuture<TestRef>>> testFutures,
        IAnalyticsEnabledTeamcity teamcity, int buildId) {
        Build build = teamcity.getBuild(teamcity.getBuildHrefById(buildId));

        if (build == null || build.isFakeStub() || build.isSuccess() )
            return;

        if (build.isComposite()) {
            for (BuildRef buildRef : build.getSnapshotDependenciesNonNull()) {
                if (buildRef.isSuccess())
                    continue;

                mergeBuildTestsFuturesWith(testFutures, teamcity, buildRef.getId());
            }

            return;
        }

        if (build.testOccurrences == null || build.testOccurrences.href == null)
            return;

        TestOccurrences testOccurrences = teamcity.getFailedUnmutedTests(build.testOccurrences.href,
            BuildChainProcessor.normalizeBranch(build.branchName));

        Map<String, CompletableFuture<TestRef>> map = testFutures.computeIfAbsent(build.buildTypeId,
            k -> new HashMap<>());

        for (TestOccurrence testOccurrence : testOccurrences.getTests())
            map.computeIfAbsent(testOccurrence.getName(), k -> teamcity.getTestRef(testOccurrence));
    }

    public BuildsHistory(Builder builder) {
        this.srvId = builder.srvId;

        this.buildTypeId = builder.buildTypeId;

        this.branchName = builder.branchName;

        this.sinceDateFilter = builder.sinceDate;

        this.untilDateFilter = builder.untilDate;
    }

    public static class Builder {
        private String srvId = "apache";

        private String buildTypeId = "IgniteTests24Java8_RunAll";

        private String branchName = "refs/heads/master";

        private Date sinceDate = null;

        private Date untilDate = null;

        private DateFormat dateFormat = new SimpleDateFormat("ddMMyyyyHHmmss");

        public Builder server(String srvId) {
            if (!isNullOrEmpty(srvId))
                this.srvId = srvId;

            return this;
        }

        public Builder buildType(String buildType) {
            if (!isNullOrEmpty(buildType))
                this.buildTypeId = buildType;

            return this;
        }

        public Builder branch(String branchName) {
            if (!isNullOrEmpty(branchName))
                this.branchName = branchName;

            return this;
        }

        public Builder sinceDate(String sinceDate) throws ParseException {
            if (!isNullOrEmpty(sinceDate))
                this.sinceDate = dateFormat.parse(sinceDate);

            return this;
        }

        public Builder untilDate(String untilDate) throws ParseException {
            if (!isNullOrEmpty(untilDate))
                this.untilDate = dateFormat.parse(untilDate);

            return this;
        }

        public BuildsHistory build() {
            return new BuildsHistory(this);
        }
    }
}
