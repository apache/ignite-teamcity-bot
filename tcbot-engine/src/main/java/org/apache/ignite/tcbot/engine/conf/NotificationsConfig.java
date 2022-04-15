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
import org.apache.ignite.tcbot.common.conf.PasswordEncoder;
import org.apache.ignite.tcbot.notify.ISendEmailConfig;
import org.apache.ignite.tcbot.notify.ISlackBotConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Notifications Config
 */
public class NotificationsConfig implements ISlackBotConfig {
    /** (Source) Email. */
    private EmailSettings email = new EmailSettings();

    /** Slack auth token. Not encoded using Password encoder */
    private String slackAuthTok;

    /** Channels to send notifications to. */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private List<NotificationChannel> channels = new ArrayList<>();

    public boolean isEmpty() {
        return (email == null || Strings.isNullOrEmpty(email.username()))
            && (email == null || Strings.isNullOrEmpty(email.password()))
            && Strings.isNullOrEmpty(slackAuthTok);
    }

    public String slackAuthToken() {
        return slackAuthTok;
    }


    public Collection<? extends INotificationChannel> channels() {
        return channels == null ? Collections.emptyList() : Collections.unmodifiableList(channels);
    }

    public ISendEmailConfig email() {
        return email;
    }
}
