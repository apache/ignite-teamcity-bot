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

import java.lang.reflect.Field;
import java.util.Properties;
import org.apache.ignite.tcbot.common.conf.PasswordEncoder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Checks auth token configuration compatibility.
 */
public class AuthTokenConfigTest {
    /** */
    @Test
    public void jiraJsonTokenIsPlainBearerByDefault() throws Exception {
        JiraServerConfig cfg = withField(new JiraServerConfig(), "authTok", "jira-pat");

        assertEquals("jira-pat", cfg.decodedHttpAuthToken());
        assertEquals("Bearer jira-pat", cfg.httpAuthorizationHeader());
    }

    /** */
    @Test
    public void jiraJsonTokenCanBeEncodedBasic() throws Exception {
        String basicTok = PasswordEncoder.userPwdToToken("user", "password");
        JiraServerConfig cfg = withField(new JiraServerConfig(), "authTok", PasswordEncoder.encode(basicTok));

        withField(cfg, "authTokEncoded", true);
        withField(cfg, "authScheme", "Basic");

        assertEquals(basicTok, cfg.decodedHttpAuthToken());
        assertEquals("Basic " + basicTok, cfg.httpAuthorizationHeader());
    }

    /** */
    @Test
    public void jiraJsonTokenCanBeAutoDetectedAsEncoded() throws Exception {
        String basicTok = PasswordEncoder.userPwdToToken("user", "password");
        JiraServerConfig cfg = withField(new JiraServerConfig(), "authTok", PasswordEncoder.encode(basicTok));

        withField(cfg, "authScheme", "Basic");

        assertEquals(basicTok, cfg.decodedHttpAuthToken());
        assertEquals("Basic " + basicTok, cfg.httpAuthorizationHeader());
    }

    /** */
    @Test
    public void jiraLegacyPropertyTokenIsEncodedBasicByDefault() {
        String basicTok = PasswordEncoder.userPwdToToken("user", "password");
        Properties props = new Properties();

        props.setProperty(JiraServerConfig.JIRA_AUTH_TOKEN, PasswordEncoder.encode(basicTok));

        JiraServerConfig cfg = new JiraServerConfig("apache", props);

        assertEquals(basicTok, cfg.decodedHttpAuthToken());
        assertEquals("Basic " + basicTok, cfg.httpAuthorizationHeader());
    }

    /** */
    @Test
    public void jiraLegacyPropertyTokenCanBePlainBearer() {
        Properties props = new Properties();

        props.setProperty(JiraServerConfig.JIRA_AUTH_TOKEN, "jira-pat");
        props.setProperty(JiraServerConfig.JIRA_AUTH_SCHEME, "Bearer");

        JiraServerConfig cfg = new JiraServerConfig("apache", props);

        assertEquals("jira-pat", cfg.decodedHttpAuthToken());
        assertEquals("Bearer jira-pat", cfg.httpAuthorizationHeader());
    }

    /** */
    @Test
    public void jiraHexLikePlainPropertyTokenStaysPlainWhenDecodeFails() {
        Properties props = new Properties();

        props.setProperty(JiraServerConfig.JIRA_AUTH_TOKEN, "abcdef1234");
        props.setProperty(JiraServerConfig.JIRA_AUTH_SCHEME, "Bearer");

        JiraServerConfig cfg = new JiraServerConfig("apache", props);

        assertEquals("abcdef1234", cfg.decodedHttpAuthToken());
        assertEquals("Bearer abcdef1234", cfg.httpAuthorizationHeader());
    }

    /** */
    @Test
    public void jiraMissingTokenHasNoAuthorizationHeader() {
        JiraServerConfig cfg = new JiraServerConfig();

        assertNull(cfg.decodedHttpAuthToken());
        assertNull(cfg.httpAuthorizationHeader());
    }

    /** */
    @Test
    public void githubJsonTokenIsPlainByDefault() throws Exception {
        GitHubConfig cfg = withField(new GitHubConfig(), "authTok", "github-pat");

        assertEquals("github-pat", cfg.gitAuthTok());
    }

    /** */
    @Test
    public void githubJsonTokenCanBeEncoded() throws Exception {
        GitHubConfig cfg = withField(new GitHubConfig(), "authTok", PasswordEncoder.encode("github-pat"));

        withField(cfg, "authTokEncoded", true);

        assertEquals("github-pat", cfg.gitAuthTok());
    }

    /** */
    @Test
    public void githubJsonTokenCanBeAutoDetectedAsEncoded() throws Exception {
        GitHubConfig cfg = withField(new GitHubConfig(), "authTok", PasswordEncoder.encode("github-pat"));

        assertEquals("github-pat", cfg.gitAuthTok());
    }

    /** */
    @Test
    public void githubLegacyPropertyTokenIsEncodedByDefault() {
        Properties props = new Properties();

        props.setProperty(GitHubConfig.GITHUB_AUTH_TOKEN, PasswordEncoder.encode("github-pat"));

        GitHubConfig cfg = new GitHubConfig().properties(props);

        assertEquals("github-pat", cfg.gitAuthTok());
    }

    /** */
    @Test
    public void githubLegacyPropertyTokenCanBePlain() {
        Properties props = new Properties();

        props.setProperty(GitHubConfig.GITHUB_AUTH_TOKEN, "github-pat");

        GitHubConfig cfg = new GitHubConfig().properties(props);

        assertEquals("github-pat", cfg.gitAuthTok());
    }

    /** */
    @Test
    public void githubHexLikePlainPropertyTokenStaysPlainWhenDecodeFails() {
        Properties props = new Properties();

        props.setProperty(GitHubConfig.GITHUB_AUTH_TOKEN, "abcdef1234");

        GitHubConfig cfg = new GitHubConfig().properties(props);

        assertEquals("abcdef1234", cfg.gitAuthTok());
    }

    /**
     * @param target Target object.
     * @param fieldName Field name.
     * @param val Field value.
     */
    private static <T> T withField(T target, String fieldName, Object val) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);

        field.setAccessible(true);
        field.set(target, val);

        return target;
    }
}
