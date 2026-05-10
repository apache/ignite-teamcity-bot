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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URLEncoder;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.apache.ignite.ci.github.GitHubBranchShort;
import org.apache.ignite.ci.github.GitHubIssueComment;
import org.apache.ignite.ci.github.GitHubUser;
import org.apache.ignite.ci.github.PullRequest;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;
import org.apache.ignite.tcbot.common.conf.IGitHubConfig;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;

class GitHubConnectionImpl implements IGitHubConnection {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(GitHubConnectionImpl.class);

    /** Config. */
    private final IDataSourcesConfigSupplier cfg;

    /** Service (server) code. */
    private String srvCode;

    /** GitHub read attempts. */
    private static final int READ_ATTEMPTS = 3;

    /** Initial retry backoff. */
    private static final long INITIAL_RETRY_BACKOFF_MS = 500;

    /** Retry jitter. */
    private static final long RETRY_JITTER_MS = 250;

    /** Max retry backoff. */
    private static final long MAX_RETRY_BACKOFF_MS = TimeUnit.SECONDS.toMillis(30);

    private static AtomicLong lastRq = new AtomicLong();

    GitHubConnectionImpl(IDataSourcesConfigSupplier cfg) {
        this.cfg = cfg;
    }

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

        for (int attempt = 1; attempt <= READ_ATTEMPTS; attempt++) {
            try (InputStream is = sendGetToGit(pr, null)) {
                InputStreamReader reader = new InputStreamReader(is);

                return new Gson().fromJson(reader, PullRequest.class);
            }
            catch (IOException e) {
                if (shouldRetry(e, attempt)) {
                    long backoffMs = retryBackoffMs(attempt);

                    logger.warn("Failed to read GitHub pull request, will retry " +
                        "[srv={}, pr={}, url={}, attempt={}/{}, backoffMs={}]",
                        srvCode, id, pr, attempt, READ_ATTEMPTS, backoffMs, e);

                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(backoffMs));

                    continue;
                }

                throw new UncheckedIOException("Failed to read GitHub pull request [srv=" + srvCode +
                    ", pr=" + id + ", url=" + pr + ", attempt=" + attempt + '/' + READ_ATTEMPTS + ']', e);
            }
        }

        throw new IllegalStateException("Unreachable");
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public GitHubUser getUser(String login) {
        String url = userApiUrl(getApiUrlMandatory(), login);

        for (int attempt = 1; attempt <= READ_ATTEMPTS; attempt++) {
            try (InputStream is = sendGetToGit(url, null)) {
                InputStreamReader reader = new InputStreamReader(is);

                return new Gson().fromJson(reader, GitHubUser.class);
            }
            catch (IOException e) {
                if (shouldRetry(e, attempt)) {
                    long backoffMs = retryBackoffMs(attempt);

                    logger.warn("Failed to read GitHub user, will retry " +
                        "[srv={}, login={}, url={}, attempt={}/{}, backoffMs={}]",
                        srvCode, login, url, attempt, READ_ATTEMPTS, backoffMs, e);

                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(backoffMs));

                    continue;
                }

                throw new UncheckedIOException("Failed to read GitHub user [srv=" + srvCode +
                    ", login=" + login + ", url=" + url + ", attempt=" + attempt + '/' + READ_ATTEMPTS + ']', e);
            }
        }

        throw new IllegalStateException("Unreachable");
    }

    /** */
    @Nullable private String notifyGitError(String url, String body) {
        try {
            HttpUtil.sendPostAsStringToGit(config().gitAuthTok(), url, body);

            return null;
        }
        catch (IOException e) {
            String err = e.getClass().getSimpleName() + ": " + e.getMessage();

            logger.error("Failed to notify Git [errMsg={}]", err, e);

            return err;
        }
        catch (RuntimeException e) {
            String err = e.getClass().getSimpleName() + ": " + e.getMessage();

            logger.error("Failed to notify Git [errMsg={}]", err, e);

            return err;
        }
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<GitHubIssueComment> getIssueComments(int prNum) {
        List<GitHubIssueComment> res = new ArrayList<>();
        AtomicReference<String> outLinkNext = new AtomicReference<>();
        String url = getApiUrlMandatory() + "issues/" + prNum + "/comments?per_page=100";

        do {
            HashMap<String, String> rspHeaders = new HashMap<>();
            outLinkNext.set(null);
            rspHeaders.put("Link", null);

            TypeToken<ArrayList<GitHubIssueComment>> tok = new TypeToken<ArrayList<GitHubIssueComment>>() {
            };

            res.addAll(readOnePage(outLinkNext, url, rspHeaders, tok));

            url = outLinkNext.get();
        }
        while (url != null);

        return res;
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public String postIssueCommentError(int prNum, String body) {
        String url = getApiUrlMandatory() + "issues/" + prNum + "/comments";
        HashMap<String, String> req = new HashMap<>();
        req.put("body", body);
        String json = new Gson().toJson(req);

        return notifyGitError(url, json);
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

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public List<PullRequest> getRecentPullRequestsPage(@Nullable String fullUrl,
        @Nullable AtomicReference<String> outLinkNext) {
        String gitApiUrl = getApiUrlMandatory();

        String url = fullUrl != null ? fullUrl : gitApiUrl + "pulls?state=all&sort=updated&direction=desc&per_page=100";

        HashMap<String, String> rspHeaders = new HashMap<>();
        if (outLinkNext != null) {
            outLinkNext.set(null);
            rspHeaders.put("Link", null);
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

    /**
     * @param gitApiUrl Repository API URL.
     * @param login GitHub login.
     */
    static String userApiUrl(String gitApiUrl, String login) {
        Preconditions.checkState(!isNullOrEmpty(gitApiUrl), "Git API URL is not configured.");
        Preconditions.checkState(!isNullOrEmpty(login), "GitHub login is empty.");

        int reposIdx = gitApiUrl.indexOf("/repos/");

        Preconditions.checkState(reposIdx >= 0, "Unsupported Git API URL: " + gitApiUrl);

        String apiRoot = gitApiUrl.substring(0, reposIdx + 1);
        String encodedLogin = URLEncoder.encode(login, StandardCharsets.UTF_8);

        return apiRoot + "users/" + encodedLogin;
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
        for (int attempt = 1; attempt <= READ_ATTEMPTS; attempt++) {
            if (rspHeaders.containsKey("Link"))
                rspHeaders.put("Link", null);

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
                if (shouldRetry(e, attempt)) {
                    long backoffMs = retryBackoffMs(attempt);

                    logger.warn("Failed to read GitHub page, will retry [srv={}, url={}, attempt={}/{}, backoffMs={}]",
                        srvCode, url, attempt, READ_ATTEMPTS, backoffMs, e);

                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(backoffMs));

                    continue;
                }

                throw new UncheckedIOException("Failed to read GitHub page [srv=" + srvCode +
                    ", url=" + url + ", link=" + rspHeaders.get("Link") +
                    ", attempt=" + attempt + '/' + READ_ATTEMPTS + ']', e);
            }
        }

        throw new IllegalStateException("Unreachable");
    }

    /**
     * @param e Exception.
     * @param attempt Attempt.
     */
    private boolean shouldRetry(IOException e, int attempt) {
        return attempt < READ_ATTEMPTS && isTemporaryTransportFailure(e);
    }

    /**
     * @param e Exception.
     */
    private boolean isTemporaryTransportFailure(Throwable e) {
        for (Throwable th = e; th != null; th = th.getCause()) {
            if (th instanceof ConnectException || th instanceof SocketException || th instanceof SocketTimeoutException)
                return true;
        }

        return false;
    }

    /**
     * @param attempt Attempt.
     */
    private long retryBackoffMs(int attempt) {
        long base = INITIAL_RETRY_BACKOFF_MS << (attempt - 1);
        long backoff = base + ThreadLocalRandom.current().nextLong(RETRY_JITTER_MS + 1);

        return Math.min(backoff, MAX_RETRY_BACKOFF_MS);
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
