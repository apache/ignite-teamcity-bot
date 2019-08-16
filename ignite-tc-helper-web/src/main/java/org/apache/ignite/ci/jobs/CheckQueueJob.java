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

import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedChain;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcservice.model.agent.Agent;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcservice.model.result.Triggered;
import org.apache.ignite.tcservice.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        Integer.getInteger("CHECK_QUEUE_MIN_FREE_AGENTS_PERCENT", 15);

    /** */
    private ITcBotUserCreds creds;

    /** */
    @Inject private ITeamcityIgnitedProvider tcIgnitedProv;

    /** */
    @Inject private IStringCompactor compactor;

    /** */
    @Inject private ITcBotConfig cfg;

    /** */
    private final Map<ITrackedChain, Long> startTimes = new HashMap<>();

    /**
     * @param creds Background credentials provider.
     */
    public void init(ITcBotUserCreds creds) {
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
    @MonitoredTask(name = "Check Servers Queue")
    protected String runEx() {
        if (Boolean.valueOf(System.getProperty(AUTO_TRIGGERING_BUILD_DISABLED))) {
            final String msg = "Automatic build triggering was disabled.";
            logger.info(msg);
            return msg;
        }

        Stream<ITrackedBranch> tracked = cfg.getTrackedBranches().branchesStream();

        int srvsChecked = 0, chainsChecked = 0;

        Map<String, List<ITrackedChain>> chainsBySrv = mapChainsByServer(tracked);
        if (chainsBySrv.isEmpty()) {
            final String msg = "Background check queue skipped - no config set for tracked branches.";
            logger.info(msg);

            return msg;
        }
        for (Map.Entry<String, List<ITrackedChain>> entry : chainsBySrv.entrySet()) {
            String srvCode = entry.getKey();

            List<ITrackedChain> chainsAll = entry.getValue();
            List<ITrackedChain> chains = chainsAll.stream()
                    .filter(c -> Objects.equals(c.serverCode(), srvCode))
                    .collect(Collectors.toList());

            srvsChecked++;

            chainsChecked += chainsAll.size();

            try {
                checkQueue(srvCode, chains);
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
    protected String checkQueue(String srvCode, List<ITrackedChain> chains) {
        ITeamcityIgnited tcIgn = tcIgnitedProv.server(srvCode, creds);

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

        String selfLogin = creds.getUser(srvCode);

        tcIgn.actualizeRecentBuildRefs();

        StringBuilder res = new StringBuilder();

        for (ITrackedChain chain : chains) {
            if (!Objects.equals(chain.serverCode(), srvCode))
                continue;

            String result = checkIfChainTriggerable(chain.serverCode(), chain.tcSuiteId(), chain.tcBranch(), tcIgn, selfLogin, chain);

            res.append(result).append("; ");
        }

        return res.toString();
    }

    @SuppressWarnings("WeakerAccess")
    @MonitoredTask(name = "Check Server Queue", nameExtArgsIndexes = {0, 1, 2})
    protected String checkIfChainTriggerable(String serverCode,
                                             String buildTypeId,
                                             String tcBranch,
                                             ITeamcityIgnited tcIgn,
                                             String selfLogin,
                                             ITrackedChain chain) {
        List<BuildRefCompacted> buildsForBr = tcIgn.getQueuedBuildsCompacted(tcBranch);

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
                        " Will not start new build.", buildId, login, tcBranch, build.buildTypeId);

                logger.info(msg);

                return msg;
            }
        }

        long curr = System.currentTimeMillis();
        long delay = chain.triggerBuildQuietPeriod();

        if (delay > 0) {
            Long lastStart = startTimes.get(chain);

            long minsPassed;

            if (lastStart != null &&
                (minsPassed = TimeUnit.MILLISECONDS.toMinutes(curr - lastStart)) < delay) {

                final String msg = MessageFormat.format("Skip triggering build, timeout has not expired " +
                                "(server={0}, suite={1}, branch={2}, delay={3} mins, passed={4} mins)",
                        serverCode, buildTypeId, tcBranch,
                        chain.triggerBuildQuietPeriod(), minsPassed);
                logger.info(msg);

                return msg;
            }
        }

        startTimes.put(chain, curr);

        tcIgn.triggerBuild(buildTypeId, tcBranch, true, false, chain.generateBuildParameters());

        return buildTypeId + " " +  tcBranch  + " triggered; ";
    }

    /**
     * @param branchesTracked Tracked branches.
     * @return Mapped chains to server identifier.
     */
    private Map<String, List<ITrackedChain>> mapChainsByServer(Stream<ITrackedBranch> branchesTracked) {
        Map<String, List<ITrackedChain>> chainsBySrv = new HashMap<>();

        branchesTracked.flatMap(ITrackedBranch::chainsStream)
                .filter(chain -> {
                    String srv = chain.serverCode();

                    if (!tcIgnitedProv.hasAccess(srv, creds)) {
                        logger.warn("Background operations credentials does not grant access to server \"{}\"," +
                                " build queue trigger will not work.", srv);

                        return false;
                    }

                    return true;
                })
                .filter(chain -> {
                    if (!chain.triggerBuild()) {
                        logger.info("Build triggering disabled for server={}, suite={}, branch={}",
                                chain.serverCode(), chain.tcBranch(), chain.tcBranch());

                        return false;
                    }
                    return true;
                })
                .forEach(chain -> {
                    logger.info("Checking queue for server {}.", chain.serverCode());

                    chainsBySrv.computeIfAbsent(chain.serverCode(), v -> new ArrayList<>()).add(chain);
                });

        return chainsBySrv;
    }
}
