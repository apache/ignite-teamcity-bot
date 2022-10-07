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
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

import com.google.gson.Gson;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.apache.ignite.tcbot.common.interceptor.GuavaCached;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.apache.ignite.tcbot.engine.conf.CleanerConfig;
import org.apache.ignite.tcbot.engine.conf.GitHubConfig;
import org.apache.ignite.tcbot.engine.conf.ICleanerConfig;
import org.apache.ignite.tcbot.engine.conf.ITcBotConfig;
import org.apache.ignite.tcbot.engine.conf.ITrackedBranchesConfig;
import org.apache.ignite.tcbot.engine.conf.JiraServerConfig;
import org.apache.ignite.tcbot.engine.conf.NotificationsConfig;
import org.apache.ignite.tcbot.engine.conf.TcBotJsonConfig;
import org.apache.ignite.tcbot.engine.conf.TcServerConfig;

/**
 *
 */
public class LocalFilesBasedConfig implements ITcBotConfig {
    private static TcBotJsonConfig reloadConfig() {
        final File workDir = TcBotWorkDir.resolveWorkDir();
        final File file = new File(workDir, "branches.json");

        try (FileReader json = new FileReader(file)) {
            return new Gson().fromJson(json, TcBotJsonConfig.class);
        }
        catch (IOException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }

    /** {@inheritDoc} */
    @GuavaCached(softValues = true, expireAfterWriteSecs = 3 * 60)
    protected TcBotJsonConfig getConfig() {
        return reloadConfig();
    }

    /** {@inheritDoc} */
    @Override public ITcServerConfig getTeamcityConfig(String srvCode) {
        return getConfig().getTcConfig(srvCode)
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
        return getConfig().getJiraConfig(srvCode)
            .orElseGet(() -> new JiraServerConfig()
                .code(srvCode)
                .properties(loadOldAuthProps(srvCode)));
    }

    /** {@inheritDoc} */
    @Override public IGitHubConfig getGitConfig(String srvCode) {
        GitHubConfig cfg = getConfig().getGitHubConfig(srvCode)
            .orElseGet(() -> new GitHubConfig()
                .code(srvCode)
                .properties(loadOldAuthProps(srvCode)));

        Preconditions.checkState(Objects.equals(cfg.code(), srvCode));

        return cfg;
    }

    /** {@inheritDoc} */
    @Override public NotificationsConfig notifications() {
        return getConfig().notifications();
    }

    /** {@inheritDoc} */
    @Override public String primaryServerCode() {
        String srvCode = getConfig().primaryServerCode();

        return Strings.isNullOrEmpty(srvCode) ? ITcBotConfig.DEFAULT_SERVER_CODE : srvCode;
    }

    /** {@inheritDoc} */
    @Override public Integer flakyRate() {
        Integer flakyRate = getConfig().flakyRate();

        return flakyRate == null || flakyRate < 0 || flakyRate > 100 ? ITcBotConfig.DEFAULT_FLAKY_RATE : flakyRate;
    }

    /** {@inheritDoc} */
    @Override public Double confidence() {
        Double confidence = getConfig().confidence();

        return confidence == null || confidence < 0 || confidence > 1 ? ITcBotConfig.DEFAULT_CONFIDENCE : confidence;
    }

    /** {@inheritDoc} */
    @Override public Boolean alwaysFailedTestDetection() {
        Boolean alwaysFailedTestDetection = getConfig().alwaysFailedTestDetection();

        return alwaysFailedTestDetection != null && alwaysFailedTestDetection;
    }

    @Override
    public ITrackedBranchesConfig getTrackedBranches() {
        return getConfig();
    }

    @GuavaCached(softValues = true, expireAfterWriteSecs = 3 * 60)
    protected Properties loadOldAuthProps(String srvCode) {
        File workDir = TcBotWorkDir.resolveWorkDir();

        String cfgName = HelperConfig.prepareConfigName(srvCode);

        return HelperConfig.loadAuthProperties(workDir, cfgName);
    }

    /** {@inheritDoc} */
    @Override public ICleanerConfig getCleanerConfig() {
        CleanerConfig cfg = getConfig().getCleanerConfig();
        return cfg != null ? cfg : CleanerConfig.getDefaultCleanerConfig();
    }

}
