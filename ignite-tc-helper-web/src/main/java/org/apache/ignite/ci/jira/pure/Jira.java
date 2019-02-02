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

package org.apache.ignite.ci.jira.pure;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import javax.inject.Inject;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.jira.Tickets;
import org.apache.ignite.ci.tcbot.conf.IJiraServerConfig;
import org.apache.ignite.ci.tcbot.conf.ITcBotConfig;
import org.apache.ignite.ci.util.HttpUtil;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Implementation of pure JIRA interaction.
 */
class Jira implements IJiraIntegration {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(Jira.class);

    /** Server id. */
    private String srvId;

    /** Config. */
    @Inject ITcBotConfig cfg;

    /** {@inheritDoc} */
    @Override public void init(String srvId) {
        this.srvId = srvId;

    }

    /** {@inheritDoc} */
    @Override public Tickets getTicketsPage(String url) {
        try {
            return new Gson().fromJson(sendGetToJira(url), Tickets.class);
        }
        catch (Exception e) {
            String errMsg = "Exception happened during receiving JIRA tickets " +
                "[url=" + url + ", errMsg=" + e.getMessage() + ']';

            logger.error(errMsg);

            return new Tickets();
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
        return cfg.getJiraConfig(srvId);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public String postJiraComment(String ticket, String comment) throws IOException {
        String jiraApiUrl = restApiUrl();

        String url = jiraApiUrl + "issue/" + ticket + "/comment";

        return HttpUtil.sendPostAsStringToJira(config().decodedHttpAuthToken(), url, "{\"body\": \"" + comment + "\"}");
    }

    /** {@inheritDoc} */
    @Override @NotNull public String restApiUrl() {
        String jiraUrl = config().getUrl();

        if (isNullOrEmpty(jiraUrl))
            throw new IllegalStateException("JIRA API URL is not configured for this server.");

        StringBuilder apiUrl = new StringBuilder();

        apiUrl.append(jiraUrl);
        if (!jiraUrl.endsWith("/"))
            apiUrl.append("/");

        apiUrl.append("rest/api/2/");

        return apiUrl.toString();
    }

    /**
     * @param url Url, relative, should not contain any start slashes.
     * @return Response as gson string.
     */
    public String sendGetToJira(String url) throws IOException {
        return HttpUtil.sendGetToJira(config().decodedHttpAuthToken(), restApiUrl() + url);
    }

    /** {@inheritDoc} */
    @Override public String getServiceId() {
        return srvId;
    }
}
