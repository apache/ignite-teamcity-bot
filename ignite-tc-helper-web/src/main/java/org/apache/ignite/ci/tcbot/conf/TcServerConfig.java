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

import com.google.common.base.Strings;
import java.util.Properties;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.HelperConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Teamcity connection configuration or reference to another config.
 */
public class TcServerConfig implements ITcServerConfig {
    private static final String DEFAULT_HOST = "https://ci.ignite.apache.org/";

    /** TC server name. */
    @Nonnull private String code;

    /** Name of server this config points to. This config is just an alias for TC Server. */
    @Nullable
    private String reference;

    @Nullable private Properties props;

    /** Host. */
    @Nullable private String host;

    /** Downloaded build logs relative path. */
    @Nullable private String logsDir;

    public TcServerConfig() {

    }

    public TcServerConfig(String code, Properties properties) {
        this.code = code;
        this.props = properties;
    }

    public String getCode() {
        return code;
    }

    /** {@inheritDoc} */
    @Override public Properties properties() {
        if (props == null)
            return new Properties();

        return props;
    }

    /** {@inheritDoc} */
    @Override public String reference() {
        return reference;
    }

    /** {@inheritDoc} */
    @Override public String host() {
        final String hostConf = hostConfigured().trim();

        return hostConf + (hostConf.endsWith("/") ? "" : "/");
    }

    /** {@inheritDoc} */
    @Override public String logsDirectory() {
        String dfltLogs = (Strings.isNullOrEmpty(getCode()) ? "" : code + "_") + "logs";

        if (!Strings.isNullOrEmpty(logsDir))
            return logsDir;

        return props != null
            ? props.getProperty(HelperConfig.LOGS, dfltLogs)
            : dfltLogs;

    }

    /**
     * Configured value for host.
     */
    @NotNull
    private String hostConfigured() {
        if (!Strings.isNullOrEmpty(host))
            return host;

        return props != null
            ? props.getProperty(HelperConfig.HOST, DEFAULT_HOST)
            : DEFAULT_HOST;

    }

    /**
     * @param props Properties.
     */
    public void properties(Properties props) {
        this.props = props;
    }

    public void code(String srvCode) {
        this.code = srvCode;
    }
}
