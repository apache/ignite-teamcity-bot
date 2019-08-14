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
package org.apache.ignite.tcbot.engine.user;

import java.util.stream.Stream;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserSession;

import javax.annotation.Nullable;

/**
 * Teamcity Bot User and Sessions storage interface.
 */
public interface IUserStorage {
    /**
     * Get user by username.
     *
     * @param username Username.
     */
    @Nullable public TcHelperUser getUser(String username);

    public void putUser(String username, TcHelperUser user);

    /**
     * @return All users in storage stream.
     */
    public Stream<TcHelperUser> allUsers();

    @Nullable public UserSession getSession(String id);

    public void putSession(String sessId, UserSession userSes);

    @Nullable public TcHelperUser findUserByEmail(String email);
}
