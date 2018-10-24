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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.TimerTask;
import javax.inject.Inject;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.configuration.CollectionConfiguration;
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
    @Inject private ITcHelper tcHelper;

    /** Helper. */
    @Inject private IJiraIntegration jiraIntegration;

    /** Ignite. */
    @Inject private Ignite ignite;

    /**
     */
    ObserverTask() {
    }

    /** */
    private Queue<BuildsInfo> buildsQueue() {
        return ignite.queue("buildsQueue", 0, new CollectionConfiguration());
    }

    /** */
    public Collection<BuildsInfo> getBuilds() {
        return Collections.unmodifiableCollection(buildsQueue());
    }

    /** */
    public void addBuild(BuildsInfo build) {
        buildsQueue().add(build);
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
        if (!tcHelper.isServerAuthorized())
            return "Server authorization required.";

        int checkedBuilds = 0;
        int notFinishedBuilds = 0;
        Set<String> ticketsNotified = new HashSet<>();

        Queue<BuildsInfo> builds = buildsQueue();

        for (BuildsInfo info : builds) {
            checkedBuilds += info.buildsCount();

            IAnalyticsEnabledTeamcity teamcity = tcHelper.server(info.srvId, tcHelper.getServerAuthorizerCreds());

            if (!info.isFinished(teamcity)) {
                notFinishedBuilds += info.buildsCount() - info.finishedBuildsCount();

                continue;
            }

            ICredentialsProv creds = tcHelper.getServerAuthorizerCreds();

            String jiraRes = jiraIntegration.notifyJira(info.srvId, creds, info.buildTypeId,
                info.branchName, info.ticket);

            if (JIRA_COMMENTED.equals(jiraRes)) {
                ticketsNotified.add(info.ticket);

                builds.remove(info);
            }
        }

        return "Checked " + checkedBuilds + " not finished " + notFinishedBuilds + " notified: " + ticketsNotified;
    }
}
