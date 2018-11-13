/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci;

import java.util.Collection;
import java.util.List;
import org.apache.ignite.ci.issue.IssueDetector;
import org.apache.ignite.ci.issue.IssuesStorage;
import org.apache.ignite.ci.teamcity.restcached.ITcServerProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.user.UserAndSessionsStorage;
import org.apache.ignite.ci.web.model.Visa;

/**
 * Teamcity Bot main interface. This inteface became too huge.
 */
@Deprecated
public interface ITcHelper extends ITcServerProvider {
    /** System property to specify: Teamcity helper home. Ignite home will be set to same dir. */
    public String TEAMCITY_HELPER_HOME = "teamcity.helper.home";

    /**
     * Teamcity bot data storage configuration region size in gigabytes. Default is 20% of physical RAM.
     */
    public String TEAMCITY_BOT_REGIONSIZE = "teamcity.bot.regionsize";

    IssuesStorage issues();

    IssueDetector issueDetector();

    ITcAnalytics tcAnalytics(String serverId);

    UserAndSessionsStorage users();

    String primaryServerId();

    Collection<String> getServerIds();

    List<String> getTrackedBranchesIds();

    /** */
    void setServerAuthorizerCreds(ICredentialsProv creds);

    /** */
    ICredentialsProv getServerAuthorizerCreds();

    /** */
    boolean isServerAuthorized();

    /**
     * @param srvId Server id.
     * @param prov Credentials.
     * @param buildTypeId Suite name.
     * @param branchForTc Branch for TeamCity.
     * @param ticket JIRA ticket full name.
     * @return {@code Visa} which contains info about JIRA notification.
     */
    Visa notifyJira(String srvId, ICredentialsProv prov, String buildTypeId, String branchForTc, String ticket);
}
