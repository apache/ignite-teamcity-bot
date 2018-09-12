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

/**
 *
 */
public class PullRequest {
    /** Pull Request number. You can see it at {@code apache/ignite/pull/"number"}. */
    @SerializedName("number") private int num;

    /** Pull Request state. */
    private String state;

    /** Pull Request title. */
    private String title;

    /** Pull Request url to get statuses. */
    @SerializedName("statuses_url") private String statusesUrl;

    /**
     * @return Pull Request number.
     */
    public int getNumber() {
        return num;
    }

    /**
     * @return Pull Request state.
     */
    public String getState() {
        return state;
    }

    /**
     * @return Pull Request title.
     */
    public String getTitle() {
        return title;
    }

    /**
     * @return Url to get PR statuses.
     */
    public String getStatusesUrl() {
        return statusesUrl;
    }
}
