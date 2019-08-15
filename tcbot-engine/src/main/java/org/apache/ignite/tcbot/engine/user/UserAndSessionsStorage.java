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
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ci.user.TcHelperUser;
import org.apache.ignite.ci.user.UserSession;
import org.apache.ignite.tcbot.persistence.CacheConfigs;

public class UserAndSessionsStorage implements IUserStorage {
    public static final String USERS = "users";
    public static final String USER_SESSIONS = "sessions";
    @Inject
    private Provider<Ignite> igniteProvider;

    private volatile Ignite ignite;

    public IgniteCache<String, TcHelperUser> users() {
        return getIgnite().getOrCreateCache(CacheConfigs.<String, TcHelperUser>getCacheV2TxConfig(USERS));
    }

    public Ignite getIgnite() {
        if (ignite != null)
            return ignite;

        final Ignite ignite = igniteProvider.get();
        this.ignite = ignite;
        return ignite;
    }

    /** {@inheritDoc} */
    @Nullable public UserSession getSession(String sessId) {
        return sessions().get(sessId);
    }

    private IgniteCache<String, UserSession> sessions() {
        return getIgnite().getOrCreateCache(CacheConfigs.<String, UserSession>getCacheV2TxConfig(USER_SESSIONS));
    }

    /** {@inheritDoc} */
    @Override public void putSession(String sessId, UserSession userSes) {
        sessions().put(sessId, userSes);
    }

    /** {@inheritDoc} */
    @Nullable @Override public TcHelperUser findUserByEmail(String email) {
        return allUsers().filter(u -> u.containsEmail(email)).findAny().orElse(null);
    }

    /** {@inheritDoc} */
    @Override @Nullable public TcHelperUser getUser(String username) {
        return users().get(username);
    }

    /** {@inheritDoc} */
    @Override public void putUser(String username, TcHelperUser user) {
        users().put(username, user);
    }

    /** {@inheritDoc} */
    @Override public Stream<TcHelperUser> allUsers() {
        return StreamSupport.stream(users().spliterator(), false).map(Cache.Entry::getValue);
    }
}
