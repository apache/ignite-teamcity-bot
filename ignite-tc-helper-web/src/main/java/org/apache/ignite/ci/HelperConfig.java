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
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;
import javax.ws.rs.QueryParam;
import jersey.repackaged.com.google.common.base.Throwables;
import org.apache.ignite.ci.conf.BranchesTracked;
import org.apache.ignite.ci.conf.PasswordEncoder;
import org.apache.ignite.ci.util.Base64Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * TC Helper Config access stuff.
 */
public class HelperConfig {
    public static final String CONFIG_FILE_NAME = "auth.properties";
    public static final String RESP_FILE_NAME = "resp.properties";
    public static final String MAIL_PROPS = "mail.auth.properties";
    public static final String HOST = "host";
    public static final String USERNAME = "username";
    @Deprecated
    private static final String PASSWORD = "password";
    public static final String ENCODED_PASSWORD = "encoded_password";
    public static final String SLACK_AUTH_TOKEN = "slack.auth_token";
    public static final String SLACK_CHANNEL = "slack.channel";
    public static final String LOGS = "logs";
    public static final String ENDL = String.format("%n");

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
        if (!(file.exists())) {

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(HOST + "=" + "http://ci.ignite.apache.org/" + ENDL);
                writer.write(USERNAME + "=" + ENDL);
                writer.write(ENCODED_PASSWORD + "=" + ENDL);
            }
            throw new IllegalStateException("Please setup username and encodedPassword in config file [" +
                file.getCanonicalPath() + "]");
        }
        return loadProps(file);
    }

    private static Properties loadProps(File file) throws IOException {
        Properties props = new Properties();

        try (FileReader reader = new FileReader(file)) {
            props.load(reader);
        }

        return props;
    }

    static String prepareConfigName(String tcName) {
        return prefixedWithServerName(tcName, CONFIG_FILE_NAME);
    }

    private static String prefixedWithServerName(@Nullable String tcName, String name) {
        return isNullOrEmpty(tcName) ? name : (tcName + "." + name);
    }

    public static File ensureDirExist(File workDir) {
        if (!workDir.exists())
            checkState(workDir.mkdirs(), "Unable to make directory [" + workDir + "]");

        return workDir;
    }

    public static File resolveWorkDir() {
        File workDir = null;
        String property = System.getProperty(ITcHelper.TEAMCITY_HELPER_HOME);
        if (isNullOrEmpty(property)) {
            String conf = ".ignite-teamcity-helper";
            String prop = System.getProperty("user.home");
            //relative in work dir
            workDir = isNullOrEmpty(prop) ? new File(conf) : new File(prop, conf);
        }
        else
            workDir = new File(property);

        return ensureDirExist(workDir);
    }

    @Nullable static String prepareBasicHttpAuthToken(Properties props, String configName) {
        final String user = getMandatoryProperty(props, USERNAME, configName);
        String pwd = props.getProperty(PASSWORD);
        boolean filled = !isNullOrEmpty(pwd);
        if(!filled) {
            String enc = props.getProperty(ENCODED_PASSWORD);
            if(!isNullOrEmpty(enc)) {
                pwd = PasswordEncoder.decode(enc);
                filled = true;
            }
        }

        if(!filled)
            return null;

        return Base64Util.encodeUtf8String(user + ":" + pwd);
    }

    public static String getMandatoryProperty(Properties props, String key, String configName) {
        final String user = props.getProperty(key);
        Preconditions.checkState(!isNullOrEmpty(user), key + " property should be filled in " + configName);
        return user;
    }

    public static BranchesTracked getTrackedBranches() {
        final File workDir = resolveWorkDir();
        final File file = new File(workDir, "branches.json");

        try(final FileReader json = new FileReader(file)) {
            return new Gson().fromJson(json, BranchesTracked.class);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Nullable public static Properties loadContactPersons(String tcName) {
        try {
            String respConf = prefixedWithServerName(tcName, RESP_FILE_NAME);
            final File workDir = resolveWorkDir();
            final File file = new File(workDir, respConf);

            if(!file.exists())
                return null;

            return loadProps(file);
        }
        catch (IOException e) {
            e.printStackTrace();
            return new Properties();
        }
    }

    public static Properties loadEmailSettings() {
        try {
            String respConf = prefixedWithServerName(null, MAIL_PROPS);
            final File workDir = resolveWorkDir();
            File file = new File(workDir, respConf);
            return loadProps(file);
        }
        catch (IOException e) {
            e.printStackTrace();
            return new Properties();
        }
    }


    @NotNull public static File getLogsDirForServer(@QueryParam("serverId") String serverId) {
        final File workDir = resolveWorkDir();
        final String configName = prepareConfigName(serverId);
        final Properties props = loadAuthProperties(workDir, configName);
        return resolveLogs(workDir, props);
    }

    @NotNull static File resolveLogs(File workDir, Properties props) {
        final String logsProp = props.getProperty(LOGS, "logs");
        final File logsDirFileConfigured = new File(logsProp);
        return logsDirFileConfigured.isAbsolute() ? logsDirFileConfigured : new File(workDir, logsProp);
    }
}
