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
package org.apache.ignite.ci.teamcity.ignited.fatbuild;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

/**
 * Triggered build information type/date/user
 */
public class TriggeredCompacted {
    /**
     * Type of triggering, for example:
     * "snapshotDependency", ""
     */
    public int type;

    /**
     * User ID by teamcity identification.
     */
    public int userId;

    /**
     * User entity: Username compacted string Id.
     */
    public int userUsername;

    /**
     * Build ID, filled for snapshotDependencies triggering.
     */
    public int buildId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TriggeredCompacted that = (TriggeredCompacted) o;
        return type == that.type &&
                userId == that.userId &&
                userUsername == that.userUsername &&
                buildId == that.buildId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, userId, userUsername, buildId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", type)
                .add("userId", userId)
                .add("userUsername", userUsername)
                .add("buildId", buildId)
                .toString();
    }
}
