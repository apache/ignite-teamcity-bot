package org.apache.ignite.ci;

import org.apache.ignite.ci.issue.IssueDetector;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.UserAndSessionsStorage;

/**
 * Created by Дмитрий on 25.02.2018
 */
public interface ITcHelper {

    IAnalyticsEnabledTeamcity server(String serverId);

    IssuesStorage issues();

    IssueDetector issueDetector();

    IAnalyticsEnabledTeamcity server(String srvId, ICredentialsProv prov);

    UserAndSessionsStorage users();
}
