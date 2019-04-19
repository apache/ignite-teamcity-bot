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
package org.apache.ignite.ci.tcbot.conf;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.File;
import java.util.Objects;
import java.util.Properties;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.di.cache.GuavaCached;

/**
 *
 */
public class LocalFilesBasedConfig implements ITcBotConfig {
    /** {@inheritDoc} */
    @GuavaCached(softValues = true, expireAfterWriteSecs = 3 * 60)
    @Override public BranchesTracked getTrackedBranches() {
        return HelperConfig.getTrackedBranches();
    }

    /** {@inheritDoc} */
    @Override public ITcServerConfig getTeamcityConfig(String srvCode) {
        return getTrackedBranches().getTcConfig(srvCode)
            .orElseGet(() -> {
                TcServerConfig tcCfg = new TcServerConfig();

                tcCfg.code(srvCode);

                Properties props = loadOldAuthProps(srvCode);

                tcCfg.properties(props);

                return tcCfg;
            });
    }

    /** {@inheritDoc} */
    @Override public IJiraServerConfig getJiraConfig(String srvCode) {
        return getTrackedBranches().getJiraConfig(srvCode)
            .orElseGet(() -> new JiraServerConfig()
                .code(srvCode)
                .properties(loadOldAuthProps(srvCode)));
    }

    /** {@inheritDoc} */
    @Override public IGitHubConfig getGitConfig(String srvCode) {
        GitHubConfig cfg = getTrackedBranches().getGitHubConfig(srvCode)
            .orElseGet(() -> new GitHubConfig()
                .code(srvCode)
                .properties(loadOldAuthProps(srvCode)));

        Preconditions.checkState(Objects.equals(cfg.code(), srvCode));

        return cfg;
    }

    /** {@inheritDoc} */
    @Override public NotificationsConfig notifications() {
        NotificationsConfig notifications = getTrackedBranches().notifications();
        if (notifications != null && !notifications.isEmpty())
            return notifications;

        Properties cfgProps = HelperConfig.loadEmailSettings();
        final String authTok = cfgProps.getProperty(HelperConfig.SLACK_AUTH_TOKEN);

        NotificationsConfig cfg = new NotificationsConfig();
        cfg.slackAuthTok = authTok;
        return notifications;
    }

    /** {@inheritDoc} */
    @Override public String primaryServerCode() {
        String srvCode = getTrackedBranches().primaryServerCode();

        return Strings.isNullOrEmpty(srvCode) ? ITcBotConfig.DEFAULT_SERVER_CODE : srvCode;
    }

    @GuavaCached(softValues = true, expireAfterWriteSecs = 3 * 60)
    protected Properties loadOldAuthProps(String srvCode) {
        File workDir = HelperConfig.resolveWorkDir();

        String cfgName = HelperConfig.prepareConfigName(srvCode);

        return HelperConfig.loadAuthProperties(workDir, cfgName);
    }

}
