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

package org.apache.ignite.ci.user;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.db.TcHelperDb;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Provider;

public class UserAndSessionsStorage {
    public static final String USERS = "users";
    public static final String USER_SESSIONS = "sessions";
    @Inject
    private Provider<Ignite> igniteProvider;


    public IgniteCache<String, TcHelperUser> users() {
        return igniteProvider.get().getOrCreateCache(TcHelperDb.getCacheV2TxConfig(USERS));
    }

    @Nullable public UserSession getSession(String sessId) {
        return sessions().get(sessId);
    }


    private IgniteCache<String, UserSession> sessions() {
        return igniteProvider.get().getOrCreateCache(TcHelperDb.getCacheV2TxConfig(USER_SESSIONS));
    }

    public void putSession(String sessId, UserSession userSession) {
        sessions().put(sessId, userSession);
    }

    public TcHelperUser getUser(String username) {
        return users().get(username);
    }

    public void putUser(String username, TcHelperUser user) {
        users().put(username, user);
    }

}
