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

package org.apache.ignite.ci.db;

import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.interceptor.MonitoredTask;
import org.apache.ignite.ci.web.model.Version;
import org.apache.ignite.cluster.BaselineNode;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.jetbrains.annotations.NotNull;

public class Ignite1Init {
    private static boolean clientMode;

    /**
     * Reference to Ignite init future.
     */
    private AtomicReference<Future<Ignite>> igniteFutureRef =
        new AtomicReference<>();

    /**
     * Internally initialized field with instance.
     */
    private Ignite ignite;

    public Ignite1Init() {
    }

    public static void setClientMode(boolean clientMode) {
        Ignite1Init.clientMode = clientMode;
    }

    public Ignite startIgnite() {
        ignitionStart();

        activate();

        dataMigrations();

        return ignite;
    }

    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Data Migrations")
    @AutoProfiling
    public String dataMigrations() {
        return new DbMigrations(ignite).dataMigration();
    }

    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Ignition Start")
    @AutoProfiling
    protected String ignitionStart() {
        System.out.println("Starting Ignite Server Node, " + Version.VERSION);

        final IgniteConfiguration cfg = getIgniteConfiguration();

        this.ignite = Ignition.start(cfg);

        return "Started, topVer " + ignite.cluster().topologyVersion();
    }

    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    @MonitoredTask(name = "Ignite Activate")
    @AutoProfiling
    protected String activate() {
        System.out.println("Activating Ignite Server Node, " + Version.VERSION);

        ignite.cluster().active(true);

        System.out.println("Activate Completed");

        final Collection<BaselineNode> baselineNodes = ignite.cluster().currentBaselineTopology();
        final String str
            = Objects.requireNonNull(baselineNodes)
            .stream()
            .map(BaselineNode::consistentId)
            .collect(Collectors.toList()).toString();

        return "Activated, BLT=" + str;
    }

    @NotNull
    private IgniteConfiguration getIgniteConfiguration() {
        final File workDir = TcBotWorkDir.resolveWorkDir();
        Ignite2Configurer.configLogger(workDir, "tcbot_logs");

        final IgniteConfiguration cfg = new IgniteConfiguration();
        Ignite2Configurer.setIgniteHome(cfg, workDir);

        TcHelperDb.setupDisco(cfg);
        cfg.setConsistentId("TcHelper");
        cfg.setGridLogger(new Slf4jLogger());

        final DataRegionConfiguration regConf = Ignite2Configurer.getDataRegionConfiguration();

        final DataStorageConfiguration dsCfg = Ignite2Configurer.getDataStorageConfiguration(regConf);

        dsCfg.setPageSize(4 * 1024);

        cfg.setDataStorageConfiguration(dsCfg);
        cfg.setMetricsLogFrequency(0);
        return cfg;
    }

    @AutoProfiling
    protected Ignite init() {
        if (clientMode)
            Ignition.setClientMode(true);

        return startIgnite();
    }

    public Future<Ignite> getIgniteFuture() {
        final FutureTask<Ignite> futTask = new FutureTask<>(this::init);

        if (igniteFutureRef.compareAndSet(null, futTask)) {
            final Thread thread = new Thread(futTask, "ignite-1-init-thread");
            thread.start();

            return futTask;
        }
        else
            return igniteFutureRef.get();
    }
}
