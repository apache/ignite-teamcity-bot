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

import com.google.common.base.Strings;
import java.text.MessageFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.ignite.ci.teamcity.ignited.BuildRefCompacted;
import org.apache.ignite.ci.teamcity.ignited.fatbuild.FatBuildCompacted;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.internal.util.typedef.T2;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranch;
import org.apache.ignite.tcbot.engine.conf.ITrackedChain;
import org.apache.ignite.tcbot.engine.conf.NotificationsConfig;
import org.apache.ignite.tcbot.notify.ISlackSender;
import org.apache.ignite.tcbot.persistence.IStringCompactor;
import org.apache.ignite.tcignited.ITeamcityIgnited;
import org.apache.ignite.tcignited.ITeamcityIgnitedProvider;
import org.apache.ignite.tcservice.model.Property;
import org.apache.ignite.tcservice.model.agent.Agent;
import org.apache.ignite.tcservice.model.result.Build;
import org.apache.ignite.tcservice.model.result.Triggered;
import org.apache.ignite.tcservice.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;

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

    /** Percentage of free windows agents required to trigger build. */
    private static final int CHECK_QUEUE_MIN_FREE_WINDOWS_AGENTS_PERCENT =
        Integer.getInteger("CHECK_QUEUE_MIN_FREE_WINDOWS_AGENTS_PERCENT", 1);

    /** Max build queue size to trigger build. */
    private static final int CHECK_MAX_QUEUE_SIZE =
        Integer.getInteger("CHECK_MAX_QUEUE_SIZE", 300);

    /** */
    private ITcBotUserCreds creds;

    /** */
    @Inject private ITeamcityIgnitedProvider tcIgnitedProv;

    /** */
    @Inject private IStringCompactor compactor;

    /** */
    @Inject private ITcBotConfig cfg;

    /** */
    @Inject private ISlackSender slackSender;

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
        catch (Throwable e) {
            e.printStackTrace();

            logger.error("Check Queue periodic check failed: " + e.getMessage(), e);

            NotificationsConfig notifications = cfg.notifications();

            String msg = ":warning: Periodic check servers queue and build triggering failed";

            notifications.channels().forEach(channel -> {
                    String chName = channel.slack();

                    if (chName != null && chName.startsWith("#"))
                        try {
                            slackSender.sendMessage(chName, msg, notifications);
                        }
                        catch (Exception ex) {
                            logger.warn("Unable to notify address [" + chName + "] about periodic check queue failure", e);
                        }
                });
        }
    }

    /**   */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @AutoProfiling
    @MonitoredTask(name = "Check Servers Queue (Triggering)")
    protected String runEx() {
        logger.info("Build triggering task is started");

        if (Boolean.parseBoolean(System.getProperty(AUTO_TRIGGERING_BUILD_DISABLED))) {
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

            if (autoTriggerDisabledForWorkingHours(srvCode)) {
                final String msg = "Automatic build triggering was disabled during working hours.";
                logger.info(msg);

                return msg;
            }

            List<ITrackedChain> chainsAll = entry.getValue();
            List<ITrackedChain> chains = chainsAll.stream()
                    .filter(c -> Objects.equals(c.serverCode(), srvCode))
                    .collect(toList());

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
    @MonitoredTask(name = "Check Server Queue (Triggering)", nameExtArgIndex = 0)
    protected String checkQueue(String srvCode, List<ITrackedChain> chains) {
        ITeamcityIgnited tcIgn = tcIgnitedProv.server(srvCode, creds);

        int queueSize = tcIgn.queueSize();

        if (queueSize > CHECK_MAX_QUEUE_SIZE) {
            String msg = MessageFormat.format("TeamCity queue is too big. No new builds will be triggered. Size of queue: {0}, TeamCity server: {1}", queueSize, srvCode);

            logger.info(msg);

            return msg;
        }

        List<Agent> agents = tcIgn.agents(true, true);

        int total = agents.size();
        int winAgents = 0;
        int running = 0;
        int winRunning = 0;

        for (Agent agent : agents) {
            //filter for windows agents
            if (agent.getPool().getName().contains("Default") &&
                    agent.isEnabled() &&
                    agent.getProperties().getProperty().stream()
                    .filter(prop -> prop.getName().equals("teamcity.agent.jvm.os.name")).findAny().orElseGet(() -> {
                        Property emptyProp = new Property();

                        emptyProp.setValue("");

                        return emptyProp;
                    }).getValue().contains("Windows")) {
                winAgents++;

                if (agent.getBuild() != null)
                    winRunning++;
            }

            if (agent.getBuild() != null) //  || !STATE_RUNNING.equals(agent.getFatBuild().status)
                ++running;
        }

        int free = total == 0 ? -1 : (total - running) * 100 / total;

        String allAgentStatus = MessageFormat.format("{0}% of agents are free ({1} total, {2} running builds).", free, total, running);

        logger.info(allAgentStatus);

        if (free < CHECK_QUEUE_MIN_FREE_AGENTS_PERCENT)
            return "Min agent percent of free agents not met:" + allAgentStatus;

        logger.info("There are more than {}% free agents (total={}, free={}).", CHECK_QUEUE_MIN_FREE_AGENTS_PERCENT,
            total, total - running);

        int winFree = winAgents == 0 ? -1 : (winAgents - winRunning) * 100 / winAgents;

        String windowsAgentStatus = MessageFormat.format("{0}% of Windows agents are free ({1} total, {2} running builds).", winFree, winAgents, winRunning);

        logger.info(windowsAgentStatus);

        if (winAgents > 0 && winFree < CHECK_QUEUE_MIN_FREE_WINDOWS_AGENTS_PERCENT)
            return "Min agent percent of free Windows agents not met:" + windowsAgentStatus;

        logger.info("There are more than {}% free Windows agents (total={}, free={}).", CHECK_QUEUE_MIN_FREE_WINDOWS_AGENTS_PERCENT,
            winAgents, winAgents - winRunning);

        String selfLogin = creds.getUser(srvCode);

        tcIgn.actualizeRecentBuildRefs();

        StringBuilder res = new StringBuilder();

        for (ITrackedChain chain : chains) {
            if (!Objects.equals(chain.serverCode(), srvCode))
                continue;

            String agentStatus = allAgentStatus + " " + windowsAgentStatus;

            String chainRes = checkIfChainTriggerable(chain.serverCode(), chain.tcSuiteId(), chain.tcBranch(), tcIgn, selfLogin, chain, agentStatus);

            res.append(chainRes).append("; ");
        }

        return res.toString();
    }

    @SuppressWarnings("WeakerAccess")
    @MonitoredTask(name = "Check Server Queue (Triggering)", nameExtArgsIndexes = {0, 1, 2})
    protected String checkIfChainTriggerable(String srvCode,
        String buildTypeId,
        String tcBranch,
        ITeamcityIgnited tcIgn,
        String selfLogin,
        ITrackedChain chain,
        String agentStatus) {
        List<BuildRefCompacted> buildsForBr = tcIgn.getQueuedAndRunningBuildsCompacted(tcBranch);

        for (BuildRefCompacted refComp : buildsForBr) {
            Integer buildId = refComp.getId();
            if (buildId == null)
                continue; // should not occur;

            FatBuildCompacted fatBuild = tcIgn.getFatBuild(buildId);

            Build build = fatBuild.toBuild(compactor);
            Triggered triggered = build.getTriggered();

            if (triggered == null) {
                logger.info("Unable to get triggering info for queued build {} (type={}).", buildId, build.buildTypeId);

                continue;
            }

            User user = build.getTriggered().getUser();

            if (user == null) {
                logger.info("Unable to get username for queued build {} (type={}). Possibly VCS triggered", buildId, build.buildTypeId);

                continue;
            }

            String buildTypeIdExisting = build.buildTypeId();
            if (buildTypeIdExisting == null) {
                logger.info("Unable to get buildTypeId for queued build {} (type={}).", buildId, build.buildTypeId);

                continue;
            }

            String login = user.username;

            if (selfLogin.equalsIgnoreCase(login)
                && buildTypeIdExisting.trim().equals(Strings.nullToEmpty(buildTypeId).trim())) {
                String msg
                    = MessageFormat.format("Queued build {0} was early triggered " +
                    "(user {1}, branch {2}, suite {3})." +
                    " Will not start new build.", Integer.toString(buildId), login, tcBranch, buildTypeIdExisting);

                logger.info(msg);

                return msg;
            }
        }

        long curr = System.currentTimeMillis();
        long delay = chain.triggerBuildQuietPeriod();

        long minsPassed = -1;
        if (delay > 0) {
            Long lastStart = startTimes.get(chain);

            if (lastStart != null &&
                (minsPassed = TimeUnit.MILLISECONDS.toMinutes(curr - lastStart)) < delay) {

                final String msg = MessageFormat.format("Skip triggering build, timeout has not expired " +
                                "(server={0}, suite={1}, branch={2}, delay={3} mins, passed={4} mins)",
                    srvCode, buildTypeId, tcBranch,
                        chain.triggerBuildQuietPeriod(), minsPassed);
                logger.info(msg);

                return msg;
            }
        }

        startTimes.put(chain, curr);

        StringBuilder trigComment = new StringBuilder();
        trigComment.append("Scheduled run ");
        trigComment.append(agentStatus);
        if (minsPassed > 0)
            trigComment.append(" Since last build triggering: ").append(Duration.ofMinutes(minsPassed));

        T2<Build, Set<Integer>> buildAndIds = tcIgn.triggerBuild(buildTypeId, tcBranch, true, false,
            chain.generateBuildParameters(),
            true,
            trigComment.toString());

        Build build = buildAndIds.get1();

        return "Build id " + build.getId() + " " + tcBranch + " for " + buildTypeId + " triggered; ";
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

    /**
     * @param srvCode Server code.
     * @return {@code true} if auto-triggering is disabled now for working hours.
     */
    private boolean autoTriggerDisabledForWorkingHours(String srvCode) {
        DayOfWeek curDayOfWeek = LocalDate.now().getDayOfWeek();

        if (curDayOfWeek == DayOfWeek.SATURDAY || curDayOfWeek == DayOfWeek.SUNDAY)
            return false;

        ITcServerConfig tcCfg = cfg.getTeamcityConfig(srvCode);

        String startTime = tcCfg.autoTriggeringBuildDisabledStartTime();
        String endTime = tcCfg.autoTriggeringBuildDisabledEndTime();

        if (startTime == null || endTime == null)
            return false;

        LocalTime now = LocalTime.now();

        return now.isAfter(LocalTime.parse(startTime)) && now.isBefore(LocalTime.parse(endTime));
    }
}
