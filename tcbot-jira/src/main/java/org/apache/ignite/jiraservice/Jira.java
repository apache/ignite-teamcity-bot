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

package org.apache.ignite.jiraservice;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.ignite.tcbot.common.conf.IDataSourcesConfigSupplier;
import org.apache.ignite.tcbot.common.interceptor.AutoProfiling;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.common.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Implementation of pure JIRA interaction.
 */
class Jira implements IJiraIntegration {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(Jira.class);

    /** Server id. */
    private String srvCode;

    /** Config. */
    @Inject private IDataSourcesConfigSupplier cfg;

    /** {@inheritDoc} */
    @Override public void init(String srvCode) {
        this.srvCode = srvCode;
    }

    /** {@inheritDoc} */
    @Override public Tickets getTicketsPage(String url) {
        try {
            Gson gson = new GsonBuilder().registerTypeAdapterFactory(new ReadResolveFactory()).create();

            return gson.fromJson(sendGetToJira(url), Tickets.class);
        }
        catch (Exception e) {
            String errMsg = "Exception happened during receiving JIRA tickets " +
                "[url=" + url + ", errMsg=" + e.getMessage() + ']';

            logger.error(errMsg, e);

            throw new IllegalStateException(errMsg, e);
        }
    }

    /** {@inheritDoc} */
    @Override public String generateTicketUrl(String ticketFullName) {
        @Nullable String jiraUrl = config().getUrl();

        Preconditions.checkState(!isNullOrEmpty(jiraUrl), "Jira URL is not configured for this server.");

        return jiraUrl + "browse/" + ticketFullName;
    }

    /** {@inheritDoc} */
    @Override public String generateCommentUrl(String ticketFullName, int commentId) {
        return generateTicketUrl(ticketFullName) +
            "?focusedCommentId=" + commentId +
            "&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-" +
            commentId;
    }

    /** {@inheritDoc} */
    @Override public IJiraServerConfig config() {
        return cfg.getJiraConfig(srvCode);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public String postJiraComment(String ticket, String comment) throws IOException {
        String jiraApiUrl = config().restApiUrl();

        String url = jiraApiUrl + "issue/" + ticket + "/comment";

        return HttpUtil.sendPostAsStringToJira(config().decodedHttpAuthToken(), url, "{\"body\": \"" + comment + "\"}");
    }

    /**
     * @param url Url, relative, should not contain any start slashes.
     * @return Response as gson string.
     */
    public String sendGetToJira(String url) throws IOException {
        return HttpUtil.sendGetToJira(config().decodedHttpAuthToken(), config().restApiUrl() + url);
    }
}
