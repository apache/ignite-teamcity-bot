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
import javax.annotation.Nullable;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.conf.PasswordEncoder;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 *
 */
public class GitHubConfig implements IGitHubConfig {
    public static final String DEFAULT_BRANCH_PREFIX = "ignite-";
    /**
     * Service (server) Name.
     */
    private String code;

    /** GitHub Api URL */
    private String apiUrl;

    /** */
    private String branchPrefix;

    /**
     * Git Auth token encoded to access non-public git repos, use {@link org.apache.ignite.ci.conf.PasswordEncoder#encodeJiraTok(String,
     * String)} to set up value in a config.
     */
    private String authTok;

    private Properties props;

    public GitHubConfig() {
    }

    public String getCode() {
        return code;
    }

    /**
     * @param props Properties.
     */
    public GitHubConfig properties(Properties props) {
        this.props = props;

        return this;
    }

    /**
     * @param code Name.
     */
    public GitHubConfig code(String code) {
        this.code = code;

        return this;
    }

    /** {@inheritDoc} */
    @Override public String gitBranchPrefix() {
        if (!Strings.isNullOrEmpty(branchPrefix))
            return branchPrefix;

        return props != null
            ? props.getProperty(HelperConfig.GIT_BRANCH_PREFIX, DEFAULT_BRANCH_PREFIX)
            : DEFAULT_BRANCH_PREFIX;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public String gitAuthTok() {
        String encAuth = gitAuthTokenEncoded();

        if (isNullOrEmpty(encAuth))
            return null;

        return PasswordEncoder.decode(encAuth);
}

    /** {@inheritDoc} */
    @Override public String gitApiUrl() {
        String gitApiUrl = getGitApiConfigured();

        return gitApiUrl.endsWith("/") ? gitApiUrl : gitApiUrl + "/";

    }

    /**
     *
     */
    @Nullable public String getGitApiConfigured() {
        if (!Strings.isNullOrEmpty(apiUrl))
            return apiUrl;

        return props != null
            ? props.getProperty(HelperConfig.GIT_API_URL)
            : null;
    }

    /**
     *
     */
    @Nullable
    public String gitAuthTokenEncoded() {
        if (!Strings.isNullOrEmpty(authTok))
            return authTok;

        return props != null
            ? props.getProperty(HelperConfig.GITHUB_AUTH_TOKEN)
            : null;

    }
}
