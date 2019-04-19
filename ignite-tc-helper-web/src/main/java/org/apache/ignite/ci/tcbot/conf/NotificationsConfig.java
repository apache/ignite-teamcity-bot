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

package org.apache.ignite.ci.tcbot.conf;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nonnull;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.conf.PasswordEncoder;
import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Notifications Config
 */
public class NotificationsConfig {
    /** Email. */
    private EmailSettings email = new EmailSettings();

    /** Slack auth token. Not encoded using Password encoder */
    private String slackAuthTok;

    /** Channels to send notifications to. */
    private List<NotificationChannel> channels = new ArrayList<>();

    @NotNull public static NotificationsConfig backwardConfig() {
        Properties cfgProps = HelperConfig.loadEmailSettings();

        NotificationsConfig cfg = new NotificationsConfig();

        cfg.slackAuthTok = cfgProps.getProperty(HelperConfig.SLACK_AUTH_TOKEN);

        cfg.email.username(cfgProps.getProperty(HelperConfig.USERNAME));

        cfg.email.password(cfgProps.getProperty(HelperConfig.ENCODED_PASSWORD));

        String slackCh = cfgProps.getProperty(HelperConfig.SLACK_CHANNEL);
        if (!Strings.isNullOrEmpty(slackCh)) {
            NotificationChannel ch = new NotificationChannel();
            ch.slack("#" + slackCh);
            ch.subscribe(TcServerConfig.DEFAULT_TRACKED_BRANCH_NAME);
            cfg.channels.add(ch);
        }
        return cfg;
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

    public Collection<NotificationChannel> channels() {
        if (channels == null)
            return Collections.emptyList();

        return channels;
    }
}
