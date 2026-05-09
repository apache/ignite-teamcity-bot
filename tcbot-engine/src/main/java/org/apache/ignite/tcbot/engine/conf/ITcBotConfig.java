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
package org.apache.ignite.tcbot.engine.conf;

import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;

import java.util.Collection;
import java.util.Collections;

/**
 * Teamcity Bot configuration access interface.
 */
public interface ITcBotConfig extends IDataSourcesConfigSupplier {
    /** Default server code. */
    String DEFAULT_SERVER_CODE = "apache";

    /** Default flaky rate. */
    Integer DEFAULT_FLAKY_RATE = 20;

    /** Default confidence. */
    Double DEFAULT_CONFIDENCE = 0.95;

    /** Default TeamCity group id whose members are allowed to administer bot settings. */
    String DEFAULT_BOT_ADMIN_GROUP = "IGNITE_COMMITER";

    /** */
    String primaryServerCode();

    /** */
    Integer flakyRate();

    /** */
    Double confidence();

    /** */
    Boolean alwaysFailedTestDetection();

    /**
     * @return Tracked branches configuration for TC Bot.
     */
    ITrackedBranchesConfig getTrackedBranches();

    /**
     * @return list of servers (services) identifiers involved into tracked branches processing.
     */
    default Collection<String> getServerIds() {
        return getTrackedBranches().getServerIds();
    }

    ITcServerConfig getTeamcityConfig(String srvCode);

    IJiraServerConfig getJiraConfig(String srvCode);

    IGitHubConfig getGitConfig(String srvCode);

    /**
     * @return TeamCity group keys/names whose members are bot admins.
     */
    default Collection<String> botAdminGroups() {
        return Collections.singleton(DEFAULT_BOT_ADMIN_GROUP);
    }

    /**
     * @return User logins allowed to manage bot users from config.
     */
    default Collection<String> userAdmins() {
        return Collections.emptyList();
    }

    /**
     * @return notification settings config.
     */
    NotificationsConfig notifications();

    ICleanerConfig getCleanerConfig();
}
