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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
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
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.Visa;
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

    /** */
    private static final String OBSERV_SEQ = "observationsSequence";

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
    private IgniteCache<Integer, CompactBuildsInfo> compactInfos() {
        return ignite.getOrCreateCache(TcHelperDb.getCacheV2TxConfig(BUILDS_CACHE_NAME));
    }

    /** */
    @Nullable public BuildsInfo getInfo(String srv, String branchForTc) {
        Integer key = strCompactor.getStringIdIfPresent(srv + branchForTc);

        if (Objects.isNull(key))
            return null;

        return compactInfos().get(key).toBuildInfo(strCompactor);
    }


    /** */
    public Collection<BuildsInfo> getInfos() {
        List<BuildsInfo> buildsInfos = new ArrayList<>();

        compactInfos().forEach(entry -> buildsInfos.add(entry.getValue().toBuildInfo(strCompactor)));

        return buildsInfos;
    }

    /** */
    public void addInfo(BuildsInfo info) {
        int infoKey = strCompactor.getStringId(info.srvId + info.branchForTc);

        compactInfos().put(infoKey, new CompactBuildsInfo(info, strCompactor));
    }

    /** */
    public boolean removeBuildInfo(String srv, String branchForTc) {
        observationLock.lock();

        try {
            Integer key = strCompactor.getStringIdIfPresent(srv + branchForTc);

            if (Objects.isNull(key))
                return false;

            return compactInfos().remove(key);
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

            ObjectMapper objMapper = new ObjectMapper();

            List<Integer> queuedVisas = new ArrayList<>();

            List<String> errors = new ArrayList<>();

            for (Cache.Entry<Integer, CompactBuildsInfo> entry : compactInfos()) {
                CompactBuildsInfo compactInfo = entry.getValue();

                Integer infoKey = entry.getKey();

                BuildsInfo info = compactInfo.toBuildInfo(strCompactor);

                IAnalyticsEnabledTeamcity teamcity = tcHelper.server(info.srvId, tcHelper.getServerAuthorizerCreds());

                queuedVisas.add(infoKey);

                checkedBuilds += info.buildsCount();

                if (info.isFinishedWithFailures(teamcity)) {
                    try {
                        boolean rmv = compactInfos().remove(infoKey);

                        Preconditions.checkState(rmv, "Key not found: " + infoKey);
                    }
                    catch (Exception e) {
                        logger.error("Cache remove: " + e.getMessage(), e);

                        errors.add(X.getFullStackTrace(e));
                    }

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

                visasHistStorage.updateVisaRequestResult(info.getContributionKey(), info.date, visa);

                if (visa.isSuccess()) {
                    ticketsNotified.add(info.ticket);

                    try {
                        boolean rmv = compactInfos().remove(infoKey);

                        Preconditions.checkState(rmv, "Key not found: " + infoKey);
                    }
                    catch (Exception e) {
                        logger.error("Cache remove: " + e.getMessage(), e);

                        errors.add(X.getFullStackTrace(e));
                    }
                }
            }

            return "Checked " + checkedBuilds + " not finished " + notFinishedBuilds + " notified: " + ticketsNotified +
                " Visas in queue: [" + queuedVisas.stream()
                .map(id -> String.valueOf(id))
                .collect(Collectors.joining(", ")) + "] Errors: [{" + String.join("}, {", errors) + "}]";
        }
        finally {
            observationLock.unlock();
        }
    }
}
