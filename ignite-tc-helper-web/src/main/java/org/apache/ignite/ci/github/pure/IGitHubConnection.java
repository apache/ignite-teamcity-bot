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
package org.apache.ignite.ci.github.pure;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ignite.ci.github.PullRequest;
import org.jetbrains.annotations.Nullable;

public interface IGitHubConnection {

    void init(String srvId);

    /**
     * @param branch TeamCity's branch name. Looks like "pull/123/head".
     * @return Pull Request.
     */
    PullRequest getPullRequest(String branch);

    /** */
    PullRequest getPullRequest(Integer id);

    /**
     * Send POST request with given body.
     *
     * @param url Url.
     * @param body Request body.
     * @return {@code True} - if GitHub was notified. {@code False} - otherwise.
     */
    boolean notifyGit(String url, String body);

    /**
     * @return {@code True} if GitHub authorization token is available.
     */
    boolean isGitTokenAvailable();

    /**
     * @return URL for git integration.
     */
    String gitApiUrl();

    List<PullRequest> getPullRequests(@Nullable String fullUrl, @Nullable AtomicReference<String> outLinkNext);

    /**
     * @return PR id from string "pull/XXXX/head"
     */
    public static Integer convertBranchToId(String branchForTc) {
        String id = null;

        for (int i = 5; i < branchForTc.length(); i++) {
            char c = branchForTc.charAt(i);

            if (!Character.isDigit(c)) {
                id = branchForTc.substring(5, i);

                break;
            }
        }

        return Integer.parseInt(id);
    }
}
