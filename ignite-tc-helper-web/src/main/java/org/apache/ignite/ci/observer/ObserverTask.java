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

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcServerProvider;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.ignite.ci.jira.IJiraIntegration.JIRA_COMMENTED;

/**
 * Checks observed builds for finished status and comments JIRA ticket.
 */
public class ObserverTask extends TimerTask {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(ObserverTask.class);

    /** Helper. */
    @Inject private ITcServerProvider srvProvider;

    /** Helper. */
    @Inject private IJiraIntegration jiraIntegration;

    /** Builds. */
    final Queue<BuildInfo> builds;

    /**
     */
    ObserverTask() {
        builds = new ConcurrentLinkedQueue<>();
    }

    /** {@inheritDoc} */
    @Override public void run() {
        try {
            runObserverTask();
        }
        catch (Exception e) {
            logger.error("Observer task failure: " + e.getMessage(), e);
        }
    }

    /**
     *
     */
    @AutoProfiling
    @MonitoredTask(name = "Build Observer")
    protected String runObserverTask() {
        int checkedBuilds = 0;
        int notFinihedBuilds = 0;
        Set<String> ticketsNotified = new HashSet<>();

        for (BuildInfo info : builds) {
            checkedBuilds++;
            IAnalyticsEnabledTeamcity teamcity = srvProvider.server(info.srvId, info.prov);

            Build build = teamcity.getBuild(info.build.getId());

            if (!"finished".equals(build.state)) {
                notFinihedBuilds++;

                continue;
            }

            String jiraRes = jiraIntegration.notifyJira(info.srvId, info.prov, info.build.buildTypeId,
                info.build.branchName, info.ticket);

            if (JIRA_COMMENTED.equals(jiraRes)) {
                ticketsNotified.add(info.ticket);

                builds.remove(info);
            }
        }

        return "Checked " + checkedBuilds + " not finished " + notFinihedBuilds + " notified: " + ticketsNotified;
    }
}
