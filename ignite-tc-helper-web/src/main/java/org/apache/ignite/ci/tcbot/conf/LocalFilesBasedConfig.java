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

import java.io.File;
import java.util.Properties;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.conf.BranchesTracked;
import org.apache.ignite.ci.di.cache.GuavaCached;

/**
 *
 */
public class LocalFilesBasedConfig implements ITcBotConfig {
    /** {@inheritDoc} */
    @GuavaCached(softValues = true, expireAfterAccessSecs = 3 * 60)
    @Override public BranchesTracked getTrackedBranches() {
        return HelperConfig.getTrackedBranches();
    }

    /** {@inheritDoc} */
    @Override public Properties getTeamcityConfig(String srvName) {
        File workDir = HelperConfig.resolveWorkDir();

        String cfgName = HelperConfig.prepareConfigName(srvName);

        return HelperConfig.loadAuthProperties(workDir, cfgName);
    }

    /** {@inheritDoc} */
    @Override public String primaryServerId() {
        return ITcBotConfig.DEFAULT_SERVER_ID;
    }
}
