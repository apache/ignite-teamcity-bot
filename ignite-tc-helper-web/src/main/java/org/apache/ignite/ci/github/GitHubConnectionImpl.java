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

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.util.HttpUtil;
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

    /** {@inheritDoc} */
    @Override public void init(String srvId) {
        final File workDir = HelperConfig.resolveWorkDir();

        String cfgName = HelperConfig.prepareConfigName(srvId);

        final Properties props = HelperConfig.loadAuthProperties(workDir, cfgName);

        gitAuthTok = (HelperConfig.prepareGithubHttpAuthToken(props));
        gitApiUrl = (props.getProperty(HelperConfig.GIT_API_URL));
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public PullRequest getPullRequest(String branchForTc) {
        Preconditions.checkState( !isNullOrEmpty(gitApiUrl) , "Git API URL is not configured for this server.");

        String id = null;

        // Get PR id from string "pull/XXXX/head"
        for (int i = 5; i < branchForTc.length(); i++) {
            char c = branchForTc.charAt(i);

            if (!Character.isDigit(c)) {
                id = branchForTc.substring(5, i);

                break;
            }
        }

        String pr = gitApiUrl + "pulls/" + id;

        try (InputStream is = HttpUtil.sendGetToGit(gitAuthTok, pr)) {
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
            logger.error("Failed to notify Git [errMsg="+e.getMessage()+']');

            return false;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isGitTokenAvailable() {
        return gitAuthTok != null;
    }

    /**
     * @return URL for git integration.
     */
    public String gitApiUrl() {
        return gitApiUrl;
    }

    @Override public void getPullRequests() {
        Preconditions.checkState( !isNullOrEmpty(gitApiUrl) , "Git API URL is not configured for this server.");

        String s = gitApiUrl + "pulls?sort=updated&direction=desc";

        if(s!=null)
        return;
        try( InputStream stream = HttpUtil.sendGetToGit(gitAuthTok, s )){
           ;
            InputStreamReader reader = new InputStreamReader(stream);
            HashMap<String, Object> map = new Gson().fromJson(reader, HashMap .class);

            System.out.println(map);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
