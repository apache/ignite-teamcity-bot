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
package org.apache.ignite.githubservice;

import com.google.common.base.Strings;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ignite.ci.github.GitHubBranchShort;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * GitHub pure connection
 */
public interface IGitHubConnection {
    public void init(String srvCode);

    /** */
    public PullRequest getPullRequest(Integer id);

    /**
     * Send POST request with given body.
     *
     * @param url Url.
     * @param body Request body.
     * @return {@code True} - if GitHub was notified. {@code False} - otherwise.
     */
    public boolean notifyGit(String url, String body);

    /**
     * @param fullUrl Full url - null for first page, not null for next page.
     * @param outLinkNext Out link for return next page full url.
     */
    public List<PullRequest> getPullRequestsPage(@Nullable String fullUrl, @Nullable AtomicReference<String> outLinkNext);

    /**
     * @param fullUrl Full url - null for first page, not null for next page.
     * @param outLinkNext Out link for return next page full url.
     */
    public List<GitHubBranchShort> getBranchesPage(@Nullable String fullUrl, @Nonnull AtomicReference<String> outLinkNext);

    /**
     * @return PR id from string "pull/XXXX/head"
     */
    @Nullable public static Integer convertBranchToPrId(String branchForTc) {
        Integer res = null;

        if (Objects.isNull(branchForTc))
            return res;

        String id = null;

        for (int i = 5; i < branchForTc.length(); i++) {
            char c = branchForTc.charAt(i);

            if (!Character.isDigit(c)) {
                id = branchForTc.substring(5, i);

                break;
            }
        }

        return Strings.isNullOrEmpty(id) ? null : Integer.parseInt(id);
    }

    /**
     *
     */
    public IGitHubConfig config();
}
