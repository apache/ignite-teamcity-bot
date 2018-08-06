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

package org.apache.ignite.ci.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trigger build if half of agents are available and there is no self-triggered builds in build queue.
 */
public class CheckQueueJob implements Runnable {
    /** JVM option to disable this job. */
    public static final String AUTO_TRIGGERING_BUILD_DISABLED = "AUTO_TRIGGERING_BUILD_DISABLED";

    /** */
    private static final Logger logger = LoggerFactory.getLogger(CheckQueueJob.class);

    /** Percentage of free agents required to trigger build. */
    private static final int CHECK_QUEUE_MIN_FREE_AGENTS_PERCENT =
        Integer.getInteger("CHECK_QUEUE_MIN_FREE_AGENTS_PERCENT", 50);

    /** */
    private final ICredentialsProv creds;

    /** */
    private final ITcHelper tcHelper;

    /**
     * @param creds Background credentials provider.
     */
    public CheckQueueJob(ITcHelper tcHelper, ICredentialsProv creds) {
        this.creds = creds;
        this.tcHelper = tcHelper;
    }

    /** {@inheritDoc} */
    @Override public void run() {
        String branch = FullQueryParams.DEFAULT_BRANCH_NAME;

        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branch);

        if (tracked == null || tracked.getChains() == null || tracked.getChains().isEmpty()) {
            logger.info("Background check queue skipped - no config specified for \"{}\".", branch);

            return;
        }

        Map<String, List<ChainAtServerTracked>> chainsBySrv = mapChainsByServer(tracked.getChains());

        for (Map.Entry<String, List<ChainAtServerTracked>> entry : chainsBySrv.entrySet()) {
            String srv = entry.getKey();

            List<ChainAtServerTracked> chains = entry.getValue();

            ITeamcity teamcity = tcHelper.server(srv, creds);

            try {
                checkQueue(teamcity, chains);
            }
            catch (RuntimeException | ExecutionException e) {
                logger.error("Unable to check queue: " + e.getMessage(), e);
            }
            catch (InterruptedException e) {
                logger.error("Unable to check queue: " + e.getMessage(), e);

                Thread.currentThread().interrupt();
            }
        }

    }

    /**
     * Trigger build if half od agents is available and there is no self-triggered builds in build queue.
     */
    private void checkQueue(ITeamcity teamcity, List<ChainAtServerTracked> chains) throws ExecutionException, InterruptedException {
        List<Agent> agents = teamcity.agents(true, true);

        int total = agents.size();
        int running = 0;

        for (Agent agent : agents) {
            if (agent.getBuild() != null) //  || !STATE_RUNNING.equals(agent.getBuild().status)
                ++running;
        }

        int free = (total - running) * 100 / total;

        logger.info("{}% of agents are free ({} total, {} running builds).", free, total, running);

        if (free >= CHECK_QUEUE_MIN_FREE_AGENTS_PERCENT) {
            logger.debug("There are more than half free agents (total={}, free={}).", total, total - running);

            List<BuildRef> builds = teamcity.getQueuedBuilds(null).get();

            String selfLogin = creds.getUser(teamcity.serverId());

            boolean triggerBuild = true;

            for (BuildRef ref : builds) {
                Build build = teamcity.getBuild(ref.href);

                User user = build.getTriggered().getUser();

                if (user == null) {
                    logger.info("Unable to get username for queued build {} (type={}).", ref.getId(), ref.buildTypeId);

                    continue;
                }

                String login = user.username;

                if (selfLogin.equalsIgnoreCase(login)) {
                    logger.info("Queued build {} triggered by me (user {}). Will not start build.", ref.getId(), login);

                    triggerBuild = false;

                    break;
                }
            }

            if (triggerBuild) {
                for (ChainAtServerTracked chain : chains)
                    teamcity.triggerBuild(chain.suiteId, chain.branchForRest, true, false);
            }
        }
    }

    /**
     * @param chains chains.
     * @return Mapped chains to server identifier.
     */
    private Map<String, List<ChainAtServerTracked>> mapChainsByServer(List<ChainAtServerTracked> chains) {
        Map<String, List<ChainAtServerTracked>> chainsBySrv = new HashMap<>();

        for (ChainAtServerTracked chain : chains) {
            String srv = chain.serverId;

            if (!creds.hasAccess(srv)) {
                logger.warn("Background operations credentials does not grant access to server \"{}\"," +
                    " build queue trigger will not work.", srv);

                continue;
            }

            logger.debug("Checking queue for server {}.", srv);

            chainsBySrv.computeIfAbsent(srv, v -> new ArrayList<>()).add(chain);
        }

        return chainsBySrv;
    }
}
