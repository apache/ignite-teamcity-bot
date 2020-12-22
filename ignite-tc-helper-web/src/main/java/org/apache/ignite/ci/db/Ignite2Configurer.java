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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import java.io.File;
import java.io.IOException;

import org.apache.ignite.tcbot.common.conf.TcBotSystemProperties;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.WALMode;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

public class Ignite2Configurer {
    public static void configLogger(File workDir, String subdir) {
        LoggerContext logCtx = (LoggerContext)LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder logEncoder = new PatternLayoutEncoder();
        logEncoder.setContext(logCtx);
        logEncoder.setPattern("%-12date{YYYY-MM-dd HH:mm:ss.SSS} %-5level [%t] - %msg%n");
        logEncoder.start();

        RollingFileAppender rollingFa = new RollingFileAppender();
        rollingFa.setContext(logCtx);
        rollingFa.setName("logFile");
        rollingFa.setEncoder(logEncoder);
        rollingFa.setAppend(true);

        final File logs = new File(workDir, subdir);
        TcBotWorkDir.ensureDirExist(logs);

        TimeBasedRollingPolicy logFilePolicy = new TimeBasedRollingPolicy();
        logFilePolicy.setContext(logCtx);
        logFilePolicy.setParent(rollingFa);
        logFilePolicy.setFileNamePattern(new File(logs, "logfile-%d{yyyy-MM-dd_HH}.log").getAbsolutePath());
        logFilePolicy.setMaxHistory(24*7*2);
        logFilePolicy.start();

        final String activeFileName = logFilePolicy.getActiveFileName();

        String absolutePath = new File(activeFileName).getAbsolutePath();

        String absolutePath1 = new File(logs, "logfile-.log").getAbsolutePath();
        System.out.println("Start logging using, policy [" + absolutePath + "] and static [" + absolutePath1 + "]");

        rollingFa.setFile(absolutePath1);

        rollingFa.setRollingPolicy(logFilePolicy);
        rollingFa.start();

        Logger log = logCtx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        log.setAdditive(false);
        log.setLevel(Level.INFO);
        log.detachAndStopAllAppenders();

        log.addAppender(rollingFa);
    }

    public static void setIgniteHome(IgniteConfiguration cfg, File workDir) {
        try {
            cfg.setIgniteHome(workDir.getCanonicalPath());
        }
        catch (IOException e) {
            e.printStackTrace();
            cfg.setIgniteHome(workDir.getAbsolutePath());
        }
    }

    @NotNull
    public static DataRegionConfiguration getDataRegionConfiguration() {
        final DataRegionConfiguration regConf = new DataRegionConfiguration()
            .setPersistenceEnabled(true);

        String regSzGb = System.getProperty(TcBotSystemProperties.TEAMCITY_BOT_REGIONSIZE);

        if (regSzGb != null) {
            try {
                int szGb = Integer.parseInt(regSzGb);

                String msg = "Using custom size of region: " + szGb + "Gb";
                LoggerFactory.getLogger(Ignite2Configurer.class).info(msg);
                System.out.println(msg);

                regConf.setMaxSize(szGb * 1024L * 1024 * 1024);
            }
            catch (NumberFormatException e) {
                e.printStackTrace();

                LoggerFactory.getLogger(Ignite2Configurer.class).error("Unable to setup region", e);
            }
        }
        else {
            String msg = "Using default size of region.";
            LoggerFactory.getLogger(Ignite2Configurer.class).info(msg);
            System.out.println(msg);
        }

        regConf.setMetricsEnabled(true);

        return regConf;
    }

    static DataStorageConfiguration getDataStorageConfiguration(DataRegionConfiguration regConf) {
        return new DataStorageConfiguration()
            // .setWalCompactionEnabled(true)
            .setWalMode(WALMode.LOG_ONLY)
            .setWalHistorySize(1)
            // .setMaxWalArchiveSize(4L * 1024 * 1024 * 1024)
            .setCheckpointFrequency(5 * 60 * 1000)
            .setWriteThrottlingEnabled(true)
            .setDefaultDataRegionConfiguration(regConf);
    }
}
