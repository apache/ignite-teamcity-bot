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
import org.apache.ignite.ci.IAnalyticsEnabledTeamcity;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.di.AutoProfiling;
import org.apache.ignite.ci.jira.Tickets;
import org.apache.ignite.ci.tcbot.visa.TcBotTriggerAndSignOffService;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.util.HttpUtil;
import org.apache.ignite.ci.web.model.Visa;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 *
 */
class Jira implements IJiraIntegration {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(Jira.class);

    /** */
    private String jiraUrl;

    /** JIRA ticket prefix. */
    @NotNull private String jiraTicketPrefix;

    /** JIRA authorization token. */
    private String jiraBasicAuthTok;

    /** URL for JIRA integration. */
    private String jiraApiUrl;

    /** {@inheritDoc} */
    @Override public void init(String srvId) {
        final File workDir = HelperConfig.resolveWorkDir();

        final String cfgName = HelperConfig.prepareConfigName(srvId);

        final Properties props = HelperConfig.loadAuthProperties(workDir, cfgName);

        jiraUrl = props.getProperty(HelperConfig.JIRA_URL);

        jiraTicketPrefix = props.getProperty(HelperConfig.JIRA_TICKET_TEMPLATE, "IGNITE-");

        jiraBasicAuthTok = HelperConfig.prepareJiraHttpAuthToken(props);
        jiraApiUrl = props.getProperty(HelperConfig.JIRA_API_URL);
    }

    /**
     * @return {@code True} if JIRA authorization token is available.
     */
    // boolean isJiraTokenAvailable();

    /** {@inheritDoc} */
    @Override public String jiraUrl() {
        return jiraUrl;
    }

    /** {@inheritDoc} */
    @Override public String ticketPrefix() {
        return jiraTicketPrefix;
    }

    /** {@inheritDoc} */
    @Override public Tickets getTickets(String srvId, ICredentialsProv prov, String url) {
        return getJiraTickets(srvId, prov, url);
    }

    /** {@inheritDoc} */
    public Tickets getJiraTickets(String srvId, ICredentialsProv prov, String url) {
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
    @Override public String getJiraApiUrl() {
        return jiraApiUrl;
    }

    /** {@inheritDoc} */
    @Override public boolean isJiraTokenAvailable() {
        return !Strings.isNullOrEmpty(jiraBasicAuthTok);
    }

    /** {@inheritDoc} */
    @AutoProfiling
    @Override public String sendJiraComment(String ticket, String comment) throws IOException {
        if (isNullOrEmpty(jiraApiUrl))
            throw new IllegalStateException("JIRA API URL is not configured for this server.");

        String url = jiraApiUrl + "issue/" + ticket + "/comment";

        return HttpUtil.sendPostAsStringToJira(jiraBasicAuthTok, url, "{\"body\": \"" + comment + "\"}");
    }

    /**
     * @param url Url.
     * @return Response as gson string.
     */
    public String sendGetToJira(String url) throws IOException {
        if (isNullOrEmpty(jiraApiUrl))
            throw new IllegalStateException("JIRA API URL is not configured for this server.");

        return HttpUtil.sendGetToJira(jiraBasicAuthTok, jiraApiUrl + url);
    }

}
