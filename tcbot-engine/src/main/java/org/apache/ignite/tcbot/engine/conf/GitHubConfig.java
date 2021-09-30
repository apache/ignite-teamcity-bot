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
import java.util.Properties;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.conf.PasswordEncoder;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 *
 */
public class GitHubConfig implements IGitHubConfig {
    public static final String DEFAULT_BRANCH_PREFIX = "ignite-";

    /** GitHub authorization token property name. */
    public static final String GITHUB_AUTH_TOKEN = "github.auth_token";

    /** Git branch naming prefix for PRLess contributions. */
    public static final String GIT_BRANCH_PREFIX = "git.branch_prefix";

    /** Github API url for the project. */
    public static final String GIT_API_URL = "git.api_url";

    /** Service (server) code. */
    private String code;

    /** GitHub Api URL */
    private String apiUrl;

    /** Branch prefix for ticket related feature branches. */
    private String branchPrefix;

    /**
     * Git Auth token encoded to access non-public git repos, use {@link PasswordEncoder#encodeJiraTok(String,
     * String)} to set up value in a config.
     */
    private String authTok;

    private Properties props;

    /**
     * Prefer branches contributions. If null or false then PRs have priority. If set to true that means branch scanning
     * preformed more often, and default triggering option is branch.
     */
    @Nullable
    private Boolean preferBranches;

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
    @Nonnull
    @Override public String gitBranchPrefix() {
        if (!Strings.isNullOrEmpty(branchPrefix))
            return branchPrefix;

        return props != null
            ? props.getProperty(GIT_BRANCH_PREFIX, DEFAULT_BRANCH_PREFIX)
            : DEFAULT_BRANCH_PREFIX;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override public String gitAuthTok() {
        String encAuth = gitAuthTokenEncoded();

        return isNullOrEmpty(encAuth) ? null : PasswordEncoder.decode(encAuth);
    }

    /** {@inheritDoc} */
    @Override public String gitApiUrl() {
        String gitApiUrl = getGitApiConfigured();

        if (isNullOrEmpty(gitApiUrl))
            return gitApiUrl;

        return gitApiUrl.endsWith("/") ? gitApiUrl : gitApiUrl + "/";

    }

    /** {@inheritDoc} */
    @Override public boolean isPreferBranches() {
        return Boolean.TRUE.equals(preferBranches);
    }

    /** {@inheritDoc} */
    @Override public String code() {
        return code;
    }

    /**
     * @return Github API url configured, probably without ending slash.
     */
    @Nullable
    public String getGitApiConfigured() {
        if (!Strings.isNullOrEmpty(apiUrl))
            return apiUrl;

        return props != null
            ? props.getProperty(GIT_API_URL)
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
            ? props.getProperty(GITHUB_AUTH_TOKEN)
            : null;
    }
}
