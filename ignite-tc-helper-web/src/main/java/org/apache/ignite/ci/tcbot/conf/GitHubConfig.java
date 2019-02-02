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
import org.apache.ignite.ci.HelperConfig;

import java.util.Properties;

/**
 *
 */
public class GitHubConfig implements IGitHubConfig {
    public static final String DEFAULT_BRANCH_PREFIX = "ignite-";
    /**
     * Service (server) Name.
     */
    private String code;

    private String apiUrl;

    private String branchPrefix;


    private Properties props;


    public GitHubConfig() {
    }

    public GitHubConfig(String code, Properties props) {
        this.code = code;
        this.props = props;
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

    @Override
    public String gitBranchPrefix() {
        if (Strings.isNullOrEmpty(branchPrefix)) {
            if (props != null) {
                return props.getProperty(HelperConfig.GIT_BRANCH_PREFIX, DEFAULT_BRANCH_PREFIX);
            } else {
                return DEFAULT_BRANCH_PREFIX;
            }
        }
        return branchPrefix;
    }
}
