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

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.ci.tcbot.conf.IGitHubConfig;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.util.HttpUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;

class GitHubConnectionImpl implements IGitHubConnection {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(GitHubConnectionImpl.class);

    /** URL for git integration. */
    private String gitApiUrl;

    /** GitHub authorization token. */
    private String gitAuthTok;

    @Inject
    ITcBotConfig config;

    private String srvCode;

    @Nullable public static String parseNextLinkFromLinkRspHeader(String s) {
        String nextLink = null;
        StringTokenizer tokenizer = new StringTokenizer(s, ",");
        for (; tokenizer.hasMoreTokens(); ) {
            String tok = tokenizer.nextToken();

            List<String> linkAndRel = new ArrayList<>();
            StringTokenizer tokenizerForLink = new StringTokenizer(tok, ";");
            for (; tokenizerForLink.hasMoreTokens(); ) {
                String nextTok = tokenizerForLink.nextToken();
                linkAndRel.add(nextTok);
            }

            if (linkAndRel.size() >= 2) {
                String linkType = linkAndRel.get(1);
                if ("rel=\"next\"".equals(linkType.trim()))
                    nextLink = linkAndRel.get(0).trim();
            }
        }

        if (!isNullOrEmpty(nextLink)) {
            if (nextLink.startsWith("<"))
                nextLink = nextLink.substring(1);
            if (nextLink.endsWith(">"))
                nextLink = nextLink.substring(0, nextLink.length() - 1);
        }
        return nextLink;
    }

    /** {@inheritDoc} */
    @Override public void init(String srvCode) {
        this.srvCode = srvCode;
        final File workDir = HelperConfig.resolveWorkDir();

        String cfgName = HelperConfig.prepareConfigName(srvCode);

        final Properties props = HelperConfig.loadAuthProperties(workDir, cfgName);

        gitAuthTok = HelperConfig.prepareGithubHttpAuthToken(props);
        gitApiUrl = props.getProperty(HelperConfig.GIT_API_URL);

    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public PullRequest getPullRequest(Integer id) {
        Preconditions.checkState(!isNullOrEmpty(gitApiUrl), "Git API URL is not configured for this server.");

        String pr = gitApiUrl + "pulls/" + id;

        try (InputStream is = HttpUtil.sendGetToGit(gitAuthTok, pr, null)) {
            InputStreamReader reader = new InputStreamReader(is);

            return new Gson().fromJson(reader, PullRequest.class);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public boolean notifyGit(String url, String body) {
        try {
            HttpUtil.sendPostAsStringToGit(gitAuthTok, url, body);

            return true;
        }
        catch (IOException e) {
            logger.error("Failed to notify Git [errMsg=" + e.getMessage() + ']');

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isGitTokenAvailable() {
        return gitAuthTok != null;
    }

    /** {@inheritDoc} */
    @Override public String gitApiUrl() {
        return gitApiUrl;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<PullRequest> getPullRequests(@Nullable String fullUrl,
        @Nullable AtomicReference<String> outLinkNext) {
        Preconditions.checkState(!isNullOrEmpty(gitApiUrl), "Git API URL is not configured for this server.");

        String url = fullUrl != null ? fullUrl : gitApiUrl + "pulls?sort=updated&direction=desc";

        HashMap<String, String> rspHeaders = new HashMap<>();
        if (outLinkNext != null) {
            outLinkNext.set(null);
            rspHeaders.put("Link", null); // requesting header
        }

        try (InputStream stream = HttpUtil.sendGetToGit(gitAuthTok, url, rspHeaders)) {
            InputStreamReader reader = new InputStreamReader(stream);
            Type listType = new TypeToken<ArrayList<PullRequest>>() {
            }.getType();

            List<PullRequest> list = new Gson().fromJson(reader, listType);
            String link = rspHeaders.get("Link");

            if (link != null) {
                String nextLink = parseNextLinkFromLinkRspHeader(link);

                if (nextLink != null)
                    outLinkNext.set(nextLink);
            }

            logger.info("Processing Github link: " + link);

            return list;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public IGitHubConfig config() {
        return config.getGitConfig(srvCode);
    }
}
