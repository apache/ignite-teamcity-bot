package org.apache.ignite.ci.web.rest.model.current;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.analysis.MultBuildRunCtx;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.tcmodel.result.tests.TestOccurrenceFull;
import org.apache.ignite.ci.web.rest.GetBuildLog;

import static org.apache.ignite.ci.util.TimeUtil.getDurationPrintable;
import static org.apache.ignite.ci.util.UrlUtil.escape;

/**
 * Represent Suite result
 */
@SuppressWarnings("WeakerAccess") public class SuiteCurrentStatus extends AbstractTestMetrics {
    /** Suite Name */
    public String name;

    /** Suite Run Result (filled if failed) */
    public String result;

    /** Web Href. to suite runs history */
    public String webToHist = "";

    /** Web Href. to suite particular run */
    public String webToBuild = "";

    /** Contact person. */
    public String contactPerson;

    public List<TestFailure> testFailures = new ArrayList<>();
    public List<TestFailure> topLongRunning = new ArrayList<>();

    /** Web Href. to thread dump display */
    @Nullable public String webUrlThreadDump;

    @Nullable public Integer runningBuildCount;
    @Nullable public Integer queuedBuildCount;

    /** TC server id. */
    public String serverId;

    /** Suite ID in teamcity identification. */
    public String suiteId;

    /** Branch name in teamcity identification. */
    public String branchName;

    /** Registered number of failures from TC helper DB */
    @Nullable public Integer failures;

    /** Registered number of runs from TC helper DB */
    @Nullable public Integer runs;

    /** Registered percent of fails from TC helper DB */
    @Nullable public String failureRate;

    public String userCommits = "";

    public void initFromContext(@Nonnull final ITeamcity teamcity,
        @Nonnull final MultBuildRunCtx suite,
        @Nullable final Function<String, RunStat> runStatSupplier,
        @Nullable final Map<String, RunStat> suiteRunStat) {

        name = suite.suiteName();
        if (!Strings.isNullOrEmpty(name) && suiteRunStat!=null) {
            final RunStat stat = suiteRunStat.get(name);
            if (stat != null) {
                failures = stat.failures;
                runs = stat.runs;
                failureRate = stat.getFailPercentPrintable();
            }
        }

        Set<String> collect = suite.lastChangeUsers().collect(Collectors.toSet());

        if(!collect.isEmpty()) {
            userCommits = collect.toString();
        }

        result = suite.getResult();
        failedTests = suite.failedTests();
        durationPrintable = getDurationPrintable(suite.getBuildDuration());
        contactPerson = suite.getContactPerson();
        webToHist = buildWebLink(teamcity, suite);
        webToBuild = buildWebLinkToBuild(teamcity, suite);

        suite.getFailedTests().forEach(occurrence -> {
            Stream<TestOccurrenceFull> stream = suite.getFullTests(occurrence);

            final TestFailure failure = new TestFailure();
            failure.initFromOccurrence(occurrence, stream, teamcity, suite);
            failure.initStat(runStatSupplier);
            testFailures.add(failure);
        });

        suite.getTopLongRunning().forEach(occurrence->{
            final TestFailure failure = new TestFailure();
            failure.initFromOccurrence(occurrence, Stream.empty(), teamcity, suite);
            failure.initStat(runStatSupplier);
            topLongRunning.add(failure);
        });

        suite.getCriticalFailLastStartedTest().forEach(
            lastTest->{
                final TestFailure failure = new TestFailure();
                failure.name = lastTest + " (last started)";
                testFailures.add(failure);
            }
        );

        if (suite.getThreadDumpFileIdx() != null) {
            webUrlThreadDump = "/rest/" + GetBuildLog.GET_BUILD_LOG + "/" + GetBuildLog.THREAD_DUMP
                + "?" + GetBuildLog.SERVER_ID + "=" + teamcity.serverId()
                + "&" + GetBuildLog.BUILD_NO + "=" + Integer.toString(suite.getBuildId())
                + "&" + GetBuildLog.FILE_IDX + "=" + Integer.toString(suite.getThreadDumpFileIdx());
        }

        runningBuildCount = suite.runningBuildCount();
        queuedBuildCount = suite.queuedBuildCount();
        serverId = teamcity.serverId();
        suiteId = suite.suiteId();
        branchName = branchForLink(suite.branchName());
    }

    private static String buildWebLinkToBuild(ITeamcity teamcity, MultBuildRunCtx suite) {
        return teamcity.host() + "viewLog.html?buildId=" + Integer.toString(suite.getBuildId());
    }

    private static String buildWebLink(ITeamcity teamcity, MultBuildRunCtx suite) {
        final String branch = branchForLink(suite.branchName());
        return teamcity.host() + "viewType.html?buildTypeId=" + suite.suiteId()
            + "&branch=" + escape(branch)
            + "&tab=buildTypeStatusDiv";
    }

    public static String branchForLink(String branchName) {
        return branchName == null || "refs/heads/master".equals(branchName) ? "<default>" : branchName;
    }
}
