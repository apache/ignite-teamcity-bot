package org.apache.ignite.ci.jira;

import org.apache.ignite.ci.user.ICredentialsProv;

public interface IJiraIntegration {
    /**
     * @param srvId TC Server ID to take information about token from.
     * @param prov Credentials.
     * @param buildTypeId Suite name.
     * @param branchForTc Branch for TeamCity.
     * @param ticket JIRA ticket full name.
     * @return {@code True} if JIRA was notified.
     */
    boolean notifyJira(String srvId, ICredentialsProv prov, String buildTypeId, String branchForTc, String ticket);
}
