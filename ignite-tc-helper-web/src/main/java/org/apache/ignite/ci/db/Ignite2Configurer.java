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
import org.apache.ignite.ci.HelperConfig;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Ignite2Configurer {
    static void configLogger(File workDir, String subdir) {
        LoggerContext logCtx = (LoggerContext) LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder logEncoder  = new PatternLayoutEncoder();
        logEncoder.setContext(logCtx);
        logEncoder.setPattern("%-12date{YYYY-MM-dd HH:mm:ss.SSS} %-5level [%t] - %msg%n");
        logEncoder.start();

        RollingFileAppender rollingFa = new RollingFileAppender();
        rollingFa.setContext(logCtx);
        rollingFa.setName("logFile");
        rollingFa.setEncoder(logEncoder);
        rollingFa.setAppend(true);

        final File logs = new File(workDir, subdir);
        HelperConfig.ensureDirExist(logs);

        rollingFa.setFile(new File(logs, "logfile.log").getAbsolutePath());

        TimeBasedRollingPolicy logFilePolicy = new TimeBasedRollingPolicy();
        logFilePolicy.setContext(logCtx);
        logFilePolicy.setParent(rollingFa);
        logFilePolicy.setFileNamePattern(new File(logs, "logfile-%d{yyyy-MM-dd_HH}.log").getAbsolutePath());
        logFilePolicy.setMaxHistory(7);
        logFilePolicy.start();

        //todo use logFilePolicy.getActiveFileName()

        rollingFa.setRollingPolicy(logFilePolicy);
        rollingFa.start();

        Logger log = logCtx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        log.setAdditive(false);
        log.setLevel(Level.INFO);
        log.detachAndStopAllAppenders();

        log.addAppender(rollingFa);
    }
}
