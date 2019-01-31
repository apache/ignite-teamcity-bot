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
    /** TC server name. */
    @Nonnull String name;

    /** Name of server this config points to. This config is just an alias for TC Server. */
    @Nullable String reference;

    @Nullable private Properties props;
    /** Host. */
    @Nullable private String host;

    public TcServerConfig() {

    }

    public TcServerConfig(String name, Properties properties) {
        this.name = name;
        this.props = properties;
    }

    public String getName() {
        return name;
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

    /**
     * Configured value for host.
     */
    @NotNull
    public String hostConfigured() {
        if (Strings.isNullOrEmpty(host))
            return props.getProperty(HelperConfig.HOST, "https://ci.ignite.apache.org/");

        return host;
    }

    /**
     * @param props Properties.
     */
    public void properties(Properties props) {
        this.props = props;
    }
}
