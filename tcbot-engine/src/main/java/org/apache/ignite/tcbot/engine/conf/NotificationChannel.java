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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import java.util.Collection;
import java.util.HashSet;
import javax.annotation.Nullable;

/**
 *
 */
public class NotificationChannel implements INotificationChannel {
    /** (Destination) Email. */
    private String email;

    /** Slack. */
    private String slack;

    /** Subscribed to failures in tracked branches. */
    private Collection<String> subscribed = new HashSet<>();

    /** Subscribed to tags. Empty ot null set means all tags are applicable. */
    private Collection<String> tagsFilter = new HashSet<>();

    /** {@inheritDoc} */
    @Override public boolean isSubscribedToBranch(String trackedBranchId) {
        return subscribed != null && subscribed.contains(trackedBranchId);
    }

    /** {@inheritDoc} */
    @Override public boolean isServerAllowed(String srvCode) {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean isSubscribedToTag(@Nullable String tag) {
        if (!hasTagFilter())
            return true;

        if(Strings.isNullOrEmpty(tag))
            return true; // nothing to filter, consider subscribed

        return tagsFilter.contains(tag);
    }

    /** {@inheritDoc} */
    @Override public String email() {
        return email;
    }

    /** {@inheritDoc} */
    @Override public String slack() {
        return slack;
    }

    /** {@inheritDoc} */
    @Override public boolean hasTagFilter() {
        return (tagsFilter != null) && !tagsFilter.isEmpty();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("email", email)
            .add("slack", slack)
            .add("subscribed", subscribed)
            .add("tagsFilter", tagsFilter)
            .toString();
    }


    /**
     * Sets email.
     * @param email Value.
     */
    public NotificationChannel email(String email) {
        this.email = email;

        return this;
    }
}
