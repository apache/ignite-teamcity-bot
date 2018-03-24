package org.apache.ignite.ci;

import java.util.List;
import java.util.function.Function;
import org.apache.ignite.ci.analysis.RunStat;

public interface ITcAnalytics {
    /**
     * @return map from test full name (suite: suite.test) to its run statistics
     */
    Function<String, RunStat> getTestRunStatProvider();

    List<RunStat> topTestFailing(int cnt);

    List<RunStat> topTestsLongRunning(int cnt);

    /**
     * @return map from suite ID to its run statistics
     */
    Function<String, RunStat> getBuildFailureRunStatProvider();

    List<RunStat> topFailingSuite(int cnt);


    String getThreadDumpCached(Integer buildId);

}
