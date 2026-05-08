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

package org.apache.ignite.tcservice.login;

import javax.annotation.Nullable;
import org.apache.ignite.tcservice.model.user.User;

/**
 * TeamCity login check result.
 */
public class TcLoginResult {
    /** TeamCity accepted the supplied credentials. */
    public static TcLoginResult accepted(User user) {
        return new TcLoginResult(user, false);
    }

    /** TeamCity explicitly rejected the supplied credentials. */
    public static TcLoginResult unauthorized() {
        return new TcLoginResult(null, true);
    }

    /** TeamCity could not be checked, for example because it is temporarily unavailable. */
    public static TcLoginResult notChecked() {
        return new TcLoginResult(null, false);
    }

    @Nullable private final User user;

    private final boolean unauthorized;

    private TcLoginResult(@Nullable User user, boolean unauthorized) {
        this.user = user;
        this.unauthorized = unauthorized;
    }

    @Nullable public User user() {
        return user;
    }

    public boolean accepted() {
        return user != null;
    }

    public boolean isUnauthorized() {
        return unauthorized;
    }
}
