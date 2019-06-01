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

package org.apache.ignite.ci;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;

import org.apache.ignite.ci.tcbot.conf.BranchesTracked;
import org.apache.ignite.tcbot.common.util.Base64Util;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * TC Helper Config access, tracked branches, etc stuff.
 */
public class HelperConfig {
    public static final String CONFIG_FILE_NAME = "auth.properties";
    public static final String MAIL_PROPS = "mail.auth.properties";
    public static final String HOST = "host";
    public static final String USERNAME = "username";
    public static final String ENCODED_PASSWORD = "encoded_password";

    /** GitHub authorization token property name. */
    public static final String GITHUB_AUTH_TOKEN = "github.auth_token";

    /** Git branch naming prefix for PRLess contributions. */
    public static final String GIT_BRANCH_PREFIX = "git.branch_prefix";

    /** JIRA authorization token property name. */
    public static final String JIRA_AUTH_TOKEN = "jira.auth_token";

    /** Github API url for the project. */
    public static final String GIT_API_URL = "git.api_url";

    /** JIRA URL to build links to tickets. */
    public static final String JIRA_URL = "jira.url";

    /** Prefix for JIRA ticket names. */
    @Deprecated
    public static final String JIRA_TICKET_TEMPLATE = "jira.ticket_template";

    /** Slack authorization token property name. */
    public static final String SLACK_AUTH_TOKEN = "slack.auth_token";
    @Deprecated
    public static final String SLACK_CHANNEL = "slack.channel";
    public static final String LOGS = "logs";

    public static Properties loadAuthProperties(File workDir, String cfgFileName) {
        try {
            return loadAuthPropertiesX(workDir, cfgFileName);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties loadAuthPropertiesX(File workDir, String cfgFileName) throws IOException {
        File file = new File(workDir, cfgFileName);

        Preconditions.checkState(file.exists(),
            "Please setup parameters for service in config file [branches.json]. " +
                "See conf directory for examples");

        return loadProps(file);
    }

    private static Properties loadProps(File file) throws IOException {
        Properties props = new Properties();

        try (FileReader reader = new FileReader(file)) {
            props.load(reader);
        }

        return props;
    }

    public static String prepareConfigName(String tcName) {
        return prefixedWithServerName(tcName, CONFIG_FILE_NAME);
    }

    private static String prefixedWithServerName(@Nullable String tcName, String name) {
        return isNullOrEmpty(tcName) ? name : (tcName + "." + name);
    }

    @NotNull public static String userPwdToToken(String user, String pwd) {
        return Base64Util.encodeUtf8String(user + ":" + pwd);
    }

    @Deprecated
    public static String getMandatoryProperty(Properties props, String key, String cfgName) {
        final String user = props.getProperty(key);
        Preconditions.checkState(!isNullOrEmpty(user), key + " property should be filled in " + cfgName);
        return user;
    }

    public static BranchesTracked getTrackedBranches() {
        final File workDir = TcBotWorkDir.resolveWorkDir();
        final File file = new File(workDir, "branches.json");

        try (FileReader json = new FileReader(file)) {
            return new Gson().fromJson(json, BranchesTracked.class);
        }
        catch (IOException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }

    public static Properties loadEmailSettings() {
        try {
            String respConf = prefixedWithServerName(null, MAIL_PROPS);
            final File workDir = TcBotWorkDir.resolveWorkDir();
            File file = new File(workDir, respConf);
            return loadProps(file);
        }
        catch (IOException e) {
            e.printStackTrace();
            return new Properties();
        }
    }

}
