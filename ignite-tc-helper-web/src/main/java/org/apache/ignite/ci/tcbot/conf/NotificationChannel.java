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

import com.google.common.base.MoreObjects;
import java.util.Collection;
import java.util.HashSet;

/**
 *
 */
public class NotificationChannel implements INotificationChannel {
    /** Email. */
    private String email;

    /** Slack. */
    private String slack;

    /** Subscribed to failures in tracked branches. */
    private Collection<String> subscribed = new HashSet<>();

    /** {@inheritDoc} */
    @Override public boolean isSubscribed(String trackedBranchId) {
        return subscribed != null && subscribed.contains(trackedBranchId);
    }

    /** {@inheritDoc} */
    @Override public boolean isServerAllowed(String srvCode) {
        return true;
    }

    /** {@inheritDoc} */
    @Override public String email() {
        return email;
    }

    /** {@inheritDoc} */
    @Override public String slack() {
        return slack;
    }

    public void slack(String slack) {
        this.slack = slack;
    }

    public void subscribe(String trackedBranchName) {
        this.subscribed.add(trackedBranchName);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("email", email)
            .add("slack", slack)
            .add("subscribed", subscribed)
            .toString();
    }
}
