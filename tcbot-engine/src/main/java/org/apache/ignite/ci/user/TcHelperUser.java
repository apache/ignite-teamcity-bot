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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;
import org.apache.ignite.tcbot.common.util.CryptUtil;
import org.apache.ignite.tcbot.engine.conf.INotificationChannel;
import org.apache.ignite.tcbot.persistence.IVersionedEntity;
import org.apache.ignite.tcbot.persistence.Persisted;
import org.apache.ignite.tcservice.model.user.User;

import static javax.xml.bind.DatatypeConverter.printHexBinary;

/**
 * TC Bot user. Contains login information and encrypted passwords.
 */
@Persisted
public class TcHelperUser implements IVersionedEntity, INotificationChannel {
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

    private Boolean admin;

    /** Subscribed to all failures in following tracked branches. */
    @Nullable private Set<String> subscribedToAllFailures;

    /** {@inheritDoc} */
    @Override public int version() {
        return _version == null ? 0 : _version;
    }

    /** {@inheritDoc} */
    @Override public int latestVersion() {
        return LATEST_VERSION;
    }

    public Credentials getOrCreateCreds(String srvId) {
        Credentials next = getCredentials(srvId);

        if (next != null)
            return next;

        Credentials creds = new Credentials();
        creds.serverId = srvId;

        getCredentialsList().add(creds);

        return creds;
    }

    /**
     * @param srvId Server id.
     */
    @Nullable public Credentials getCredentials(String srvId) {
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

    /** {@inheritDoc} */
    @Override public boolean isSubscribedToBranch(String trackedBranchId) {
        return subscribedToAllFailures != null && subscribedToAllFailures.contains(trackedBranchId);
    }

    /** {@inheritDoc} */
    @Override public boolean isServerAllowed(String srvCode) {
        return getCredentials(srvCode) != null;
    }

    /** {@inheritDoc} */
    @Override public boolean isSubscribedToTag(@Nullable String tag) {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean hasTagFilter() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public String email() {
        return email;
    }

    /** {@inheritDoc} */
    @Override public String slack() {
        return null;
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

    public boolean hasEmail() {
        return !Strings.isNullOrEmpty(email);
    }

    public String fullName() {
        return fullName;
    }

    public String username() {
        return username;
    }

    public boolean containsEmail(String email) {
        if (Strings.isNullOrEmpty(email))
            return false;

        return (this.email != null && this.email.equals(email))
            || (additionalEmails != null && additionalEmails.contains(email));
    }

    /**
     * @param admin Administration.
     */
    public void setAdmin(Boolean admin) {
        this.admin = admin;
    }

    /**
     *
     */
    public boolean isAdmin() {
        return Boolean.TRUE.equals(admin);
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
