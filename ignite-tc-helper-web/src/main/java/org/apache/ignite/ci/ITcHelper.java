package org.apache.ignite.ci;

import org.apache.ignite.ci.detector.IssueDetector;
import org.apache.ignite.ci.detector.IssuesStorage;

/**
 * Created by Дмитрий on 25.02.2018
 */
public interface ITcHelper {

    IAnalyticsEnabledTeamcity server(String serverId);

    IssuesStorage issues();

    IssueDetector issueDetector();
}
