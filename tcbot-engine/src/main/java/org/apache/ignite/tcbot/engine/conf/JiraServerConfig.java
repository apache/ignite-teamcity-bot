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
package org.apache.ignite.tcbot.engine.conf;

import com.google.common.base.Strings;
import java.util.Properties;
import javax.annotation.Nullable;
import org.apache.ignite.jiraservice.Ticket;
import org.apache.ignite.tcbot.common.conf.IJiraServerConfig;
import org.apache.ignite.tcbot.common.conf.JiraApiVersion;
import org.apache.ignite.tcbot.common.conf.PasswordEncoder;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 *
 */
public class JiraServerConfig implements IJiraServerConfig {
    /** JIRA authorization token property name. */
    public static final String JIRA_AUTH_TOKEN = "jira.auth_token";

    /** JIRA authorization token PasswordEncoder flag property name. */
    public static final String JIRA_AUTH_TOKEN_ENCODED = "jira.auth_token.encoded";

    /** JIRA authorization scheme property name. */
    public static final String JIRA_AUTH_SCHEME = "jira.auth_scheme";

    /** JIRA Basic authorization scheme. */
    public static final String JIRA_AUTH_SCHEME_BASIC = "Basic";

    /** JIRA Bearer authorization scheme. */
    public static final String JIRA_AUTH_SCHEME_BEARER = "Bearer";

    /** JIRA URL to build links to tickets. */
    public static final String JIRA_URL = "jira.url";

    /** Prefix for JIRA ticket names. */
    @Deprecated
    public static final String JIRA_TICKET_TEMPLATE = "jira.ticket_template";

    /** Service (server) Name. */
    private String code;

    /**
     * Tickets for commenting in JIRA and finding out PA tickets. Default project is "IGNITE".
     */
    private String projectCode;

    /**
     * Branch number prefix. Optional, if not present {@link #projectCode}-NNNNN is searched.<br> But if branch has
     * different enumeration, this prefix will be searched instead.<br> If specified, that meant tickets maching
     * branches have another identification.<br> For exaple some ticket having ID {@link #projectCode}-N1 will be
     * commented, but a branch will be identified using {@link #branchNumPrefix}N2 with another number.<br><br>
     *
     * Search of branches will be performed using data in JIRA ticket fields for {@link #projectCode}-N1, fields are
     * listed in {@link Ticket} class.
     */
    private String branchNumPrefix;

    private Properties props;

    /**
     * JIRA Auth token to access JIRA. Plain and {@link PasswordEncoder}-encoded tokens are auto-detected unless
     * {@link #authTokEncoded} is set explicitly.
     */
    private String authTok;

    /**
     * {@code True} if {@link #authTok} is encoded with {@link PasswordEncoder}, {@code false} if it is plain, or
     * {@code null} to auto-detect.
     */
    private Boolean authTokEncoded;

    /**
     * HTTP Authorization scheme. Use {@code Bearer} for JIRA personal access tokens and {@code Basic} for legacy
     * base64 username/password tokens.
     */
    private String authScheme;

    /**
     * JIRA Server URL. HTTPs is highly recommended.
     */
    private String url;

    /**
     * JIRA API version.
     * We use the default api version if it is not specified by the configuration.
     **/
    private JiraApiVersion apiVersion = JiraApiVersion.defaultApiVersion();

    public JiraServerConfig() {
    }

    public JiraServerConfig(String code, Properties props) {
        this.code = code;
        this.props = props;
    }

    /** {@inheritDoc} */
    @Override public String getCode() {
        return code;
    }

    /**
     * @param props Properties.
     */
    public JiraServerConfig properties(Properties props) {
        this.props = props;

        return this;
    }

    /**
     * @param code Name.
     */
    public JiraServerConfig code(String code) {
        this.code = code;

        return this;
    }

    /** {@inheritDoc} */
    @Override public String getUrl() {
        if (Strings.isNullOrEmpty(url) && props != null)
            return props.getProperty(JIRA_URL);

        return url;
    }

    /** {@inheritDoc} */
    @Override public JiraApiVersion getApiVersion() {
        return apiVersion;
    }

    /** {@inheritDoc} */
    @Override public String projectCodeForVisa() {
        if (Strings.isNullOrEmpty(projectCode) && props != null) {
            String ticketPref = props.getProperty(JIRA_TICKET_TEMPLATE, "IGNITE-");

            return ticketPref.replaceAll("-", "");
        }

        return projectCode;
    }

    /** {@inheritDoc} */
    @Nullable @Override public String branchNumPrefix() {
        return Strings.emptyToNull(branchNumPrefix);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public String decodedHttpAuthToken() {
        String tok = authTokenConfigured();

        if (isNullOrEmpty(tok))
            return null;

        Boolean encoded = isAuthTokenEncoded();

        if (encoded == null)
            return PasswordEncoder.decodeIfEncoded(tok);

        return encoded ? PasswordEncoder.decode(tok) : tok;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public String httpAuthorizationHeader() {
        String tok = decodedHttpAuthToken();

        return isNullOrEmpty(tok) ? null : authScheme() + " " + tok;
    }

    /**
     * @return Configured auth token.
     */
    @Nullable
    private String authTokenConfigured() {
        if (!Strings.isNullOrEmpty(authTok))
            return authTok;

        return props != null ? props.getProperty(JIRA_AUTH_TOKEN) : null;
    }

    /**
     * @return {@code True} if configured auth token is encoded with {@link PasswordEncoder}, {@code false} if it is
     * plain, or {@code null} if it should be auto-detected.
     */
    @Nullable
    private Boolean isAuthTokenEncoded() {
        if (authTokEncoded != null)
            return authTokEncoded;

        if (props != null && Strings.isNullOrEmpty(authTok)) {
            String encoded = props.getProperty(JIRA_AUTH_TOKEN_ENCODED);

            return Strings.isNullOrEmpty(encoded) ? null : Boolean.parseBoolean(encoded);
        }

        return null;
    }

    /**
     * @return HTTP Authorization scheme.
     */
    private String authScheme() {
        String scheme;

        if (!Strings.isNullOrEmpty(authScheme))
            scheme = authScheme;
        else if (props != null && Strings.isNullOrEmpty(authTok))
            scheme = props.getProperty(JIRA_AUTH_SCHEME, JIRA_AUTH_SCHEME_BASIC);
        else
            scheme = JIRA_AUTH_SCHEME_BEARER;

        if (JIRA_AUTH_SCHEME_BASIC.equalsIgnoreCase(scheme))
            return JIRA_AUTH_SCHEME_BASIC;

        if (JIRA_AUTH_SCHEME_BEARER.equalsIgnoreCase(scheme))
            return JIRA_AUTH_SCHEME_BEARER;

        throw new IllegalStateException("Unsupported JIRA auth scheme: " + scheme);
    }
}
