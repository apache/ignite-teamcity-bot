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

package org.apache.ignite.ci.web.rest.login;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.ci.tcbot.ITcBotBgAuth;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.tcbot.common.exeption.ServiceUnauthorizedException;
import org.apache.ignite.tcbot.common.exeption.ServiceUnavailableException;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.user.IUserStorage;
import org.apache.ignite.tcbot.persistence.scheduler.IScheduler;
import org.apache.ignite.tcservice.TeamcityServiceConnection;
import org.apache.ignite.tcservice.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically refreshes cached bot admin flags using background TeamCity credentials.
 */
public class UserAdminRefreshService {
    /** Admin status is allowed to be stale while TeamCity is temporarily unavailable. */
    public static final long ADMIN_STATUS_MAX_AGE_MS = TimeUnit.DAYS.toMillis(1);

    /** Background refresh period. */
    private static final long REFRESH_PERIOD_HOURS = 1;

    /** Scheduler task name. */
    private static final String TASK_NAME = "userAdminRefresh";

    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(UserAdminRefreshService.class);

    @Inject private IScheduler scheduler;

    @Inject private IUserStorage users;

    @Inject private ITcBotConfig cfg;

    @Inject private ITcBotBgAuth bgAuth;

    @Inject private Provider<TeamcityServiceConnection> tcFactory;

    /** Start guard. */
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * Starts periodic admin refresh.
     */
    public void start() {
        if (!started.compareAndSet(false, true))
            return;

        scheduler.invokeLater(this::scheduleRefresh, 1, TimeUnit.MINUTES);
    }

    /**
     * Schedules next refresh.
     */
    private void scheduleRefresh() {
        scheduler.sheduleNamed(TASK_NAME, this::refreshAndReschedule, REFRESH_PERIOD_HOURS, TimeUnit.HOURS);
    }

    /**
     * Refreshes and schedules next attempt.
     */
    private void refreshAndReschedule() {
        try {
            refreshStaleAdmins();
        }
        catch (Exception e) {
            logger.warn("Failed to refresh admin flags: " + e.getMessage(), e);
        }
        finally {
            scheduler.invokeLater(this::scheduleRefresh, REFRESH_PERIOD_HOURS, TimeUnit.HOURS);
        }
    }

    /**
     * Refreshes stale admin flags.
     */
    @MonitoredTask(name = "Refresh Bot Admin Flags")
    public String refreshStaleAdmins() {
        ITcBotUserCreds creds = bgAuth.getServerAuthorizerCreds();
        String srvId = cfg.primaryServerCode();

        if (creds == null || !creds.hasAccess(srvId))
            return "Skipped: no background TeamCity credentials for " + srvId;

        long nowTs = System.currentTimeMillis();
        List<TcHelperUser> staleUsers = users.allUsers()
            .filter(user -> user != null && user.username != null)
            .filter(user -> user.isAdminStatusStale(nowTs, ADMIN_STATUS_MAX_AGE_MS))
            .collect(Collectors.toList());

        if (staleUsers.isEmpty())
            return "No stale admin flags";

        TeamcityServiceConnection tcConn = tcFactory.get();
        tcConn.init(srvId);
        tcConn.setAuthData(creds.getUser(srvId), creds.getPassword(srvId));

        int refreshed = 0;
        int invalidated = 0;
        int notFound = 0;

        for (TcHelperUser user : staleUsers) {
            try {
                User tcUser = tcConn.getUserByUsername(user.username);

                boolean admin = tcUser != null && tcUser.belongsToAnyGroup(cfg.botAdminGroups());

                user.updateAdmin(admin, nowTs);

                if (tcUser != null)
                    user.enrichUserData(tcUser);

                users.putUser(user.username, user);

                refreshed++;

                if (!admin)
                    invalidated++;
            }
            catch (RuntimeException e) {
                if (isNotFound(e)) {
                    user.updateAdmin(false, nowTs);
                    users.putUser(user.username, user);

                    refreshed++;
                    invalidated++;
                    notFound++;

                    continue;
                }

                if (isTeamCityUnavailable(e))
                    return "Skipped: TeamCity is unavailable, refreshed=" + refreshed;

                throw e;
            }
        }

        return "Refreshed " + refreshed + " stale admin flag(s), invalidated " + invalidated +
            ", not found " + notFound;
    }

    /**
     * @param e Exception.
     */
    private boolean isNotFound(Throwable e) {
        for (Throwable th = e; th != null; th = th.getCause()) {
            if (th instanceof FileNotFoundException)
                return true;
        }

        return false;
    }

    /**
     * @param e Exception.
     */
    private boolean isTeamCityUnavailable(Throwable e) {
        for (Throwable th = e; th != null; th = th.getCause()) {
            if (th instanceof ServiceUnavailableException || th instanceof ServiceUnauthorizedException)
                return true;

            if (th instanceof UncheckedIOException && !isNotFound(th))
                return true;
        }

        return false;
    }
}
