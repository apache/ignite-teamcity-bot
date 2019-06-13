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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.ci.github.GitHubBranchShort;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static com.google.common.base.Strings.isNullOrEmpty;

class GitHubConnectionImpl implements IGitHubConnection {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(GitHubConnectionImpl.class);

    /** Config. */
    @Inject
    private IDataSourcesConfigSupplier cfg;

    /** Service (server) code. */
    private String srvCode;

    private static AtomicLong lastRq = new AtomicLong();

    /**
     * @param linkRspHdrVal Value of Link response HTTP header.
     */
    @Nullable public static String parseNextLinkFromLinkRspHeader(String linkRspHdrVal) {
        String nextLink = null;
        StringTokenizer tokenizer = new StringTokenizer(linkRspHdrVal, ",");
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
        Preconditions.checkState(this.srvCode == null, "Server re-init is not supported");

        this.srvCode = srvCode;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public PullRequest getPullRequest(Integer id) {
        String gitApiUrl = getApiUrlMandatory();

        String pr = gitApiUrl + "pulls/" + id;

        try (InputStream is = sendGetToGit(pr, null)) {
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
            HttpUtil.sendPostAsStringToGit(config().gitAuthTok(), url, body);

            return true;
        }
        catch (IOException e) {
            logger.error("Failed to notify Git [errMsg=" + e.getMessage() + ']');

            return false;
        }
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<PullRequest> getPullRequestsPage(@Nullable String fullUrl,
        @Nullable AtomicReference<String> outLinkNext) {
        String gitApiUrl = getApiUrlMandatory();

        String url = fullUrl != null ? fullUrl : gitApiUrl + "pulls?sort=updated&direction=desc";

        HashMap<String, String> rspHeaders = new HashMap<>();
        if (outLinkNext != null) {
            outLinkNext.set(null);
            rspHeaders.put("Link", null); // requesting header
        }

        TypeToken<ArrayList<PullRequest>> tok = new TypeToken<ArrayList<PullRequest>>() {
        };

        return readOnePage(outLinkNext, url, rspHeaders, tok);
    }

    @Nonnull public String getApiUrlMandatory() {
        String gitApiUrl = config().gitApiUrl();

        Preconditions.checkState(!isNullOrEmpty(gitApiUrl), "Git API URL is not configured for this server.");
        return gitApiUrl;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<GitHubBranchShort> getBranchesPage(@Nullable String fullUrl,
                                                             @Nonnull AtomicReference<String> outLinkNext) {
        String url = fullUrl != null ? fullUrl : getApiUrlMandatory() + "branches";

        HashMap<String, String> rspHeaders = new HashMap<>();
        outLinkNext.set(null);
        rspHeaders.put("Link", null); // requesting header

        TypeToken<ArrayList<GitHubBranchShort>> tok = new TypeToken<ArrayList<GitHubBranchShort>>() {
        };

        return this.readOnePage(outLinkNext, url, rspHeaders, tok);
    }

    public <T> List<T> readOnePage(@Nullable AtomicReference<String> outLinkNext,
        String url, HashMap<String, String> rspHeaders, TypeToken<ArrayList<T>> typeTok) {
        try (InputStream stream = sendGetToGit(url, rspHeaders)) {
            InputStreamReader reader = new InputStreamReader(stream);
            List<T> list = new Gson().fromJson(reader, typeTok.getType());
            String link = rspHeaders.get("Link");

            if (link != null) {
                String nextLink = parseNextLinkFromLinkRspHeader(link);

                if (nextLink != null && outLinkNext != null)
                    outLinkNext.set(nextLink);
            }

            logger.info("Processing Github link: " + link);

            return list;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    protected InputStream sendGetToGit(String url, HashMap<String, String> rspHeaders) throws IOException {
        final String tok = config().gitAuthTok();

        velocityControl(tok);

        return HttpUtil.sendGetToGit(tok, url, rspHeaders);
    }

    //https://developer.github.com/v3/#rate-limiting
    @AutoProfiling
    protected void velocityControl(String tok) {
        final int reqPerHour = Strings.isNullOrEmpty(tok) ? 60 : 5000;
        final long nanosInHour = Duration.ofHours(1).toNanos();
        final long waitBeforeNextReq = nanosInHour / reqPerHour;

        boolean win;
        do {
            final long lastRq = this.lastRq.get();

            final long curNs = System.nanoTime();

            if (lastRq != 0) {
                final long nanosPassed = curNs - lastRq;
                final long nsWait = waitBeforeNextReq - nanosPassed;

                if (nsWait > 0)
                    LockSupport.parkNanos(nsWait);
            }

            win = this.lastRq.compareAndSet(lastRq, curNs);
        } while (!win);
    }

    /** {@inheritDoc} */
    @Override public IGitHubConfig config() {
        Preconditions.checkNotNull(srvCode);

        return cfg.getGitConfig(srvCode);
    }
}
