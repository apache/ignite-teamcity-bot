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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.ignite.tcbot.common.conf.ITcServerConfig;
import org.apache.ignite.tcbot.common.conf.PasswordEncoder;
import org.apache.ignite.tcbot.common.conf.TcBotWorkDir;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nonnull;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Notifications Config
 */
public class NotificationsConfig implements INotificationsConfig {
    /** (Source) Email. */
    private EmailSettings email = new EmailSettings();

    /** Slack auth token. Not encoded using Password encoder */
    private String slackAuthTok;

    /** Channels to send notifications to. */
    private List<NotificationChannel> channels = new ArrayList<>();

    private static final String MAIL_PROPS = "mail.auth.properties";
    private static final String USERNAME = "username";
    private static final String ENCODED_PASSWORD = "encoded_password";
    /** Slack authorization token property name. */
    private static final String SLACK_AUTH_TOKEN = "slack.auth_token";
    @Deprecated
    private static final String SLACK_CHANNEL = "slack.channel";

    @Nonnull
    public static NotificationsConfig backwardConfig() {
        Properties cfgProps = loadEmailSettings();

        NotificationsConfig cfg = new NotificationsConfig();

        cfg.slackAuthTok = cfgProps.getProperty(SLACK_AUTH_TOKEN);

        cfg.email.username(cfgProps.getProperty(USERNAME));

        cfg.email.password(cfgProps.getProperty(ENCODED_PASSWORD));

        String slackCh = cfgProps.getProperty(SLACK_CHANNEL);
        if (!Strings.isNullOrEmpty(slackCh)) {
            NotificationChannel ch = new NotificationChannel();
            ch.slack("#" + slackCh);
            ch.subscribe(ITcServerConfig.DEFAULT_TRACKED_BRANCH_NAME);
            cfg.channels.add(ch);
        }

        return cfg;
    }

    public static Properties loadEmailSettings() {
        try {
            return loadProps(new File(TcBotWorkDir.resolveWorkDir(), MAIL_PROPS));
        }
        catch (IOException e) {
            e.printStackTrace();
            return new Properties();
        }
    }

    private static Properties loadProps(File file) throws IOException {
        Properties props = new Properties();

        try (FileReader reader = new FileReader(file)) {
            props.load(reader);
        }

        return props;
    }


    public boolean isEmpty() {
        return (email == null || Strings.isNullOrEmpty(email.username()))
            && (email == null || Strings.isNullOrEmpty(email.password()))
            && Strings.isNullOrEmpty(slackAuthTok);
    }

    public String slackAuthToken() {
        return slackAuthTok;
    }

    /**
     * @return Email to send notifications from.
     */
    public String emailUsername() {
        return email == null ? null : email.username();
    }

    public String emailUsernameMandatory() {
        String username = emailUsername();

        Preconditions.checkState(!isNullOrEmpty(username),
            "notifications/email/username property should be filled in branches.json");

        return username;
    }

    /**
     * @return Email password.
     */
    @Nonnull
    public String emailPasswordClearMandatory() {
        Preconditions.checkNotNull(email,
            "notifications/email/pwd property should be filled in branches.json");
        Preconditions.checkState(!isNullOrEmpty(email.password()),
            "notifications/email/pwd property should be filled in branches.json");

        return PasswordEncoder.decode(email.password());
    }

    public Collection<? extends INotificationChannel> channels() {
        if (channels == null)
            return Collections.emptyList();

        return Collections.unmodifiableList(channels);
    }

    public void addChannel(NotificationChannel ch) {
        if (channels == null)
            this.channels = new ArrayList<>();

        channels.add(ch);
    }
}
