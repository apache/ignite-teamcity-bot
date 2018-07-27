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
