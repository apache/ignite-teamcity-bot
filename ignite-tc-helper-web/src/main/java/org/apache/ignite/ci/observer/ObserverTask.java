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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;
import javax.cache.Cache;
import javax.inject.Inject;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.Compactor;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.web.model.hist.VisasHistoryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks observed builds for finished status and comments JIRA ticket.
 */
public class ObserverTask extends TimerTask {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(ObserverTask.class);

    /** */
    public static final String BUILDS_CACHE_NAME = "compactBuildsInfos15";

    /** Helper. */
    @Inject private ITcHelper tcHelper;

    /** Helper. */
    @Inject private IJiraIntegration jiraIntegration;

    /** Ignite. */
    @Inject private Ignite ignite;

    /** */
    @Inject private VisasHistoryStorage visasHistoryStorage;

    /** */
    @Inject private Compactor compactor;

    /**
     */
    ObserverTask() {
    }

    /** */
    private IgniteCache<Object, Object> compactInfos() {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(BUILDS_CACHE_NAME));
    }

    /** */
    public Collection<BuildsInfo> getInfos() {
        List<BuildsInfo> buildsInfos = new ArrayList<>();

        compactInfos().forEach(entry -> buildsInfos.add(compactor.unMarshall(entry.getKey(), BuildsInfo.class)));

        return buildsInfos;
    }

    /** */
    public void addInfo(BuildsInfo info) {
        compactInfos().put(compactor.marshall(info), new Object());
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

        for (Cache.Entry<Object, Object> entry : compactInfos()) {
            Object compactInfo = entry.getKey();

            BuildsInfo info = compactor.unMarshall(compactInfo, BuildsInfo.class);

            checkedBuilds += info.buildsCount();

            IAnalyticsEnabledTeamcity teamcity = tcHelper.server(info.srvId, tcHelper.getServerAuthorizerCreds());

            if (info.isFinishedWithFailures(teamcity)) {
                compactInfos().remove(compactInfo);

                logger.error("JIRA will not be commented." +
                    " [ticket: " + info.ticket + ", branch:" + info.branchForTc + "] : " +
                    "one or more re-runned blocker's builds finished with UNKNOWN status.");

                continue;
            }

            if (!info.isFinished(teamcity)) {
                notFinishedBuilds += info.buildsCount() - info.finishedBuildsCount();

                continue;
            }

            ICredentialsProv creds = tcHelper.getServerAuthorizerCreds();

            Visa visa = jiraIntegration.notifyJira(info.srvId, creds, info.buildTypeId,
                info.branchForTc, info.ticket);

            visasHistoryStorage.updateVisaRequestRes(info.produceContributionKey(), info.date, visa);

            if (visa.isSuccess()) {
                ticketsNotified.add(info.ticket);
                compactInfos().remove(compactInfo);
            }
        }

        return "Checked " + checkedBuilds + " not finished " + notFinishedBuilds + " notified: " + ticketsNotified;
    }
}
