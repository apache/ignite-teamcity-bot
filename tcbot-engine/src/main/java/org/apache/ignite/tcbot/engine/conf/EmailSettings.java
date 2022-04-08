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

@SuppressWarnings("unused")
public class EmailSettings {
    /** Email to send notifications from. */
    private String username;

    /** Email password, encoded using Password Encoder. */
    private String pwd;

    /** Custom smtp server, default is gmail */
    private SmtpSettings smtp;

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

    public SmtpSettings smtp() {
        return smtp;
    }
}
