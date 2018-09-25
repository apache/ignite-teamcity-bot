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

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Strings;
import jersey.repackaged.com.google.common.base.Throwables;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.ITcServerProvider;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.hist.BuildRef;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.rest.parms.FullQueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;

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
    private ICredentialsProv creds;

    /** */
    private ITcServerProvider tcHelper;

    /** */
    private final Map<ChainAtServerTracked, Long> startTimes = new HashMap<>();

    /**
     * @param creds Background credentials provider.
     */
    public void init(ITcServerProvider tcHelper, ICredentialsProv creds) {
        this.creds = creds;
        this.tcHelper = tcHelper;
    }

    /** {@inheritDoc} */
    @Override public void run() {
        try {
            runEx();
        }
        catch (Exception e) {
            e.printStackTrace();

            logger.error("Check Queue periodic check failed: " + e.getMessage(), e);
        }
    }

    /**   */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @AutoProfiling
    @MonitoredTask(name = "Check Queue")
    protected String runEx() {
        if (Boolean.valueOf(System.getProperty(CheckQueueJob.AUTO_TRIGGERING_BUILD_DISABLED))) {
            final String msg = "Automatic build triggering was disabled.";
            logger.info(msg);
            return msg;
        }

        String branch = FullQueryParams.DEFAULT_BRANCH_NAME;

        //todo support several branches
        final BranchTracked tracked = HelperConfig.getTrackedBranches().getBranchMandatory(branch);

        if (tracked == null || tracked.getChains() == null || tracked.getChains().isEmpty()) {
            final String msg = "Background check queue skipped - no config specified for ";
            logger.info(msg + "\"{}\".", branch);

            return msg + branch;
        }

        Map<String, List<ChainAtServerTracked>> chainsBySrv = mapChainsByServer(tracked.getChains());

        StringBuilder res = new StringBuilder();
        for (Map.Entry<String, List<ChainAtServerTracked>> entry : chainsBySrv.entrySet()) {
            String srv = entry.getKey();

            List<ChainAtServerTracked> chains = entry.getValue();

            ITeamcity teamcity = tcHelper.server(srv, creds);

            try {
                res.append(srv).append(":").append(checkQueue(teamcity, chains)).append(";");
            }
            catch (RuntimeException | ExecutionException e) {
                logger.error("Unable to check queue: " + e.getMessage(), e);

                throw Throwables.propagate(e);
            }
            catch (InterruptedException e) {
                logger.error("Unable to check queue: " + e.getMessage(), e);

                Thread.currentThread().interrupt();
                throw Throwables.propagate(e);
            }
        }

        if(Strings.isNullOrEmpty(res.toString()))
            return "No TC queue check results: Trigger able branches " + chainsBySrv.size();

        return res.toString();
    }

    /**
     * Trigger build if half od agents is available and there is no self-triggered builds in build queue.
     */
    private String checkQueue(ITeamcity teamcity, List<ChainAtServerTracked> chains) throws ExecutionException, InterruptedException {
        List<Agent> agents = teamcity.agents(true, true);

        int total = agents.size();
        int running = 0;

        for (Agent agent : agents) {
            if (agent.getBuild() != null) //  || !STATE_RUNNING.equals(agent.getBuild().status)
                ++running;
        }

        int free = (total - running) * 100 / total;

        final String agentStatus = MessageFormat.format("{0}% of agents are free ({1} total, {2} running builds).", free, total, running);

        logger.info(agentStatus);

        if (free < CHECK_QUEUE_MIN_FREE_AGENTS_PERCENT) {
            return"Min agent percent of free agents not met:" + agentStatus;
        }

        logger.info("There are more than half free agents (total={}, free={}).", total, total - running);

        List<BuildRef> builds = teamcity.getQueuedBuilds(null).get();

        String selfLogin = creds.getUser(teamcity.serverId());

        for (BuildRef ref : builds) {
            Build build = teamcity.getBuild(ref.href);

            User user = build.getTriggered().getUser();

            if (user == null) {
                logger.info("Unable to get username for queued build {} (type={}).", ref.getId(), ref.buildTypeId);

                continue;
            }

            String login = user.username;

            if (selfLogin.equalsIgnoreCase(login)) {
                final String msg = MessageFormat.format("Queued build {0} was early triggered by me (user {1}). Will not startIgnite build.", ref.getId(), login);

                logger.info(msg);

                return msg;
            }
        }

        StringBuilder res = new StringBuilder();

        for (ChainAtServerTracked chain : chains) {
            if(!Objects.equals(chain.serverId, teamcity.serverId()))
                continue;

            long curr = System.currentTimeMillis();
            long delay = chain.getTriggerBuildQuietPeriod();

            if (delay > 0) {
                Long lastStart = startTimes.get(chain);

                long minsPassed;

                if (lastStart != null &&
                    (minsPassed = TimeUnit.MILLISECONDS.toMinutes(curr - lastStart)) < delay) {

                    final String msg = MessageFormat.format("Skip triggering build, timeout has not expired " +
                                    "(server={0}, suite={1}, branch={2}, delay={3} mins, passed={4} mins)",
                            chain.getServerId(), chain.getSuiteIdMandatory(), chain.getBranchForRestMandatory(),
                            chain.getTriggerBuildQuietPeriod(), minsPassed);
                    logger.info(msg);

                    res.append(msg).append("; ");

                    continue;
                }
            }

            startTimes.put(chain, curr);

            teamcity.triggerBuild(chain.suiteId, chain.branchForRest, true, false);

            res.append(chain.branchForRest).append(" ").append(chain.suiteId).append(" triggered; ");
        }

        return res.toString();
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

            if (!chain.isTriggerBuild()) {
                logger.info("Build triggering disabled for server={}, suite={}, branch={}",
                    srv, chain.getSuiteIdMandatory(), chain.getBranchForRestMandatory());

                continue;
            }

            logger.info("Checking queue for server {}.", srv);

            chainsBySrv.computeIfAbsent(srv, v -> new ArrayList<>()).add(chain);
        }

        return chainsBySrv;
    }
}
