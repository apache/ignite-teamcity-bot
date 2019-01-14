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
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import javax.inject.Inject;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.jira.pure.IJiraIntegration;
import org.apache.ignite.ci.jira.pure.IJiraIntegrationProvider;
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.apache.ignite.ci.web.model.Visa;
import org.apache.ignite.ci.web.model.VisaRequest;
import org.apache.ignite.ci.web.model.hist.VisasHistoryStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks observed builds for finished status and comments JIRA ticket.
 * All observations are mapped with {@link ContributionKey} which are produced
 * from BuildsInfo and used as a key for specific observation. It interacts
 * with {@link VisasHistoryStorage} as persistent storage. For more information
 * see package-info.
 */
public class ObserverTask extends TimerTask {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(ObserverTask.class);

    /** Helper. */
    @Inject private ITcHelper tcHelper;

    /** */
    @Inject private ITeamcityIgnitedProvider teamcityIgnitedProvider;

    /** */
    @Inject private IJiraIntegrationProvider jiraIntegrationProvider;

    /** */
    @Inject private VisasHistoryStorage visasHistStorage;

    @Inject private TcBotTriggerAndSignOffService visaIssuer;

    /** */
    private ReentrantLock observationLock = new ReentrantLock();

    /** */
    private Map<ContributionKey, BuildsInfo> infos = new ConcurrentHashMap<>();

    /** */
    @Inject private IStringCompactor strCompactor;

    /**
     */
    ObserverTask() {
    }

    /**
     * Connects to {@link VisasHistoryStorage} to get observed
     * {@link VisaRequest}. Chiefly it's used for reconstructing
     *  observations after server restart.
     */
    public void init() {
        visasHistStorage.getLastVisas().stream()
            .filter(req -> req.isObserving())
            .forEach(req -> infos.put(req.getInfo().getContributionKey(), req.getInfo()));
    }

    /** */
    @Nullable public BuildsInfo getInfo(ContributionKey key) {
        return infos.get(key);
    }


    /** */
    public Collection<BuildsInfo> getInfos() {
        return infos.values();
    }

    /**
     * Add {@link BuildsInfo} for observation.
     * Observation with similar to given {@link ContributionKey} will be
     * overwritten.
     */
    public void addInfo(BuildsInfo info) {
        visasHistStorage.updateLastVisaRequest(info.getContributionKey(), req -> req.setObservingStatus(false));

        visasHistStorage.put(new VisaRequest(info).setObservingStatus(true));

        infos.put(info.getContributionKey(), info);
    }

    /** */
    public boolean removeBuildInfo(ContributionKey key) {
        observationLock.lock();

        try {
            if (!infos.containsKey(key))
                return false;

            infos.remove(key);

            visasHistStorage.updateLastVisaRequest(key, req -> req.setObservingStatus(false));

            return true;
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
     * That method is runned by {@link ObserverTask} scheduled.
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

            List<ContributionKey> rmv = new ArrayList<>();

            for (ContributionKey key : infos.keySet()) {
                BuildsInfo info = infos.get(key);

                ITeamcityIgnited teamcity = teamcityIgnitedProvider.server(info.srvId,
                    tcHelper.getServerAuthorizerCreds());

                checkedBuilds += info.buildsCount();

                if (info.isCancelled(teamcity, strCompactor)) {
                    rmv.add(key);

                    logger.error("JIRA will not be commented." +
                        " [ticket: " + info.ticket + ", branch:" + info.branchForTc + "] : " +
                        "one or more re-runned blocker's builds finished with UNKNOWN status.");

                    continue;
                }

                if (!info.isFinished(teamcity, strCompactor)) {
                    notFinishedBuilds += info.buildsCount() - info.finishedBuildsCount(teamcity, strCompactor);

                    continue;
                }

                Visa visa = visasHistStorage.getLastVisaRequest(info.getContributionKey()).getResult();

                if (!visa.isSuccess()) {
                    ICredentialsProv creds = tcHelper.getServerAuthorizerCreds();

                    IJiraIntegration jiraIntegration = jiraIntegrationProvider.server(info.srvId);

                    Visa updatedVisa = visaIssuer.notifyJira(info.srvId, creds, info.buildTypeId,
                        info.branchForTc, info.ticket);

                    visasHistStorage.updateLastVisaRequest(info.getContributionKey(), (req -> req.setResult(updatedVisa)));

                    if (updatedVisa.isSuccess())
                        ticketsNotified.add(info.ticket);

                    visa = updatedVisa;
                }

                if (visa.isSuccess())
                    rmv.add(key);
            }

            rmv.forEach(key -> {
                infos.remove(key);

                visasHistStorage.updateLastVisaRequest(key, req -> req.setObservingStatus(false));
            });

            return "Checked " + checkedBuilds + " not finished " + notFinishedBuilds + " notified: " + ticketsNotified;
        }
        finally {
            observationLock.unlock();
        }
    }
}
