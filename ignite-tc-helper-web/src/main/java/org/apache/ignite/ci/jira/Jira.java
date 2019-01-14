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

package org.apache.ignite.ci.jira;

import com.google.common.base.Preconditions;
import java.io.File;
import java.util.Properties;
import javax.inject.Inject;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.jira.pure.IJiraIntegration;
import org.apache.ignite.ci.user.ICredentialsProv;
import org.apache.ignite.ci.web.model.Visa;
import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 *
 */
public class Jira implements IJiraIntegration {
    /** */
    @Inject ITcHelper helper;

    /** */
    private String jiraUrl;

    /** JIRA ticket prefix. */
    @NotNull private String jiraTicketPrefix;

    /** {@inheritDoc} */
    @Override public void init(String srvId) {
        final File workDir = HelperConfig.resolveWorkDir();

        final String cfgName = HelperConfig.prepareConfigName(srvId);

        final Properties props = HelperConfig.loadAuthProperties(workDir, cfgName);

        jiraUrl = props.getProperty(HelperConfig.JIRA_URL);

        jiraTicketPrefix = props.getProperty(HelperConfig.JIRA_TICKET_TEMPLATE, "IGNITE-");
    }

    /** {@inheritDoc} */
    @Override public String jiraUrl() {
        return jiraUrl;
    }

    /** {@inheritDoc} */
    @Override public String ticketPrefix() {
        return jiraTicketPrefix;
    }

    /** {@inheritDoc} */
    @Override public Visa notifyJira(String srvId, ICredentialsProv prov, String buildTypeId, String branchForTc,
        String ticket) {
        return helper.notifyJira(srvId, prov, buildTypeId, branchForTc, ticket);
    }

    /** {@inheritDoc} */
    @Override public Tickets getTickets(String srvId, ICredentialsProv prov, String url) {
        return helper.getJiraTickets(srvId, prov, url);
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
}
