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

package org.apache.ignite.ci.tcbot.visa;

import com.google.common.base.Strings;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.apache.ignite.ci.github.GitHubUser;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.tcbot.engine.user.IUserStorage;

/**
 * Resolves bot users to GitHub accounts using explicit profile settings first, then public GitHub email.
 */
public class GitHubUserResolver {
    /** */
    @Inject private IUserStorage userStorage;

    /**
     * @param username Bot user name.
     * @param candidates GitHub users already loaded in the bot cache.
     */
    public Resolution resolve(@Nullable String username, @Nullable Collection<GitHubUser> candidates) {
        if (Strings.isNullOrEmpty(username))
            return Resolution.empty();

        return resolve(userStorage.getUser(username), candidates);
    }

    /**
     * @param user Bot user.
     * @param candidates GitHub users already loaded in the bot cache.
     */
    public Resolution resolve(@Nullable TcHelperUser user, @Nullable Collection<GitHubUser> candidates) {
        if (user == null)
            return Resolution.empty();

        Set<String> configured = new LinkedHashSet<>(user.getGithubIds());
        Set<String> auto = new LinkedHashSet<>();

        if (candidates != null) {
            for (GitHubUser candidate : candidates) {
                if (candidate == null || Strings.isNullOrEmpty(candidate.login()))
                    continue;

                if (!Strings.isNullOrEmpty(candidate.email()) && user.containsEmail(candidate.email()))
                    auto.add(candidate.login());
            }
        }

        return new Resolution(configured, auto);
    }

    /**
     * @param user Bot user.
     * @param gitHubUser GitHub user.
     */
    public boolean matches(@Nullable TcHelperUser user, @Nullable GitHubUser gitHubUser) {
        if (user == null || gitHubUser == null)
            return false;

        return matchesLogin(user, gitHubUser.login()) ||
            (!Strings.isNullOrEmpty(gitHubUser.email()) && user.containsEmail(gitHubUser.email()));
    }

    /**
     * @param user Bot user.
     * @param login GitHub login.
     */
    public boolean matchesLogin(@Nullable TcHelperUser user, @Nullable String login) {
        if (user == null || Strings.isNullOrEmpty(login))
            return false;

        return user.getGithubIds().stream().anyMatch(login::equalsIgnoreCase);
    }

    /**
     * @param prs Pull requests.
     */
    public static List<GitHubUser> authors(@Nullable Collection<PullRequest> prs) {
        if (prs == null)
            return java.util.Collections.emptyList();

        return prs.stream()
            .map(PullRequest::gitHubUser)
            .filter(user -> user != null && !Strings.isNullOrEmpty(user.login()))
            .collect(Collectors.toList());
    }

    /** GitHub resolution result. */
    public static class Resolution {
        /** */
        public final Set<String> configuredLogins;

        /** */
        public final Set<String> autoResolvedLogins;

        /**
         * @param configuredLogins Explicitly configured GitHub logins.
         * @param autoResolvedLogins GitHub logins matched by email.
         */
        private Resolution(Set<String> configuredLogins, Set<String> autoResolvedLogins) {
            this.configuredLogins = configuredLogins;
            this.autoResolvedLogins = autoResolvedLogins;
        }

        /**
         * @return Empty result.
         */
        public static Resolution empty() {
            return new Resolution(new LinkedHashSet<>(), new LinkedHashSet<>());
        }

        /**
         * @return All matched GitHub logins.
         */
        public Set<String> allLogins() {
            Set<String> res = new LinkedHashSet<>(configuredLogins);

            res.addAll(autoResolvedLogins);

            return res;
        }
    }
}
