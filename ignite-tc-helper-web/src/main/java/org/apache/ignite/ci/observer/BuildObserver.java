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
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.ci.tcbot.ITcBotBgAuth;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.model.ContributionKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link BuildObserver} provides opportunity for scheduled run of {@link ObserverTask} and controlling existing and new
 * observations.
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

    @Inject private ITcBotBgAuth tcBotBgAuth;

    /** */
    @Inject private ITeamcityIgnitedProvider teamcityIgnitedProvider;

    /** */
    @Inject private IStringCompactor strCompactor;

    /**
     */
    @Inject
    public BuildObserver(ObserverTask observerTask) {
        timer = new Timer();

        timer.schedule(observerTask, 0, PERIOD);

        this.observerTask = observerTask;

        this.observerTask.init();
    }

    /**
     * Stop observer.
     */
    public void stop() {
        timer.cancel();
    }

    /**
     * {@link ObserverTask} will stop tracking status of observation for specified {@link ContributionKey}.
     *
     * @return {@code true} if observation which is connected with specified {@link ContributionKey} was stopped.
     */
    public boolean stopObservation(ContributionKey key) {
        try {
            return observerTask.removeBuildInfo(key);
        }
        catch (Exception e) {
            logger.error("Observation stop: " + e.getMessage(), e);

            return false;
        }
    }

    /**
     * @param srvId Server id.
     * @param ticket JIRA ticket name.
     * @param branchForTc Branch for TC.
     * @param baseBranchForTc Base branch in TC identification.
     * @param userName
     */
    public void observe(String srvId, String ticket, String branchForTc, String parentSuiteId,
        @Nullable String baseBranchForTc, String userName,
        Build... builds) {
        BuildsInfo buildsInfo = new BuildsInfo(srvId, ticket, branchForTc, parentSuiteId, baseBranchForTc, userName, builds);

        observerTask.addInfo(buildsInfo);
    }

    /**
     * @param key {@code Contribution Key}.
     */
    public String getObservationStatus(ContributionKey key) {
        StringBuilder sb = new StringBuilder();

        BuildsInfo buildsInfo = observerTask.getInfo(key);

        ITcBotUserCreds creds = tcBotBgAuth.getServerAuthorizerCreds();

        ITeamcityIgnited teamcity = teamcityIgnitedProvider.server(key.srvId, creds);

        if (Objects.nonNull(buildsInfo)) {
            sb.append(buildsInfo.ticket).append(" to be commented, waiting for builds. ");
            sb.append(buildsInfo.finishedBuildsCount(teamcity, strCompactor));
            sb.append(" builds done from ");
            sb.append(buildsInfo.buildsCount());
        }

        return sb.toString();
    }
}
