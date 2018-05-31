package org.apache.ignite.ci;

import org.apache.ignite.ci.detector.IssueDetector;
import org.apache.ignite.ci.detector.IssuesStorage;
import org.apache.ignite.ci.web.auth.ICredentialsProv;

/**
 * Created by Дмитрий on 25.02.2018
 */
public interface ITcHelper {

    IAnalyticsEnabledTeamcity server(String serverId);

    IssuesStorage issues();

    IssueDetector issueDetector();

    IAnalyticsEnabledTeamcity server(String srvId, ICredentialsProv prov);
}
