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

import java.util.Objects;
import java.util.Timer;
import javax.inject.Inject;
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class BuildObserver {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(BuildObserver.class);

    /** Time between observing actions in milliseconds. */
    private static final long PERIOD = 10 * 60 * 1_000;

    /** Timer. */
    private final Timer timer;

    /** Task, which should be done periodically. */
    private ObserverTask observerTask;

    /** Helper. */
    @Inject private ITcHelper tcHelper;

    /**
     */
    @Inject
    public BuildObserver(ObserverTask observerTask) {
        timer = new Timer();

        timer.schedule(observerTask, 0, PERIOD);

        this.observerTask = observerTask;
    }

    /**
     * Stop observer.
     */
    public void stop() {
        timer.cancel();
    }

    /** */
    public ObserverTask getObserverTask() {
        return observerTask;
    }

    /** */
    public boolean stopObservation(ContributionKey key) {
        try {
            observerTask.removeBuildInfo(key);

            return true;
        }
        catch (Exception e) {
            logger.error("Observation stop: " + e.getMessage(), e);

            return false;
        }
    }

    /**
     * @param srvId Server id.
     * @param prov Credentials.
     * @param branchForTc Branch for TC.
     * @param ticket JIRA ticket name.
     */
    public void observe(String srvId, ICredentialsProv prov, String ticket, String branchForTc, Build... builds) {
        BuildsInfo buildsInfo = new BuildsInfo(srvId, prov, ticket, branchForTc, builds);

        observerTask.addInfo(buildsInfo);
    }

    /**
     * @param srvId Server id.
     * @param branch Branch.
     */
    public String getObservationStatus(ContributionKey key) {
        StringBuilder sb = new StringBuilder();

        BuildsInfo buildsInfo = observerTask.getInfo(key);

        ICredentialsProv creds = tcHelper.getServerAuthorizerCreds();

        IAnalyticsEnabledTeamcity teamcity = tcHelper.server(key.srvId, creds);

        if (Objects.nonNull(buildsInfo)) {
            sb.append(buildsInfo.ticket).append(" to be commented, waiting for builds. ");
            sb.append(buildsInfo.finishedBuildsCount(teamcity));
            sb.append(" builds done from ");
            sb.append(buildsInfo.buildsCount());
        }

        return sb.toString();
    }
}
