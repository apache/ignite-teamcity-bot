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

import jdk.internal.joptsimple.internal.Strings;

/**
 * Notifications Config
 */
public class NotificationsConfig {
    /** Email to send notifications from. */
    String emailUsername;
    /** Email password. */
    String emailPwd;

    /** Slack auth token. Not encoded using Password encoder */
    String slackAuthTok;

    public boolean isEmpty() {
        return Strings.isNullOrEmpty(emailUsername)
            && Strings.isNullOrEmpty(emailPwd)
            && Strings.isNullOrEmpty(slackAuthTok);
    }

    public String slackAuthToken() {
        return slackAuthTok;
    }

    /**
     * @return Email to send notifications from.
     */
    public String emailUsername() {
        return emailUsername;
    }


    /**
     * @return Email password.
     */
    public String emailPassword() {
        return emailPwd;
    }

    /**
     * @param emailPwd New email password.
     */
    public void emailPassword(String emailPwd) {
        this.emailPwd = emailPwd;
    }
}
