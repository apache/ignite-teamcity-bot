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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gson.annotations.SerializedName;
import java.util.Objects;

import org.apache.ignite.tcbot.persistence.IVersionedEntity;
import org.apache.ignite.tcbot.persistence.Persisted;

import javax.annotation.Nullable;

/**
 *
 */
@Persisted
public class PullRequest implements IVersionedEntity {
    /** Open status. */
    public static final String OPEN = "open";

    /** Symbols count to include to short commit version. */
    public static final int INCLUDE_SHORT_VER = 7;

    /** Entitiy current (latest) version. */
    private static final int LATEST_VERSION = 7;

    /** Entity version. */
    @SuppressWarnings("FieldCanBeLocal") private Integer _ver = LATEST_VERSION;

    /** Pull Request number. You can see it at {@code apache/ignite/pull/"number"}. */
    @SerializedName("number") private int num;

    /** Pull Request state. */
    private String state;

    /** Pull Request title. */
    private String title;

    @SerializedName("html_url") private String htmlUrl;

    @SerializedName("updated_at") private String updatedAt;

    /** Pull Request statuses URL. */
    @SerializedName("statuses_url") private String statusesUrl;

    @SerializedName("user") private GitHubUser gitHubUser;

    @SerializedName("head") private GitHubBranch head;

    @SerializedName("base") private GitHubBranch base;

    /**
     * @return Pull Request time update.
     */
    public String getTimeUpdate() {
        return updatedAt;
    }

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

    /**
     * @return Git hub user.
     */
    public GitHubUser gitHubUser() {
        return gitHubUser;
    }

    /**
     * @return Html url.
     */
    public String htmlUrl() {
        return htmlUrl;
    }

    /**
     * @return Head.
     */
    public GitHubBranch head() {
        return head;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("num", num)
            .add("state", state)
            .add("title", title)
            .add("statusesUrl", statusesUrl)
            .toString();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        PullRequest req = (PullRequest)o;

        return num == req.num &&
            Objects.equals(_ver, req._ver) &&
            Objects.equals(state, req.state) &&
            Objects.equals(title, req.title) &&
            Objects.equals(htmlUrl, req.htmlUrl) &&
            Objects.equals(updatedAt, req.updatedAt) &&
            Objects.equals(statusesUrl, req.statusesUrl) &&
            Objects.equals(gitHubUser, req.gitHubUser) &&
            Objects.equals(head, req.head) &&
            Objects.equals(base, req.base);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(_ver, num, state, title, htmlUrl, updatedAt, statusesUrl, gitHubUser, head, base);
    }

    /** {@inheritDoc} */
    @Override public int version() {
        return _ver == null ? -1 : _ver;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    @Nullable  public String lastCommitShaShort() {
        String sha = head().sha();

        return Strings.isNullOrEmpty(sha) ? null : sha.substring(0, INCLUDE_SHORT_VER);
    }
}
