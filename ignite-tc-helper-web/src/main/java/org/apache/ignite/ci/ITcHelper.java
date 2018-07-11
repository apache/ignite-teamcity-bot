package org.apache.ignite.ci;

import java.util.List;
import org.apache.ignite.ci.issue.IssueDetector;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.UserAndSessionsStorage;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Created by Дмитрий on 25.02.2018
 */
public interface ITcHelper {
    IssuesStorage issues();

    IssueDetector issueDetector();

    IAnalyticsEnabledTeamcity server(String srvId, @Nullable ICredentialsProv prov);

    ITcAnalytics tcAnalytics(String serverId);

    UserAndSessionsStorage users();

    String primaryServerId();

    Collection<String> getServerIds();

    List<String> getTrackedBranchesIds();
}
