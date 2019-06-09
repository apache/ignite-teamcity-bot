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
package org.apache.ignite.tcbot.common.conf;

import com.google.common.base.Strings;

import javax.annotation.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Abstract JIRA server config.
 */
public interface IJiraServerConfig {
    /**
     * @return Service ID or server code, internally identified, any string configured.
     */
    public String getCode();

    /**
     * Return JIRA URL, e.g. https://issues.apache.org/jira/
     */
    public String getUrl();

    /**
     * JIRA project code for filtering out tickets and for adding VISA (JIRA comments).
     */
    public String projectCodeForVisa();

    /**
     * @return PR name and branch name matching number prefix
     */
    @Nullable public String branchNumPrefix();

    /**
     * Extracted JIRA basic authorization token from properties.
     *
     * @return Null or decoded auth token for JIRA.
     */
    @Nullable public String decodedHttpAuthToken();

    /**
     * @return {@code True} if JIRA authorization token is available.
     */
    public default boolean isJiraTokenAvailable() {
        return !Strings.isNullOrEmpty(decodedHttpAuthToken());
    }

    public default String restApiUrl() {
        String jiraUrl = getUrl();

        if (isNullOrEmpty(jiraUrl))
            throw new IllegalStateException("JIRA API URL is not configured for this server.");

        StringBuilder apiUrl = new StringBuilder();

        apiUrl.append(jiraUrl);
        if (!jiraUrl.endsWith("/"))
            apiUrl.append("/");

        apiUrl.append("rest/api/2/");

        return apiUrl.toString();
    }

}
