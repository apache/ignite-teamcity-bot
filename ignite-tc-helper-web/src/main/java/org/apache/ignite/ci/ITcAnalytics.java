package org.apache.ignite.ci;

import java.util.List;
import java.util.function.Function;
import org.apache.ignite.ci.analysis.RunStat;
import org.apache.ignite.ci.analysis.SuiteInBranch;
import org.apache.ignite.ci.analysis.TestInBranch;

public interface ITcAnalytics {
    List<RunStat> topTestFailing(int cnt);

    List<RunStat> topTestsLongRunning(int cnt);

    /**
     * Return build statistics for default branch provider
     * @return map from suite ID to its run statistics
     */
    Function<SuiteInBranch, RunStat> getBuildFailureRunStatProvider();

    /**
     * @return map from test full name (suite: suite.test) and its branch to its run statistics
     */
    Function<TestInBranch, RunStat> getTestRunStatProvider();


    List<RunStat> topFailingSuite(int cnt);


    String getThreadDumpCached(Integer buildId);

}
