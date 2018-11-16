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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.jira.IJiraIntegration;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.CompactContributionKey;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.web.model.VisaRequest;
import org.apache.ignite.ci.web.model.hist.VisasHistoryStorage;
import org.apache.ignite.internal.util.typedef.X;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks observed builds for finished status and comments JIRA ticket.
 */
public class ObserverTask extends TimerTask {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(ObserverTask.class);

    /** */
    public static final String BUILDS_CACHE_NAME = "compactBuildsInfosCache";

    /** Helper. */
    @Inject private ITcHelper tcHelper;

    /** Helper. */
    @Inject private IJiraIntegration jiraIntegration;

    /** Ignite. */
    @Inject private Ignite ignite;

    /** */
    @Inject private VisasHistoryStorage visasHistStorage;

    /** */
    @Inject private IStringCompactor strCompactor;

    /** */
    private ReentrantLock observationLock = new ReentrantLock();

    /**
     */
    ObserverTask() {
    }

    /** */
    private IgniteCache<CompactContributionKey, CompactBuildsInfo> compactInfos() {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(BUILDS_CACHE_NAME));
    }

    /** */
    @Nullable public BuildsInfo getInfo(ContributionKey key) {
        CompactBuildsInfo compactBuildsInfo = compactInfos().get(new CompactContributionKey(key, strCompactor));

        return Objects.isNull(compactBuildsInfo) ? null : compactBuildsInfo.toBuildInfo(strCompactor);
    }


    /** */
    public Collection<BuildsInfo> getInfos() {
        List<BuildsInfo> buildsInfos = new ArrayList<>();

        compactInfos().forEach(entry -> buildsInfos.add(entry.getValue().toBuildInfo(strCompactor)));

        return buildsInfos;
    }

    /** */
    public void addInfo(BuildsInfo info) {
        observationLock.lock();

        try {
            visasHistStorage.put(new VisaRequest(info));

            compactInfos().put(new CompactContributionKey(info.getContributionKey(), strCompactor),
                new CompactBuildsInfo(info, strCompactor));
        }
        finally {
            observationLock.unlock();
        }
    }

    /** */
    private void removeBuildInfo(CompactContributionKey key) {
        try {
            BuildsInfo buildsInfo = compactInfos().get(key).toBuildInfo(strCompactor);

            boolean rmv = compactInfos().remove(key);

            Preconditions.checkState(rmv, "Key not found: " + key.toContributionKey(strCompactor).toString());
        }
        catch (Exception e) {
            logger.error("Cache remove: " + e.getMessage(), e);

            throw new RuntimeException("Observer queue: " +
                getInfos().stream().map(bi -> bi.getContributionKey().toString())
                    .collect(Collectors.joining(", ")) +
                " Error: " + X.getFullStackTrace(e));
        }
    }

    /** */
    public void removeBuildInfo(ContributionKey key) {
        observationLock.lock();

        try {
            removeBuildInfo(new CompactContributionKey(key, strCompactor));
        }
        finally {
            observationLock.unlock();
        }
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
        observationLock.lock();

        try {
            if (!tcHelper.isServerAuthorized())
                return "Server authorization required.";

            int checkedBuilds = 0;
            int notFinishedBuilds = 0;
            Set<String> ticketsNotified = new HashSet<>();

            Map<CompactContributionKey, Boolean> rmv = new HashMap<>();

            for (IgniteCache.Entry<CompactContributionKey, CompactBuildsInfo> entry : compactInfos()) {
                CompactBuildsInfo compactInfo = entry.getValue();

                BuildsInfo info = compactInfo.toBuildInfo(strCompactor);

                IAnalyticsEnabledTeamcity teamcity = tcHelper.server(info.srvId, tcHelper.getServerAuthorizerCreds());

                checkedBuilds += info.buildsCount();

                if (info.isCancelled(teamcity)) {
                    rmv.put(entry.getKey(), false);

                    logger.error("JIRA will not be commented." +
                        " [ticket: " + info.ticket + ", branch:" + info.branchForTc + "] : " +
                        "one or more re-runned blocker's builds finished with UNKNOWN status.");

                    continue;
                }

                if (!info.isFinished(teamcity)) {
                    notFinishedBuilds += info.buildsCount() - info.finishedBuildsCount(teamcity);

                    continue;
                }

                Visa visa = visasHistStorage.getVisaRequest(info.getContributionKey(), info.date).getResult();

                if (Objects.isNull(visa))
                    continue;

                if (!visa.isSuccess()) {
                    ICredentialsProv creds = tcHelper.getServerAuthorizerCreds();

                    visa = jiraIntegration.notifyJira(info.srvId, creds, info.buildTypeId,
                        info.branchForTc, info.ticket);

                    visasHistStorage.updateVisaRequestResult(info.getContributionKey(), info.date, visa);

                    if (visa.isSuccess())
                        ticketsNotified.add(info.ticket);
                }

                if (visa.isSuccess())
                    rmv.put(entry.getKey(), false);
            }

            rmv.entrySet().forEach(entry -> {
                try {
                    removeBuildInfo(entry.getKey());

                    entry.setValue(true);
                }
                catch (Exception e) {
                   logger.error(e.getMessage(), e);
                }
            });

            return "Checked " + checkedBuilds + " not finished " + notFinishedBuilds + " notified: " + ticketsNotified +
                " Rmv problems: " + rmv.values().stream().filter(v -> !v).count();
        }
        finally {
            observationLock.unlock();
        }
    }
}
