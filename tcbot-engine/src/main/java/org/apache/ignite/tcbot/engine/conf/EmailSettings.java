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
import org.apache.ignite.tcbot.common.conf.PasswordEncoder;
import org.apache.ignite.tcbot.notify.ISendEmailConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.google.common.base.Strings.isNullOrEmpty;

@SuppressWarnings("unused")
public class EmailSettings implements ISendEmailConfig {
    /** Email to send notifications from. */
    private String username;

    /** Email password, encoded using Password Encoder. */
    private String pwd;

    /** Custom smtp server, default is gmail */
    private SmtpSettings smtp;

    /** Default is true. */
    private Boolean auth;

    /**
     * @return Email to send notifications from.
     */
    public String username() {
        return username;
    }

    /**
     * @param username New email to send notifications from.
     */
    public void username(String username) {
        this.username = username;
    }

    /**
     * @return Email password, encoded using Password Encoder.
     */
    public String password() {
        return pwd;
    }

    /**
     * @param pwd New email password, encoded using Password Encoder.
     */
    public void password(String pwd) {
        this.pwd = pwd;
    }

    @Nullable
    public SmtpSettings smtp() {
        return smtp;
    }

    @Nullable public Boolean isAuthRequired() {
        return auth;
    }

    @Nullable
    @Override
    public Boolean isSmtpSsl() {
        SmtpSettings smtp = smtp();

        return smtp != null ? smtp.ssl() : null;
    }

    @Nullable
    @Override
    public Integer smtpPort() {
        SmtpSettings smtp = smtp();

        return smtp != null ? smtp.port() : null;
    }

    /**
     *
     */
    @Nonnull
    @Override public String usernameMandatory() {
        String username = username();

        Preconditions.checkState(!isNullOrEmpty(username),
                "notifications/email/username property should be filled in branches.json");

        return username;
    }

    /**
     * @return Email password.
     */
    @Nonnull
    @Override public String passwordClearMandatory() {
        Preconditions.checkState(!isNullOrEmpty(password()),
                "notifications/email/pwd property should be filled in branches.json");

        return PasswordEncoder.decode(password());
    }

    @Nullable
    @Override
    public String smtpHost() {
        SmtpSettings smtp = smtp();

        return smtp != null ? smtp.host() : null;
    }


}
