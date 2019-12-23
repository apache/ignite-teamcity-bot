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

import com.google.common.base.Strings;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.common.conf.IBuildParameterSpec;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;

/**
 * Teamcity connection configuration or reference to another config.
 */
public class TcServerConfig implements ITcServerConfig {
    private static final String DEFAULT_HOST = "https://ci.ignite.apache.org/";
    public static final String HOST = "host";

    public static final String TC_BUILD_LOGS_DIR = "logs";

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

    /** Default tracked branch name in internal identification of TC bot. */
    @Nullable private String defaultTrackedBranch;

    /**
     * TC suite (build type) id for generating VISAs. If not specified, some value is found from default tracked branch
     * suites having 'default' branch specified
     */
    @Nullable private String defaultVisaSuiteId;

    /** Build parameters which may be used for filtering. */
    @Nullable private List<BuildParameterSpec> filteringParameters = new ArrayList<>();

    /** Trusted suites. */
    @Nullable private List<String> trustedSuites = new ArrayList<>();

    /** Additional service code to check access before allowing accessing this service. */
    private String additionalServiceToCheckAccess;

    /** Time as auto-triggering build is disabled. {@link DateTimeFormatter.ISO_LOCAL_TIME} must be used. */
    @Nullable private String autoTriggeringBuildDisabledStartTime;

    /** Time as auto-triggering build is enabled. {@link DateTimeFormatter.ISO_LOCAL_TIME} must be used. */
    @Nullable private String autoTriggeringBuildDisabledEndTime;

    public TcServerConfig() {

    }

    @Nonnull public String getCode() {
        return code;
    }

    /** {@inheritDoc} */
    @Nullable @Override public String reference() {
        return reference;
    }

    /** {@inheritDoc} */
    @Nullable @Override public String additionalServiceToCheckAccess() {
        return additionalServiceToCheckAccess;
    }

    /** {@inheritDoc} */
    @Override public String host() {
        final String hostConf = hostConfigured().trim();

        return hostConf + (hostConf.endsWith("/") ? "" : "/");
    }

    /** {@inheritDoc} */
    @Override public String logsDirectory() {
        if (!Strings.isNullOrEmpty(logsDir))
            return logsDir;

        String dfltLogs = (Strings.isNullOrEmpty(getCode()) ? "" : code + "_") + "logs";

        return props != null
            ? props.getProperty(TC_BUILD_LOGS_DIR, dfltLogs)
            : dfltLogs;
    }

    /**
     * Configured value for host.
     */
    @Nonnull
    private String hostConfigured() {
        if (!Strings.isNullOrEmpty(host))
            return host;

        return props != null
            ? props.getProperty(HOST, DEFAULT_HOST)
            : DEFAULT_HOST;
    }

    /**
     * @return tracked branch name to be used for this server for PRs check
     */
    @Nonnull @Override public String defaultTrackedBranch() {
        if (!Strings.isNullOrEmpty(defaultTrackedBranch))
            return defaultTrackedBranch;

        return DEFAULT_TRACKED_BRANCH_NAME;
    }

    /** {@inheritDoc} */
    @Override @Nullable public String defaultVisaSuiteId() {
        return defaultVisaSuiteId;
    }

    /** {@inheritDoc} */
    @Nonnull @Override public Collection<? extends IBuildParameterSpec> filteringParameters() {
        if (filteringParameters == null || filteringParameters.isEmpty())
            return Collections.emptySet();

        return Collections.unmodifiableList(filteringParameters);
    }

    /** {@inheritDoc} */
    @Nonnull @Override public Collection<String> trustedSuites() {
        if (trustedSuites == null || trustedSuites.isEmpty())
            return Collections.emptySet();

        return Collections.unmodifiableList(trustedSuites);
    }

    /**
     * @param props Properties.
     */
    public TcServerConfig properties(Properties props) {
        this.props = props;

        return this;
    }

    public TcServerConfig code(String srvCode) {
        this.code = srvCode;

        return this;
    }

    /** {@inheritDoc} */
    public String autoTriggeringBuildDisabledStartTime() {
        return autoTriggeringBuildDisabledStartTime;
    }

    /** {@inheritDoc} */
    public String autoTriggeringBuildDisabledEndTime() {
        return autoTriggeringBuildDisabledEndTime;
    }
}
