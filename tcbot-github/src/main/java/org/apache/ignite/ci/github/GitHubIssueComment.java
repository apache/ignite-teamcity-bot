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
package org.apache.ignite.ci.github;

import com.google.gson.annotations.SerializedName;
import org.apache.ignite.tcbot.persistence.Persisted;

/**
 * GitHub issue comment. Pull request timeline comments are exposed through the issue comments API.
 */
@Persisted
public class GitHubIssueComment {
    /** Comment body. */
    private String body;

    /** HTML URL. */
    @SerializedName("html_url") private String htmlUrl;

    /**
     * @return Comment body.
     */
    public String body() {
        return body;
    }

    /**
     * @return HTML URL.
     */
    public String htmlUrl() {
        return htmlUrl;
    }
}
