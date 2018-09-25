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

package org.apache.ignite.ci.observer;

import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.ITcServerProvider;
import org.apache.ignite.ci.tcmodel.result.Build;

/**
 * Checks observed builds for finished status and comments JIRA ticket.
 */
public class ObserverTask extends TimerTask {
    /** Helper. */
    private final ITcHelper helper;

    /** Builds. */
    final Queue<BuildInfo> builds;

    /**
     * @param helper Helper.
     */
    ObserverTask(ITcHelper helper) {
        this.helper = helper;
        builds = new ConcurrentLinkedQueue<>();
    }

    /** {@inheritDoc} */
    @Override public void run() {
        for (BuildInfo info : builds) {
            IAnalyticsEnabledTeamcity teamcity = helper.server(info.srvId, info.prov);

            Build build = teamcity.getBuild(info.build.getId());

            if (!"finished".equals(build.state))
                continue;

            if (helper.notifyJira(info.srvId, info.prov, info.build.buildTypeId, info.build.branchName, info.ticket))
                builds.remove(info);
        }
    }
}
