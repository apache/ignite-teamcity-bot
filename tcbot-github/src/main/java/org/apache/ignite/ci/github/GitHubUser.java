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

import java.util.Objects;

@Persisted
public class GitHubUser {
    @SerializedName("login") private String login;
    @SerializedName("avatar_url") private String avatarUrl;
    /*See full example in prsList.json */

    /**
     * @return Login.
     */
    public String login() {
        return login;
    }

    /**
     * @return Avatar url.
     */
    public String avatarUrl() {
        return avatarUrl;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GitHubUser user = (GitHubUser)o;
        return Objects.equals(login, user.login) &&
            Objects.equals(avatarUrl, user.avatarUrl);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(login, avatarUrl);
    }
}
