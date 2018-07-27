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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.tcmodel.user.User;
import org.apache.ignite.ci.util.CryptUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static javax.xml.bind.DatatypeConverter.printHexBinary;

@Persisted
public class TcHelperUser implements IVersionedEntity {
    public static final int LATEST_VERSION = 2;
    @SuppressWarnings("FieldCanBeLocal")
    public Integer _version = LATEST_VERSION;

    public String username;

    @Nullable
    public byte[] salt;

    @Nullable
    public byte[] userKeyKcv;

    @Nullable
    private List<Credentials> credentialsList = new ArrayList<>();

    public String fullName;

    public String email;

    public Set<String> additionalEmails = new LinkedHashSet<>();

    @Nullable private Set<String> subscribedToAllFailures;

    @Override
    public int version() {
        return _version == null ? 0 : _version;
    }

    @Override
    public int latestVersion() {
        return LATEST_VERSION;
    }

    public Credentials getOrCreateCreds(String serverId) {
        Credentials next = getCredentials(serverId);

        if (next != null)
            return next;

        Credentials creds = new Credentials();
        creds.serverId = serverId;

        getCredentialsList().add(creds);

        return creds;
    }

    @Nullable
    public Credentials getCredentials(String srvId) {
        for (Credentials next : getCredentialsList()) {
            if (next.serverId.equals(srvId))
                return next;
        }

        return null;
    }

    public List<Credentials> getCredentialsList() {
        if (credentialsList == null)
            credentialsList = new ArrayList<>();

        return credentialsList;
    }

    public String getDisplayName() {
        if (!Strings.isNullOrEmpty(fullName))
            return fullName;

        return username;
    }

    public void enrichUserData(User tcUser) {
        if (tcUser.email != null) {
            if (email == null)
                email = tcUser.email;
            else if (!email.equals(tcUser.email))
                additionalEmails.add(tcUser.email);
        }

        if (tcUser.name != null) {
            if (this.fullName == null)
                fullName = tcUser.name;
        }
    }

    public void resetCredentials() {
        userKeyKcv = null;
        getCredentialsList().clear();
        salt = null;
    }

    public boolean isSubscribed(String trackedBranchId) {
        return subscribedToAllFailures != null && subscribedToAllFailures.contains(trackedBranchId);
    }

    public void resetNotifications() {
        if (subscribedToAllFailures != null)
            subscribedToAllFailures.clear();
    }

    public void addNotification(String branch) {
        if (subscribedToAllFailures == null)
            subscribedToAllFailures = new TreeSet<>();

        subscribedToAllFailures.add(branch);
    }

    public boolean hasSubscriptions() {
        return subscribedToAllFailures!=null && !subscribedToAllFailures.isEmpty();
    }

    public static class Credentials {
        String serverId;
        String username;

        byte[] passwordUnderUserKey;

        Credentials() {

        }

        public Credentials(String serviceId, String serviceLogin) {
            this.serverId = serviceId;
            this.username = serviceLogin;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("serverId", serverId)
                .add("username", username)
                .add("passwordUnderUserKey", printHexBinary(passwordUnderUserKey))
                .toString();
        }

        void setPasswordUnderUserKey(byte[] bytes) {
            passwordUnderUserKey = bytes;
        }

        public String getUsername() {
            return username;
        }

        public String getServerId() {
            return serverId;
        }

        public byte[] getPasswordUnderUserKey() {
            return passwordUnderUserKey;
        }

        public void setPassword(String password, byte[] userKey) {
            setPasswordUnderUserKey(
                CryptUtil.aesEncryptP5Pad(
                    userKey,
                    password.getBytes(CryptUtil.CHARSET)));
        }

        public Credentials setLogin(String username) {
            this.username = username;
            return this;
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("username", username)
            .add("fullName", fullName)
            .add("email", email)
            .add("additionalEmails", additionalEmails)
            .add("salt", salt == null ? "" : printHexBinary(salt))
            .add("userKeyKcv", userKeyKcv == null ? "" : printHexBinary(userKeyKcv))
            .add("credentialsList", credentialsList)
            .add("subscribedToAllFailures", subscribedToAllFailures == null ? "" : subscribedToAllFailures.toString())
            .toString();
    }
}
