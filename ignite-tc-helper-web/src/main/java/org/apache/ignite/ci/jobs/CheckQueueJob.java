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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.ignite.ci.conf.BranchTracked;
import org.apache.ignite.ci.conf.ChainAtServerTracked;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.di.MonitoredTask;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.tcmodel.agent.Agent;
import org.apache.ignite.ci.tcmodel.result.Build;
import org.apache.ignite.ci.tcmodel.result.Triggered;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.IStringCompactor;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnited;
import org.apache.ignite.ci.teamcity.ignited.ITeamcityIgnitedProvider;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.ExceptionUtil;
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
    private ICredentialsProv creds;

    /** */
    @Inject private ITeamcityIgnitedProvider tcIgnitedProv;

    /** */
    @Inject private IStringCompactor compactor;

    /** */
    @Inject private ITcBotConfig cfg;

    /** */
    private final Map<ChainAtServerTracked, Long> startTimes = new HashMap<>();

    /**
     * @param creds Background credentials provider.
     */
    public void init(ICredentialsProv creds) {
        this.creds = creds;
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
        if (Boolean.valueOf(System.getProperty(AUTO_TRIGGERING_BUILD_DISABLED))) {
            final String msg = "Automatic build triggering was disabled.";
            logger.info(msg);
            return msg;
        }

        List<BranchTracked> tracked = cfg.getTrackedBranches().getBranches();

        if (tracked == null || tracked.isEmpty()) {
            final String msg = "Background check queue skipped - no config set for tracked branches.";
            logger.info(msg);

            return msg;
        }

        int srvsChecked = 0, chainsChecked = 0;

        Map<String, List<ChainAtServerTracked>> chainsBySrv = mapChainsByServer(tracked);

        for (Map.Entry<String, List<ChainAtServerTracked>> entry : chainsBySrv.entrySet()) {
            String srvId = entry.getKey();

            List<ChainAtServerTracked> chainsAll = entry.getValue();
            List<ChainAtServerTracked> chains = chainsAll.stream()
                    .filter(c -> Objects.equals(c.serverId, srvId))
                    .collect(Collectors.toList());

            srvsChecked++;

            chainsChecked += chainsAll.size();

            try {
                checkQueue(srvId, chains);
            }
            catch (Exception e) {
                logger.error("Unable to check queue: " + e.getMessage(), e);

                throw ExceptionUtil.propagateException(e);
            }
        }

        return "Checked: " + srvsChecked + "servers, " + chainsChecked + " chains, "
            + ": Trigger'able branches " + chainsBySrv.size();
    }

    /**
     * Trigger build if half of agents is available and there is no self-triggered builds in build queue.
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @AutoProfiling
    @MonitoredTask(name = "Check Server Queue", nameExtArgIndex = 0)
    protected String checkQueue(String srvId,
        List<ChainAtServerTracked> chains) {

        ITeamcityIgnited tcIgn = tcIgnitedProv.server(srvId, creds);

        List<Agent> agents = tcIgn.agents(true, true);

        int total = agents.size();
        int running = 0;

        for (Agent agent : agents) {
            if (agent.getBuild() != null) //  || !STATE_RUNNING.equals(agent.getFatBuild().status)
                ++running;
        }

        int free = total == 0 ? -1 : (total - running) * 100 / total;

        final String agentStatus = MessageFormat.format("{0}% of agents are free ({1} total, {2} running builds).", free, total, running);

        logger.info(agentStatus);

        if (free < CHECK_QUEUE_MIN_FREE_AGENTS_PERCENT)
            return "Min agent percent of free agents not met:" + agentStatus;

        logger.info("There are more than {}% free agents (total={}, free={}).", CHECK_QUEUE_MIN_FREE_AGENTS_PERCENT,
            total, total - running);

        String selfLogin = creds.getUser(srvId);

        StringBuilder res = new StringBuilder();

        for (ChainAtServerTracked chain : chains) {
            if(!Objects.equals(chain.serverId, srvId))
                continue;

            boolean trigger = true;

            List<BuildRefCompacted> buildsForBr = tcIgn.getQueuedBuildsCompacted(chain.branchForRest);

            for (BuildRefCompacted refComp : buildsForBr) {
                Integer buildId = refComp.getId();
                if (buildId == null)
                    continue; // should not occur;

                final FatBuildCompacted fatBuild = tcIgn.getFatBuild(buildId);
                final Build build = fatBuild.toBuild(compactor);
                final Triggered triggered = build.getTriggered();

                if (triggered == null) {
                    logger.info("Unable to get triggering info for queued build {} (type={}).", buildId, build.buildTypeId);

                    continue;
                }

                User user = build.getTriggered().getUser();

                if (user == null) {
                    logger.info("Unable to get username for queued build {} (type={}).", buildId, build.buildTypeId);

                    continue;
                }

                String login = user.username;

                if (selfLogin.equalsIgnoreCase(login)) {
                    final String msg
                            = MessageFormat.format("Queued build {0} was early triggered " +
                            "(user {1}, branch {2}, suite {3})." +
                            " Will not start Ignite build.", buildId, login, chain.branchForRest, build.buildTypeId);

                    logger.info(msg);

                    res.append(msg).append("; ");

                    trigger = false;

                    break;
                }
            }

            if (!trigger)
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

            tcIgn.triggerBuild(chain.suiteId, chain.branchForRest, true, false);

            res.append(chain.branchForRest).append(" ").append(chain.suiteId).append(" triggered; ");
        }

        return res.toString();
    }

    /**
     * @param branchesTracked Tracked branches.
     * @return Mapped chains to server identifier.
     */
    private Map<String, List<ChainAtServerTracked>> mapChainsByServer(List<BranchTracked> branchesTracked) {
        Map<String, List<ChainAtServerTracked>> chainsBySrv = new HashMap<>();

        for(BranchTracked branchTracked: branchesTracked) {
            for (ChainAtServerTracked chain : branchTracked.getChains()) {
                String srv = chain.serverId;

                if (!tcIgnitedProv.hasAccess(srv, creds)) {
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
        }

        return chainsBySrv;
    }
}
