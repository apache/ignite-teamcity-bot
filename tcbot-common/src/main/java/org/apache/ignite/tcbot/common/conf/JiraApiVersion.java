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

/**
 * JIRA API version.
 */
public enum JiraApiVersion {
    /**
     * https://developer.atlassian.com/cloud/jira/platform/rest/v2/intro
     */
    V2("rest/api/2/", "search?jql="),

    /**
     * https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/
     */
    V3("rest/api/3/", "search/jql?jql=");

    private final String apiUrl;
    private final String searchUrl;

    JiraApiVersion(String apiUrl, String searchUrl) {
        this.apiUrl = apiUrl;
        this.searchUrl = searchUrl;
    }

    /**
     * Returns a relative path to the api.
     *
     * @return relative path to the api.
     */
    public String apiUrl() {
        return apiUrl;
    }

    /**
     * Returns a relative path to the search api.
     *
     * @return relative path to the search api.
     */
    public String searchUrl() {
        return searchUrl;
    }

    /**
     * Returns a default api version.
     *
     * @return default api version.
     */
    public static JiraApiVersion defaultApiVersion() {
        return V2;
    }
}
