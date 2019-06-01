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

package org.apache.ignite.ci;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.ci.db.Ignite2Configurer;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.WALMode;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;

import java.io.File;
import java.io.IOException;

public class TcBotIgniteServerLauncher {
    public static void main(String[] args) throws IOException {
        final File workDir = TcBotWorkDir.resolveWorkDir();
        Ignite2Configurer.configLogger(workDir, "tcbot_srv_logs");

        final IgniteConfiguration cfg = new IgniteConfiguration();
        Ignite2Configurer.setIgniteHome(cfg, workDir);
        cfg.setWorkDirectory(new File(cfg.getIgniteHome(), "tcbot_srv").getCanonicalPath());

/*
        setupDisco(cfg);*/
        cfg.setConsistentId("tcbot");
        cfg.setGridLogger(new Slf4jLogger());
        System.err.println("Hello");


        final DataRegionConfiguration regConf = Ignite2Configurer.getDataRegionConfiguration();

        final DataStorageConfiguration dsCfg = new DataStorageConfiguration()
                .setWalMode(WALMode.LOG_ONLY)
                .setWalHistorySize(1)
                .setCheckpointFrequency(5 * 60 * 1000)
                .setWriteThrottlingEnabled(true)
                .setDefaultDataRegionConfiguration(regConf);

        cfg.setDataStorageConfiguration(dsCfg);

        System.out.println("Starting Ignite Server Node");

        final Ignite ignite = Ignition.start(cfg);

        System.out.println("Activating Ignite Server Node");

        ignite.cluster().active(true);

        System.out.println("Activate Completed");
    }
}
